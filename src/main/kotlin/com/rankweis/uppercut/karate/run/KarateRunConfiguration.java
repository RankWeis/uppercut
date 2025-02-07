package com.rankweis.uppercut.karate.run;

import com.intellij.debugger.impl.GenericDebuggerRunnerSettings;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.LanguageRuntimeType;
import com.intellij.execution.target.TargetEnvironmentAwareRunProfile;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.rankweis.uppercut.settings.KarateSettingsState;
import com.rankweis.uppercut.testrunner.KarateTestRunner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  @Getter @Setter private String tag;
  @Getter @Setter private String path;
  @Getter @Setter private PreferredTest preferredTest = PreferredTest.WHOLE_FILE;
  @Setter private String parallelism;
  @Getter @Setter private boolean allInFolderAreFeature = false;
  private String environment;


  protected KarateRunConfiguration(@NotNull Project project,
    @NotNull ConfigurationFactory factory, @Nullable String name) {
    super(name, project, factory);
    this.setMainClassName("com.rankweis.uppercut.testrunner.KarateTestRunner");
  }

  @Override public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new KarateSettingsEditor(getProject(), getFactory(), getName());
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {

    if (env.getRunnerSettings() instanceof GenericDebuggerRunnerSettings genericDebuggerRunnerSettings) {
      genericDebuggerRunnerSettings.setLocal(true);
      genericDebuggerRunnerSettings.setTransport(DebuggerSettings.SOCKET_TRANSPORT);
      genericDebuggerRunnerSettings.setDebugPort("8091");
    }

    return new JavaApplicationCommandLineState<>(this, env) {
      @Override
      protected JavaParameters createJavaParameters() throws ExecutionException {
        final JavaParameters params = super.createJavaParameters();

        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        ClassLoader pluginClassLoader = this.getClass().getClassLoader();
        try {
          currentThread.setContextClassLoader(pluginClassLoader);
          params.getClassPath().add(PathUtil.getJarPathForClass(KarateTestRunner.class));
          // code working with ServiceLoader here
        } finally {
          currentThread.setContextClassLoader(originalClassLoader);
        }
        if (env.getRunnerSettings() instanceof GenericDebuggerRunnerSettings genericDebuggerRunnerSettings) {
          params.getVMParametersList()
            .addParametersString(String.format("-agentlib:jdwp=transport=dt_socket,server=y,address=%s,suspend=y",
              genericDebuggerRunnerSettings.getDebugPort()));
        }
        if (getTestName().map(String::isBlank).orElse(false)) {
          String[] split = getName().split(":");
          if (split.length == 2) {
            setTestName(split[0]);
            myConfiguration.lineNumber = Integer.parseInt(split[1].split(" ")[0]);
          }
        }
        String escapedName = myConfiguration.getTestName().map(s -> s.replace(" ", "_")).orElse("");
        String testNameParameter = "--testname";
        if (preferredTest == PreferredTest.WHOLE_FILE) {
          params.getProgramParametersList().add(testNameParameter,
            Optional.ofNullable(myConfiguration.getRelPath()).filter(s -> !s.isBlank())
              .orElse(escapedName));
        } else if (preferredTest == PreferredTest.SINGLE_SCENARIO) {
          params.getProgramParametersList().add(testNameParameter,
            Optional.ofNullable(myConfiguration.getRelPath()).map(s -> s + ":" + lineNumber)
              .orElse(escapedName));
        } else if (preferredTest == PreferredTest.ALL_IN_FOLDER) {
          params.getProgramParametersList().add(testNameParameter,
            Optional.ofNullable(myConfiguration.getPath())
              .orElse(escapedName));
        }
        if (getTag() != null) {
          params.getProgramParametersList().add("--tag", getTag());
        }
        if (getWorkingDirectory() != null) {
          params.getProgramParametersList().add("--working-dir", getWorkingDirectory());
        }
        if (getPath() != null) {
          params.getProgramParametersList().add("--relpath", getPath());
        }
        if (getParallelism() != null) {
          params.getProgramParametersList().add("--parallelism", getParallelism());
        }
        if (StringUtils.isNotEmpty(getEnv())) {
          params.getProgramParametersList().add("--environment", getEnv());
        }
        ReadAction.run(() -> JavaRunConfigurationExtensionManager.getInstance()
          .updateJavaParameters(KarateRunConfiguration.this, params, getRunnerSettings(), executor));

        return params;
      }

      @Override protected @NotNull OSProcessHandler startProcess() throws ExecutionException {
        return super.startProcess();
      }

      @Override protected @Nullable ConsoleView createConsole(@NotNull Executor executor) {
        List<SMTRunnerConsoleView> consoles = new ArrayList<>(1);
        ApplicationManager.getApplication().invokeAndWait(() -> {
          KarateTestConsoleConfiguration consoleProperties =
            new KarateTestConsoleConfiguration(getConfiguration(), "Karate", executor);

          SMTRunnerConsoleView console =
            SMTestRunnerConnectionUtil.createConsole(consoleProperties);
          console.initUI();
          console.addMessageFilter(new UrlFilter(getProject()));
          consoles.add(console);
        }, ModalityState.any());

        return consoles.get(0);
      }
    };
  }


  @Override public void checkConfiguration() {
  }

  @Override public boolean canRunOn(@NotNull TargetEnvironmentConfiguration target) {
    return true;
  }

  @Override public @Nullable LanguageRuntimeType<?> getDefaultLanguageRuntimeType() {
    return null;
  }

  @Override public @Nullable String getDefaultTargetName() {
    return "";
  }

  @Override public void setDefaultTargetName(@Nullable String targetName) {
  }

  @Override public void writeExternal(@NotNull Element element) {
    element.setAttribute("lineNumber", String.valueOf(lineNumber));
    element.setAttribute("testName", testName.orElse(""));
    element.setAttribute("testDescription", Optional.ofNullable(testDescription).orElse(""));
    element.setAttribute("featureName", Optional.ofNullable(featureName).orElse(""));
    element.setAttribute("scenarioName", Optional.ofNullable(scenarioName).orElse(""));
    element.setAttribute("tag", Optional.ofNullable(tag).orElse(""));
    element.setAttribute("path", Optional.ofNullable(path).orElse(""));
    element.setAttribute("preferredTest", preferredTest.name);
    element.setAttribute("parallelism", Optional.ofNullable(parallelism).orElse(
      Optional.ofNullable(KarateSettingsState.getInstance().getDefaultParallelism()).map(String::valueOf).orElse("1")));
    element.setAttribute("allInFolderAreFeature", String.valueOf(allInFolderAreFeature));
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
    tag = element.getAttributeValue("tag");
    path = element.getAttributeValue("path");
    preferredTest =
      Arrays.stream(PreferredTest.values()).filter(s -> s.name.equals(element.getAttributeValue("preferredTest")))
        .findFirst().orElse(PreferredTest.WHOLE_FILE);
    parallelism = element.getAttributeValue("parallelism");
    allInFolderAreFeature = Boolean.parseBoolean(element.getAttributeValue("allInFolderAreFeature"));
    relPath = element.getAttributeValue("relPath");
  }

  public void setTestName(String testName) {
    this.testName = Optional.ofNullable(testName);
  }

  public String getEnv() {
    return StringUtil.isEmpty(environment) ?
       String.valueOf(KarateSettingsState.getInstance().getDefaultEnvironment()) : environment;
  }

  public String getParallelism() {
    return StringUtil.isEmpty(parallelism) ?
       String.valueOf(KarateSettingsState.getInstance().getDefaultParallelism()) : parallelism;
  }

  public void setEnv(String environment) {
    this.environment = environment;
  }
}
