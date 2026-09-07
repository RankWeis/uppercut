package com.rankweis.uppercut.karate.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PathUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intuit.karate.junit5.Karate;
import com.rankweis.uppercut.help.UppercutWebHelpProvider;
import com.rankweis.uppercut.karate.debugging.agent.KarateDebugChannel;
import com.rankweis.uppercut.karate.debugging.agent.KarateDebugProcess;
import com.rankweis.uppercut.karate.psi.GherkinFileType;
import com.rankweis.uppercut.settings.KarateSettingsState;
import com.rankweis.uppercut.testrunner.KarateTestRunner;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class KarateRunConfiguration extends ApplicationConfiguration implements ModuleRunProfile,
  TargetEnvironmentAwareRunProfile {

  @Getter @Setter private String relPath;

  public enum PreferredTest {
    WHOLE_FILE("WHOLE_FILE"),
    SINGLE_SCENARIO("SINGLE_SCENARIO"),
    ALL_TAGS("TAGS"),
    ALL_IN_FOLDER("ALL_IN_FOLDER");

    final String name;

    PreferredTest(String name) {
      this.name = name;
    }
  }

  @Getter @Setter private int lineNumber = 0;
  @Getter private Optional<String> testName = Optional.empty();
  @Getter @Setter private String testDescription;
  @Getter @Setter private String featureName;
  @Getter @Setter private String scenarioName;
  @Getter @Setter private String debugPort;
  /**
   * Opt-in: attach the JVM debugger to the test JVM as well, in its own tab. Karate has no
   * user-written step definitions, so this is for the two things the Karate debugger cannot reach -
   * Java a feature calls through {@code Java.type}, and karate-core itself. Off by default, because
   * a Java breakpoint suspends every thread in the JVM, including the one serving the Karate tab.
   */
  @Getter @Setter private boolean attachJvmDebugger = false;
  @Getter @Setter private String tag;
  @Getter @Setter private String path;
  @Getter @Setter private PreferredTest preferredTest = PreferredTest.WHOLE_FILE;
  @Setter private String parallelism;
  /** True when the run was created on a folder that holds feature files; see the producer's shouldReplace. */
  @Getter @Setter private boolean folderHasFeatures = false;
  @Getter @Setter private int remotePort = 0;
  private String environment;


  protected KarateRunConfiguration(@NotNull Project project,
    @NotNull ConfigurationFactory factory, @Nullable String name) {
    super(name, project, factory);
    this.setMainClassName("com.rankweis.uppercut.testrunner.KarateTestRunner");
  }

  @Override public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new KarateSettingsEditor(getProject(), getFactory(), getName());
  }

  /**
   * The launch state for a Karate run. Named rather than anonymous so it can carry
   * {@link DebugChannelHolder}, which is how the debug runner reaches this launch's channel.
   */
  abstract static class KarateCommandLineState
    extends JavaApplicationCommandLineState<KarateRunConfiguration> implements DebugChannelHolder {

    KarateCommandLineState(@NotNull KarateRunConfiguration configuration,
      @NotNull ExecutionEnvironment environment) {
      super(configuration, environment);
    }
  }

  /**
   * How {@code KarateDebugRunner} gets at the channel this launch opened. The channel has to be
   * created while the command line is being built - that is when its port is written into the test
   * JVM's arguments - but the debug session cannot exist until there is a process to attach it to.
   */
  public interface DebugChannelHolder {

    @Nullable KarateDebugChannel debugChannel();

    /**
     * The JDWP port this launch opened for the opt-in JVM debugger, or 0 when it was not asked for.
     * Like the channel's port it is chosen while the command line is built, because that is when it
     * has to be written into the test JVM's arguments.
     */
    int jvmDebugPort();
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {

    return new KarateCommandLineState(this, env) {
      /**
       * The debug channel this launch opened, or null on a Run. Created in
       * {@link #createJavaParameters()}, where its port is written into the test JVM's arguments, and
       * read back by {@code KarateDebugRunner} once there is a process to attach a session to.
       */
      private KarateDebugChannel debugChannel;
      /** The JDWP port for the opt-in JVM debugger, or 0 when this run did not ask for one. */
      private int jvmDebugPort;

      @Override
      public @Nullable KarateDebugChannel debugChannel() {
        return debugChannel;
      }

      @Override
      public int jvmDebugPort() {
        return jvmDebugPort;
      }

      @Override
      protected JavaParameters createJavaParameters() throws ExecutionException {
        final JavaParameters params = super.createJavaParameters();
        VirtualFile[] libraryRoots = karateLibraryRoots();
        if (libraryRoots.length == 0) {
          // No libraries anywhere is the signature of a project that has not finished (or has
          // failed) importing. Launching anyway sends the run down the v1 fallback with an empty
          // classpath, which dies with "Must have karate-core on the classpath" - an error that
          // points at everything except the actual problem.
          throw new ExecutionException(
            "No libraries found on the classpath - the project may still be importing, or the "
              + "Gradle/Maven sync may have failed. Wait for the import to finish (check the Build "
              + "tool window) and run again.");
        }
        List<String> libraryNames = Arrays.stream(libraryRoots).map(VirtualFile::getName).toList();
        // The library scan above can see Karate through the project-wide library table while the
        // classpath the JVM is about to get - built from the run configuration's module - has none
        // of it. That happens when the feature file is not inside any module (a project opened from
        // its pom.xml or build file rather than imported, or a symlinked project path). Refuse with
        // the cause rather than launch into "Must have karate-core on the classpath".
        List<String> classpathJars = params.getClassPath().getPathList().stream()
          .map(path -> new File(path).getName()).toList();
        Module module = getConfigurationModule().getModule();
        String classpathProblem = classpathProblem(classpathJars, module == null ? null : module.getName());
        if (classpathProblem != null) {
          throw new ExecutionException(classpathProblem);
        }
        KarateSettingsState.KarateVersionPreference preference =
          KarateSettingsState.getInstance().getKarateVersionPreference();
        checkVersionOverrideMatchesClasspath(preference, libraryNames);
        boolean karateV2 = isKarateV2(preference, libraryNames.stream());
        if (karateV2) {
          params.getProgramParametersList().add("--karate-major-version", "2");
        } else {
          List<String> karateJunit5 = Arrays.stream(libraryRoots)
            .filter(v -> v.getName().contains("karate-junit5"))
            .map(VirtualFile::getPath).toList();
          if (karateJunit5.isEmpty()) {
            log.warn("No junit5 in classpath");
            // The bundled fallback is v1-only; v2 users always have karate on their own classpath.
            params.getProgramParametersList().add("--karate-provided", "true");
            String bundledJar = PathUtil.getJarPathForClass(Karate.class);
            params.getClassPath().add(bundledJar);
            // Both versions, so the warning the runner prints can name them. "Results may be
            // inconsistent" is only actionable if the user can see which Karate ran against which.
            String bundledVersion = karateJarVersion(new File(bundledJar).getName());
            if (bundledVersion != null) {
              params.getProgramParametersList().add("--karate-bundled-version", bundledVersion);
            }
            libraryNames.stream()
              .map(KarateRunConfiguration::karateJarVersion)
              .filter(Objects::nonNull)
              .findFirst()
              .ifPresent(v -> params.getProgramParametersList().add("--karate-module-version", v));
          }
        }
        params.setUseDynamicClasspath(true);
        params.getClassPath().add(PathUtil.getJarPathForClass(KarateTestRunner.class));

        if (getTestName().map(String::isBlank).orElse(false)) {
          String[] split = getName().split(":");
          if (split.length == 2) {
            setTestName(split[0]);
            myConfiguration.lineNumber = Integer.parseInt(split[1].split(" ")[0]);
          }
        }
        String escapedName = myConfiguration.getTestName().map(s -> s.replace(" ", "_")).orElse("");
        String testNameParameter = "--testname";
        // Prefer the absolute source path (file: URL) when we have one, so an edit in the IDE is
        // reflected on the next run without a rebuild. classpath: resolves against target/test-classes
        // (or build/resources/test), which is only refreshed by process-test-resources / processTestResources.
        // The producer stores the feature's absolute path in `path` for file-based runs and a relative
        // folder name for ALL_IN_FOLDER, so the isAbsolute() check picks the right branch on its own.
        String featurePath = myConfiguration.getPath();
        boolean useFileUrl = !StringUtils.isBlank(featurePath) && new File(featurePath).isAbsolute();
        if (preferredTest == PreferredTest.WHOLE_FILE) {
          params.getProgramParametersList().add(testNameParameter,
            useFileUrl ? "file:" + featurePath
              : Optional.ofNullable(myConfiguration.getRelPath()).filter(s -> !s.isBlank()).orElse(escapedName));
        } else if (preferredTest == PreferredTest.SINGLE_SCENARIO) {
          params.getProgramParametersList().add(testNameParameter,
            useFileUrl ? "file:" + featurePath + ":" + lineNumber
              : Optional.ofNullable(myConfiguration.getRelPath()).map(s -> s + ":" + lineNumber)
                .orElse(escapedName));
        } else if (preferredTest == PreferredTest.ALL_IN_FOLDER) {
          params.getProgramParametersList().add(testNameParameter,
            Optional.ofNullable(featurePath).orElse(escapedName));
        }
        if (!StringUtils.isBlank(getTag())) {
          params.getProgramParametersList().add("--tag", getTag());
          for (String root : tagScanRoots(module)) {
            params.getProgramParametersList().add("--tag-root", root);
          }
        }
        if (!StringUtils.isBlank(getWorkingDirectory())) {
          params.getProgramParametersList().add("--working-dir", getWorkingDirectory());
        }
        if (!StringUtils.isBlank(getPath())) {
          params.getProgramParametersList().add("--relpath", getPath());
        }
        boolean debugging = DefaultDebugExecutor.EXECUTOR_ID.equals(env.getExecutor().getId());
        if (debugging) {
          // One scenario at a time while debugging. The agent can park several threads at once, but
          // a suspended run is far easier to follow when only one thing is moving - and it matches
          // what a JDWP suspend=y run already does to the whole VM.
          params.getProgramParametersList().add("--parallelism", "1");
        } else if (!StringUtils.isBlank(getParallelism())) {
          params.getProgramParametersList().add("--parallelism", getParallelism());
        }
        if (!StringUtils.isBlank(getEnv())) {
          params.getProgramParametersList().add("--environment", getEnv());
        }

        if (debugging) {
          String staleBreakpoints = staleJavaBreakpointsProblem(staleJavaBreakpointFiles(getProject()));
          if (staleBreakpoints != null) {
            throw new ExecutionException(staleBreakpoints);
          }
          try {
            // The debug-port field pins the channel's port for containers and firewalls that only
            // let certain ports through; blank takes a free one.
            debugChannel = new KarateDebugChannel(
              StringUtils.isBlank(getDebugPort()) ? 0 : Integer.parseInt(getDebugPort().trim()));
            debugChannel.start();
            params.getProgramParametersList().add("--debug-port", String.valueOf(debugChannel.port()));
          } catch (IOException | NumberFormatException e) {
            // No feature-file breakpoints, but the run itself still works.
            log.warn("Could not open the Karate debug channel; feature-file breakpoints are off", e);
            debugChannel = null;
          }
          if (isAttachJvmDebugger()) {
            jvmDebugPort = openJvmDebugPort(params, debugChannel);
          }
        }

        return params;
      }

      @Override protected @Nullable ConsoleView createConsole(@NotNull Executor executor) {
        List<SMTRunnerConsoleView> consoles = new ArrayList<>(1);
        ApplicationManager.getApplication().invokeAndWait(() -> {
          KarateTestConsoleConfiguration consoleProperties =
            new KarateTestConsoleConfiguration(getConfiguration(), "Karate", executor);

          SMTRunnerConsoleView console =
            SMTestRunnerConnectionUtil.createConsole(consoleProperties);
          console.initUI();
          console.addMessageFilter(new UppercutConsoleFilter(getProject()));
          // Debug ignores the parallelism setting - worth saying, but only to someone who asked for
          // more than one: telling a serial run that it is serial is noise on every single launch.
          if (DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId()) && parallelismAboveOne()) {
            console.print("Karate debugger: scenarios run one at a time while debugging, "
              + "not " + getParallelism() + " at a time.\n", ConsoleViewContentType.SYSTEM_OUTPUT);
          }
          consoles.add(console);
        }, ModalityState.any());

        return consoles.get(0);
      }
    };
  }


  /**
   * How long the run's first step waits for the opt-in JVM debugger to attach. Well under the agent's
   * own 15s handshake timeout, past which it starts with no breakpoint set at all - a JVM debugger
   * that never arrives must not cost the user their Karate breakpoints as well.
   */
  private static final long JVM_DEBUGGER_ATTACH_TIMEOUT_MILLIS = 8_000L;

  /**
   * Opens a JDWP port on the test JVM for the opt-in JVM debugger, and holds the Karate handshake
   * until it attaches. Returns the port, or 0 if none could be opened - in which case the run goes
   * ahead with the Karate debugger alone.
   */
  private static int openJvmDebugPort(@NotNull JavaParameters params,
    @Nullable KarateDebugChannel channel) {
    int port;
    try (ServerSocket free = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = free.getLocalPort();
    } catch (IOException e) {
      log.warn("Could not find a free port for the JVM debugger; running without it", e);
      return 0;
    }
    // server=y,suspend=n. suspend=y would guarantee nothing runs before the attach, at the price of
    // a test JVM parked forever if the attach never happens - the one outcome this debugger goes out
    // of its way to avoid. The Karate handshake gives the same guarantee with a timeout that ends in
    // a run rather than a hang, so the JVM itself never has to wait.
    params.getVMParametersList().add(
      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:" + port);
    if (channel != null) {
      channel.holdForJvmDebugger(JVM_DEBUGGER_ATTACH_TIMEOUT_MILLIS);
    }
    return port;
  }

  /** Whether this configuration asked for more than one scenario at a time; blank or junk means no. */
  private boolean parallelismAboveOne() {
    if (StringUtils.isBlank(getParallelism())) {
      return false;
    }
    try {
      return Integer.parseInt(getParallelism().trim()) > 1;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * Libraries to detect the Karate version from, scoped to the run's module when there is one.
   *
   * <p>A monorepo can hold modules on different Karate versions; a project-wide scan would see the
   * v2 jars of one module and pick the v2 runner for all of them. But module scoping alone would
   * regress setups that worked with the old project-wide scan - karate jars attached to a sibling
   * module (root-module configurations, shared test-support modules) - by triggering the bundled
   * fallback over the user's real Karate. So: when the module's own classpath has no karate jar at
   * all, widen back to the project scan; module scoping only decides when the module actually has
   * karate on it.
   */
  private VirtualFile[] karateLibraryRoots() {
    return KarateLibraries.rootsFor(getProject(), getConfigurationModule().getModule());
  }

  /**
   * Where a tag run looks for features: the module's source and resource roots, not the module
   * directory. A tag run has no file to name, so the runner hands Karate directories to walk;
   * walking the module root also walks the build output ({@code target/test-classes},
   * {@code build/resources/test}), where Maven and Gradle keep a copy of every feature - and each
   * tagged scenario ran twice, once per copy. With no module (or a module with no roots) the runner
   * falls back to the working directory as before.
   */
  static List<String> tagScanRoots(@Nullable Module module) {
    if (module == null) {
      return List.of();
    }
    return Arrays.stream(ModuleRootManager.getInstance(module).getSourceRoots(true))
      .map(VirtualFile::getPath)
      .toList();
  }


  /**
   * Why the classpath the JVM would be launched with cannot run Karate, or null if it can. Checked
   * before the bundled-Karate fallback, which would otherwise add its own jar and mask the problem.
   */
  static @Nullable String classpathProblem(List<String> classpathJarNames, @Nullable String moduleName) {
    if (classpathJarNames.stream().anyMatch(n -> n.startsWith("karate-"))) {
      return null;
    }
    if (moduleName == null) {
      return "This feature file is not inside any module, so the run has no classpath. Open the project "
        + "folder (not just the pom.xml or build file), or re-import the project from the Maven/Gradle tool "
        + "window, and run again. " + TROUBLESHOOTING;
    }
    return "Module '" + moduleName + "' has no Karate on its classpath, so the run would fail with \"Must "
      + "have karate-core on the classpath\". Add karate-junit5 (Karate 1) or karate-junit6 (Karate 2) to "
      + "the module, or run the feature from a module that has it. " + TROUBLESHOOTING;
  }

  /**
   * Why a Debug run cannot do what the user is about to expect of it, or null.
   *
   * <p>Breakpoints set in feature files by 3.0.1 and earlier were saved as <b>Java</b> line
   * breakpoints, because that is how feature-file debugging used to work. Nothing removes them on
   * upgrade: they sit in the gutter looking live and never pause, and a Karate breakpoint on the same
   * line appears beside them. Starting the run anyway means watching it go by with the breakpoints
   * apparently ignored - so say what is wrong before launching a JVM, the way the other refusals do.
   *
   * @param staleFeatureFiles the {@code .feature} files that still hold a Java line breakpoint
   */
  static @Nullable String staleJavaBreakpointsProblem(List<String> staleFeatureFiles) {
    if (staleFeatureFiles.isEmpty()) {
      return null;
    }
    String files = staleFeatureFiles.stream().distinct().sorted().collect(Collectors.joining(", "));
    return "This project still has Java line breakpoints in feature files, left by an earlier version "
      + "of Uppercut: " + files + ". They cannot pause a Karate run any more. Delete them under \"Java "
      + "Line Breakpoints\" in the Breakpoints dialog (Ctrl/Cmd+Shift+F8) and set them again - new "
      + "breakpoints in feature files are Karate breakpoints and work on both Karate versions. "
      + TROUBLESHOOTING;
  }

  /** The breakpoint type the platform saved feature-file breakpoints under before 3.0.2. */
  private static final String JAVA_LINE_BREAKPOINT_TYPE = "java-line";

  /** Feature files that still carry a Java line breakpoint, newest platform state each time. */
  private static List<String> staleJavaBreakpointFiles(Project project) {
    return ApplicationManager.getApplication().runReadAction((Computable<List<String>>) () -> Arrays.stream(
        XDebuggerManager.getInstance(project).getBreakpointManager().getAllBreakpoints())
      .filter(breakpoint -> JAVA_LINE_BREAKPOINT_TYPE.equals(breakpoint.getType().getId()))
      // A disabled leftover confuses nobody, and refusing a run over one would be its own annoyance.
      .filter(XBreakpoint::isEnabled)
      .filter(XLineBreakpoint.class::isInstance)
      .map(breakpoint -> ((XLineBreakpoint<?>) breakpoint).getPresentableFilePath())
      .filter(path -> path.endsWith("." + GherkinFileType.INSTANCE.getDefaultExtension()))
      .toList());
  }

  /** Every message the plugin refuses a run with is explained on this page. */
  static final String TROUBLESHOOTING = "See " + UppercutWebHelpProvider.SITE + "troubleshooting";

  /**
   * Fails the run only when the version override cannot possibly work: pinning V1 on a classpath that
   * has Karate 2 and no Karate 1, or V2 on one that has Karate 1 and no Karate 2.
   *
   * <p>Without this the run still fails, just incomprehensibly: forcing V1 onto a Karate 2 module
   * sends it down the v1 path, which reflects on {@code com.intuit.karate.RuntimeHook} - a class
   * Karate 2 removed - and reports "Must have karate-core on the classpath", even though karate-core
   * is right there and it is the setting that is wrong. AUTO never reaches this.
   *
   * <p>When both majors are visible (a stale transitive karate-core 2.x on a v1 module, or a module
   * mid-migration), or when the jar names say nothing about the version (unversioned local jars), the
   * pin is the user's word and wins - that is what the setting is for.
   */
  static void checkVersionOverrideMatchesClasspath(
    KarateSettingsState.KarateVersionPreference preference, List<String> libraryNames)
    throws ExecutionException {
    boolean hasKarate2 = libraryNames.stream().anyMatch(KarateRunConfiguration::isKarate2Jar);
    boolean hasKarate1 = libraryNames.stream().anyMatch(KarateRunConfiguration::isKarate1Jar);
    if (preference == KarateSettingsState.KarateVersionPreference.V1 && hasKarate2 && !hasKarate1) {
      throw new ExecutionException(
        "Karate version is pinned to V1 in Settings > Tools > Karate, but this module's classpath "
          + "only has Karate 2. Set it back to AUTO, or to V2, to run this feature. " + TROUBLESHOOTING);
    }
    if (preference == KarateSettingsState.KarateVersionPreference.V2 && hasKarate1 && !hasKarate2) {
      throw new ExecutionException(
        "Karate version is pinned to V2 in Settings > Tools > Karate, but this module's classpath "
          + "only has Karate 1 (expected karate-core 2.x or karate-junit6). Set it back to AUTO, or to "
          + "V1, to run this feature. " + TROUBLESHOOTING);
    }
  }

  /**
   * Decides whether to drive Karate 2.x. Honors the settings override; on AUTO, detects
   * Karate 2 from library jar names (karate-junit5 was renamed to karate-junit6 in v2).
   */
  public static boolean isKarateV2(KarateSettingsState.KarateVersionPreference preference,
    java.util.stream.Stream<String> libraryNames) {
    if (preference == KarateSettingsState.KarateVersionPreference.V1) {
      return false;
    }
    if (preference == KarateSettingsState.KarateVersionPreference.V2) {
      return true;
    }
    return libraryNames.anyMatch(KarateRunConfiguration::isKarate2Jar);
  }

  /** karate-core/karate-junit6 2.x, or a karate-junit6 jar of any version - that artifact only exists in v2. */
  static boolean isKarate2Jar(String name) {
    return name.matches("karate-(core|junit6)-2\\..*") || name.matches("karate-junit6(-.*)?\\.jar");
  }

  /**
   * The version in a Karate jar's file name, or null when the name carries none. Local or shaded
   * jars are often unversioned ({@code karate-core.jar}), which is why the caller treats a null as
   * "say nothing" rather than as a mismatch.
   */
  static @Nullable String karateJarVersion(String jarName) {
    Matcher matcher = KARATE_JAR_VERSION.matcher(jarName);
    return matcher.matches() ? matcher.group(1) : null;
  }

  private static final Pattern KARATE_JAR_VERSION =
    Pattern.compile("karate-(?:core|junit5|junit6)-(\\d+\\.\\d+(?:\\.\\d+)?(?:[.-][A-Za-z0-9]+)*)\\.jar");

  /** karate-core/karate-junit5 1.x, or a karate-junit5 jar of any version - that artifact only exists in v1. */
  static boolean isKarate1Jar(String name) {
    return name.matches("karate-(core|junit5)-1\\..*") || name.matches("karate-junit5(-.*)?\\.jar");
  }

  @Override public void checkConfiguration() {
  }

  @Override public boolean canRunOn(@NotNull TargetEnvironmentConfiguration target) {
    return true;
  }

  @Override public void writeExternal(@NotNull Element element) {
    element.setAttribute("lineNumber", String.valueOf(lineNumber));
    element.setAttribute("testName", testName.orElse(""));
    element.setAttribute("testDescription", Optional.ofNullable(testDescription).orElse(""));
    element.setAttribute("featureName", Optional.ofNullable(featureName).orElse(""));
    element.setAttribute("scenarioName", Optional.ofNullable(scenarioName).orElse(""));
    element.setAttribute("debugPort", Optional.ofNullable(debugPort).orElse(""));
    element.setAttribute("attachJvmDebugger", String.valueOf(attachJvmDebugger));
    element.setAttribute("tag", Optional.ofNullable(tag).orElse(""));
    element.setAttribute("path", Optional.ofNullable(path).orElse(""));
    element.setAttribute("preferredTest", preferredTest.name);
    element.setAttribute("parallelism", Optional.ofNullable(parallelism).orElse(
      Optional.ofNullable(KarateSettingsState.getInstance().getDefaultParallelism()).map(String::valueOf).orElse("1")));
    element.setAttribute("folderHasFeatures", String.valueOf(folderHasFeatures));
    element.setAttribute("relPath", Optional.ofNullable(relPath).orElse(""));
    super.writeExternal(element);
  }

  @Override
  public void readExternal(@NotNull Element element) {
    super.readExternal(element);
    lineNumber = Integer.parseInt(Optional.ofNullable(element.getAttributeValue("lineNumber")).orElse("0"));
    testName = Optional.ofNullable(element.getAttributeValue("testName"));
    testDescription = element.getAttributeValue("testDescription");
    featureName = element.getAttributeValue("featureName");
    scenarioName = element.getAttributeValue("scenarioName");
    debugPort = element.getAttributeValue("debugPort");
    attachJvmDebugger = Boolean.parseBoolean(element.getAttributeValue("attachJvmDebugger"));
    tag = element.getAttributeValue("tag");
    path = element.getAttributeValue("path");
    preferredTest =
      Arrays.stream(PreferredTest.values()).filter(s -> s.name.equals(element.getAttributeValue("preferredTest")))
        .findFirst().orElse(PreferredTest.WHOLE_FILE);
    parallelism = element.getAttributeValue("parallelism");
    folderHasFeatures = Boolean.parseBoolean(element.getAttributeValue("folderHasFeatures"))
      // the attribute's name before the rule widened from "only features" to "any features"
      || Boolean.parseBoolean(element.getAttributeValue("allInFolderAreFeature"));
    relPath = element.getAttributeValue("relPath");
  }

  public void setTestName(String testName) {
    this.testName = Optional.ofNullable(testName);
  }

  public String getEnv() {
    return StringUtil.isEmpty(environment)
      ? String.valueOf(KarateSettingsState.getInstance().getDefaultEnvironment()) : environment;
  }

  public String getParallelism() {
    return StringUtil.isEmpty(parallelism)
      ? String.valueOf(KarateSettingsState.getInstance().getDefaultParallelism()) : parallelism;
  }

  public void setEnv(String environment) {
    this.environment = environment;
  }
}
