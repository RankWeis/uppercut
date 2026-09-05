package com.rankweis.uppercut.karate.run;

import static com.rankweis.uppercut.karate.run.KarateOutputToGeneralTestEventsConverter.KarateConfigState.FAILED;
import static com.rankweis.uppercut.karate.run.KarateOutputToGeneralTestEventsConverter.KarateConfigState.NO_RESULT;
import static com.rankweis.uppercut.karate.run.KarateOutputToGeneralTestEventsConverter.KarateConfigState.SUCCEEDED;

import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.ServiceMessageBuilder;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateOutputToGeneralTestEventsConverter extends OutputToGeneralTestEventsConverter {

  private static final Logger LOG = Logger.getInstance(KarateOutputToGeneralTestEventsConverter.class);

  enum KarateConfigState {
    SUCCEEDED,
    FAILED,
    NO_RESULT
  }

  TestConsoleProperties testConsoleProperties;
  private Key<?> myCurrentOutputType;
  private KarateItem karateConfigItem;
  private KarateConfigState karateConfigState = NO_RESULT;
  private ServiceMessageVisitor myCurrentVisitor;
  private final Map<String, LinkedList<KarateItem>> threadToScenarioStack = new HashMap<>();
  private final Map<Integer, KarateItem> idToItem = new HashMap<>();
  private String currentThreadGroup = "main";
  private static final String UPPERCUT_PREFIX = "<<UPPERCUT>>";
  public static final Pattern UPPERCUT_LOG_PATTERN =
    Pattern.compile("^\\[([^]]+)] ([\\d:.,]+) (\\w+) ?(.*)\n?");
  public static final Pattern SCENARIO_NAME =
    Pattern.compile(
      "^\\[([^]]*)].* Scenario name: (.*), featureFileName: (.*), id (\\d+), featureId (\\d+), (.*) <<UPPERCUT>>\n?$");
  public static final Pattern FEATURE_FILE_NAME =
    Pattern.compile(".*KarateTestRunner - FeatureFileName: ([^,]*), id: (\\d+), (.*) <<UPPERCUT>>\n?");
  private static final Pattern FEATURE_LINE_PATTERN = Pattern.compile("feature: \\S+\n?");

  @Data
  @Builder
  @EqualsAndHashCode
  static class KarateItem {

    private String name;
    private int parentId;
    private int id;
  }

  public KarateOutputToGeneralTestEventsConverter(@NotNull String testFrameworkName,
    @NotNull TestConsoleProperties consoleProperties) {
    super(testFrameworkName, consoleProperties);
    this.testConsoleProperties = consoleProperties;
  }

  @Override public void process(String text, Key outputType) {
    if (text != null) {
      if (text.startsWith(UPPERCUT_PREFIX)) {
        text = text.substring(UPPERCUT_PREFIX.length());
      }
      // The platform buffers a partial stdout line until its newline and keys that buffer by output
      // type. Recolouring is only ever applied to stdout: a real stderr chunk must keep its own type,
      // or it gets appended into a half-received stdout line - for a v2 run that is a <<UPPERCUT-V2>>
      // event line long enough to span several reads, and the splice corrupts its JSON.
      if (ProcessOutputType.isStderr(outputType)) {
        super.process(text, outputType);
        return;
      }
      Matcher matcher = UPPERCUT_LOG_PATTERN.matcher(text);
      if (matcher.matches()) {
        String logLevel = matcher.group(3);
        if (List.of("ERROR", "WARN", "SEVERE", "FATAL").contains(logLevel)) {
          myCurrentOutputType = ProcessOutputType.STDERR;
        } else if (List.of("INFO", "DEBUG", "TRACE").contains(logLevel)) {
          myCurrentOutputType = ProcessOutputType.STDOUT;
        }
      } else if (myCurrentOutputType == null) {
        myCurrentOutputType = outputType;
      }
      super.process(text, myCurrentOutputType);
    }
  }

  private boolean process(String text) {
    LinkedList<KarateItem> karateItems =
      threadToScenarioStack.computeIfAbsent(currentThreadGroup, k -> new LinkedList<>());
    if (text.strip().endsWith("<<UPPERCUT>>")) {
      // Safety guard: consume structural messages that shouldn't be shown to user.
      return true;
    }
    if (!karateItems.isEmpty()) {
      KarateItem scenario = karateItems.peek();
      for (String s : text.splitWithDelimiters("\n", 2)) {
        ServiceMessageBuilder msgScenario;
        if (myCurrentOutputType == ProcessOutputType.STDOUT) {
          msgScenario = ServiceMessageBuilder.testStdOut(scenario.getName()).addAttribute("out", s);
        } else {
          msgScenario = ServiceMessageBuilder.testStdErr(scenario.getName()).addAttribute("out", s);
        }
        finishMessage(msgScenario, scenario);
      }
      return true;
    }
    return false;
  }

  @Override protected boolean processServiceMessages(@NotNull String text, @NotNull Key<?> outputType,
    @NotNull ServiceMessageVisitor visitor) {
    myCurrentOutputType = outputType;
    myCurrentVisitor = visitor;
    return processEventText(text);
  }

  private boolean processEventText(final String text) {
    if (text.startsWith(KarateV2EventProcessor.EVENT_PREFIX)) {
      return getV2EventProcessor().process(text);
    }
    Matcher matcher = UPPERCUT_LOG_PATTERN.matcher(text);
    if (text.contains("[config]") || (karateConfigItem != null && text.contains(
      ">> " + karateConfigItem.getName() + " failed"))) {

      if (karateJsStartedFailed(text)) {
        return true;
      }
    }

    if (karateConfigState == NO_RESULT && karateConfigItem != null) {
      if (FEATURE_LINE_PATTERN.matcher(text.replace("<<NEWLINE>>", "\n")).matches()) {
        ServiceMessageBuilder karateConfig =
          ServiceMessageBuilder.testSuiteFinished(karateConfigItem.getName());
        finishMessage(karateConfig, karateConfigItem);
        karateConfigState = SUCCEEDED;
      }
    }

    if (!matcher.matches()) {
      return process(text);
    }
    setCurrentThread(text);
    if (text.contains("karate.env is:")) {
      return doProcessServiceMessages(ServiceMessageBuilder.testsStarted().toString());
    }
    if (featureStartEnd(text) || scenarioStartEnd(text)) {
      return true;
    }
    // Strip the UPPERCUT prefix and forward the log message content to scenario output.
    String content = matcher.group(4);
    if (content != null && !content.isEmpty()) {
      return process(content + "\n");
    }
    return true;
  }

  private boolean doProcessServiceMessages(@NotNull final String text) {
    if (this.myCurrentOutputType == null || this.myCurrentVisitor == null) {
      return false;
    }
    try {
      super.processServiceMessages(text, this.myCurrentOutputType, this.myCurrentVisitor);
      return true;
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  private static final Pattern CONFIG_PATTERN = Pattern.compile("\\[config] (\\S+)\n?");

  private boolean karateJsStartedFailed(String text) {
    String karateConfigName = karateConfigItem == null ? "null" : karateConfigItem.getName();
    Matcher m = CONFIG_PATTERN.matcher(text);
    if (karateConfigItem == null && m.find()) {
      String[] parts = m.group(1).split(":");
      karateConfigName = parts[parts.length - 1];
      int rand = ThreadLocalRandom.current().nextInt();
      karateConfigItem = addFeatureToTree(karateConfigName, rand);
      return true;
    } else if (karateConfigState == NO_RESULT
      && text.replace("<<NEWLINE>>", "\n").contains("\n>> " + karateConfigName + " failed\n")) {
      ServiceMessageBuilder scenarioFailed =
        ServiceMessageBuilder.testFailed(karateConfigName)
          .addAttribute("message", "Running config " + karateConfigName + " failed");
      finishMessage(scenarioFailed, karateConfigItem);
      karateConfigState = FAILED;
      return true;
    }
    return false;

  }

  private boolean featureStartEnd(String text) {
    Matcher matcher =
      FEATURE_FILE_NAME.matcher(text.trim());
    if (!matcher.matches()) {
      return false;
    }
    String featureName = matcher.group(1);
    int id = Integer.parseInt(matcher.group(2));
    String startOrFinish = matcher.group(3);
    if (startOrFinish.equals("START")) {
      return true;
    } else if (startOrFinish.equals("FINISH")) {
      ServiceMessageBuilder message =
        ServiceMessageBuilder.testSuiteFinished(featureName);

      KarateItem item = idToItem.get(id);
      if (item != null) {
        finishMessage(message, item);
      }
      return true;
    }
    return false;
  }

  private KarateItem addFeatureToTree(String featureName, int id) {
    KarateItem item = KarateItem.builder().id(id).name(featureName).parentId(0).build();
    ServiceMessageBuilder testStarted = ServiceMessageBuilder.testSuiteStarted(featureName);
    Arrays.stream(ModuleManager.getInstance(testConsoleProperties.getProject()).getModules())
      .flatMap(m -> Arrays.stream(ModuleRootManager.getInstance(m).getSourceRoots()))
      .map(root -> VfsUtil.findRelativeFile(featureName, root)).filter(Objects::nonNull).findFirst()
      .ifPresent(file -> testStarted.addAttribute("locationHint", "file://" + file.getPath() + ":1"));

    if (!idToItem.containsKey(id)) {
      idToItem.put(id, item);
      finishMessage(testStarted, item);
    }
    return item;
  }

  private boolean scenarioStartEnd(String text) {
    Matcher matcher = SCENARIO_NAME.matcher(text.trim());
    if (!matcher.matches()) {
      return false;
    }
    String threadGroup = matcher.group(1);
    String scenarioName = matcher.group(2);
    String featureName = matcher.group(3);
    Integer scenarioId = Integer.parseInt(matcher.group(4));
    int featureId = Integer.parseInt(matcher.group(5));
    String startOrFinish = matcher.group(6);
    int parentId;
    String[] splitScenarioName = scenarioName.split("##");
    if (splitScenarioName.length > 1) {
      parentId = Integer.parseInt(splitScenarioName[splitScenarioName.length - 2]);
      scenarioName = splitScenarioName[splitScenarioName.length - 1];
    } else {
      scenarioName = matcher.group(2);
      addFeatureToTree(featureName, featureId);
      parentId = featureId;
    }
    if (startOrFinish.equals("START")) {
      ServiceMessageBuilder scenarioStarted = ServiceMessageBuilder.testStarted(scenarioName);
      String finalScenarioName = scenarioName;
      Arrays.stream(ModuleManager.getInstance(testConsoleProperties.getProject()).getModules())
        .flatMap(m -> {
          ArrayList<VirtualFile> vfs =
            new ArrayList<>(Arrays.stream(ModuleRootManager.getInstance(m).getSourceRoots()).toList());
          vfs.add(ProjectUtil.guessProjectDir(testConsoleProperties.getProject()));
          return vfs.stream();
        })
        .map(root -> VfsUtil.findRelativeFile(featureName, root)).filter(Objects::nonNull).findFirst()
        .ifPresent(file -> {
          int lineNumber = ApplicationManager.getApplication()
            .runReadAction((Computable<Integer>) () -> {
              PsiFile psiFile = PsiManager.getInstance(testConsoleProperties.getProject()).findFile(file);
              if (psiFile == null) {
                return -1;
              }
              int index = psiFile.getText().indexOf(finalScenarioName);
              int num = 1;
              if (index > 0) {
                num = Objects.requireNonNull(
                    PsiDocumentManager.getInstance(testConsoleProperties.getProject()).getDocument(psiFile))
                  .getLineNumber(index) + 1;
              }
              return num;
            });
          scenarioStarted.addAttribute("locationHint", "file://" + file.getPath() + ":" + lineNumber);
        });
      LinkedList<KarateItem> karateItems =
        threadToScenarioStack.computeIfAbsent(threadGroup, (k) -> new LinkedList<>());
      KarateItem item = idToItem.computeIfAbsent(scenarioId,
        (k) -> KarateItem.builder().id(scenarioId).name(finalScenarioName).parentId(parentId).build());
      karateItems.push(item);
      finishMessage(scenarioStarted, item);
    } else if (startOrFinish.equals("FINISH")) {
      if (idToItem.containsKey(scenarioId)) {
        ServiceMessageBuilder scenarioFinished = ServiceMessageBuilder.testFinished(scenarioName);
        finishMessage(scenarioFinished, idToItem.get(scenarioId));
        idToItem.remove(scenarioId);
        LinkedList<KarateItem> karateItems = threadToScenarioStack.get(threadGroup);
        if (karateItems != null && !karateItems.isEmpty()) {
          karateItems.pop();
        }
      }
    } else {
      if (idToItem.containsKey(scenarioId)) {
        ServiceMessageBuilder scenarioFailed =
          ServiceMessageBuilder.testFailed(scenarioName)
            .addAttribute("message", startOrFinish.replace("<<NEWLINE>>", "\n"));
        finishMessage(scenarioFailed, idToItem.get(scenarioId));
        idToItem.remove(scenarioId);
        LinkedList<KarateItem> karateItems = threadToScenarioStack.get(threadGroup);
        if (karateItems != null && !karateItems.isEmpty()) {
          karateItems.pop();
        }
      } else if (idToItem.containsKey(featureId)) {
        ServiceMessageBuilder scenarioFailed =
          ServiceMessageBuilder.testFailed(featureName)
            .addAttribute("message", startOrFinish.replace("<<NEWLINE>>", "\n"));
        finishMessage(scenarioFailed, idToItem.get(featureId));
        idToItem.remove(featureId);
      }
    }
    return true;
  }

  private void setCurrentThread(String text) {
    Matcher matcher =
      UPPERCUT_LOG_PATTERN.matcher(text.trim());
    String threadGroup;
    if (matcher.matches()) {
      threadGroup = matcher.group(1);
      this.currentThreadGroup = threadGroup;
    }
  }

  private void finishMessage(@NotNull ServiceMessageBuilder msg, KarateItem item) {
    msg.addAttribute("nodeId", String.valueOf(item.getId()));
    msg.addAttribute("parentNodeId", String.valueOf(item.getParentId()));
    doProcessServiceMessages(msg.toString());
  }

  private KarateV2EventProcessor v2EventProcessor;

  private KarateV2EventProcessor getV2EventProcessor() {
    if (v2EventProcessor == null) {
      v2EventProcessor = new KarateV2EventProcessor(new KarateV2EventProcessor.EventSink() {
        @Override public void testsStarted() {
          doProcessServiceMessages(ServiceMessageBuilder.testsStarted().toString());
        }

        @Override public void emit(@NotNull ServiceMessageBuilder message, int nodeId, int parentNodeId) {
          message.addAttribute("nodeId", String.valueOf(nodeId));
          message.addAttribute("parentNodeId", String.valueOf(parentNodeId));
          doProcessServiceMessages(message.toString());
        }

        @Override public String resolveLocation(@NotNull String featurePath, int line) {
          VirtualFile file = findFeatureFile(featurePath);
          return file == null ? null : "file://" + file.getPath() + ":" + line;
        }
      });
    }
    return v2EventProcessor;
  }

  /**
   * Karate 2.x event paths are relative to the working directory and point at the compiled test
   * classpath (e.g. {@code build/resources/test/sample/x.feature} for Gradle, {@code target/test-classes/...}
   * for Maven). Strip leading segments until a suffix resolves against a source root; only when the
   * whole stripping pass finds nothing, fall back to the project dir.
   *
   * <p>The two passes must not be interleaved: the runtime path resolves against the project dir
   * as-is (the compiled copy under {@code build/} really exists), so a combined root list would
   * short-circuit on the generated file before stripping ever produced the source-relative suffix.
   * Navigation would then open the build copy, where edits and breakpoints are silently discarded
   * on the next build.
   */
  private VirtualFile findFeatureFile(String featurePath) {
    List<VirtualFile> sourceRoots =
      Arrays.stream(ModuleManager.getInstance(testConsoleProperties.getProject()).getModules())
        .flatMap(m -> Arrays.stream(ModuleRootManager.getInstance(m).getSourceRoots()))
        .toList();
    VirtualFile inSourceRoot = resolveByStripping(featurePath, sourceRoots);
    if (inSourceRoot != null) {
      return inSourceRoot;
    }
    LOG.info("Karate feature path did not resolve against any source root, falling back to project dir. "
      + "path=" + featurePath + " sourceRoots=" + sourceRoots);
    VirtualFile projectDir = ProjectUtil.guessProjectDir(testConsoleProperties.getProject());
    return projectDir == null ? null : resolveByStripping(featurePath, List.of(projectDir));
  }

  private static @Nullable VirtualFile resolveByStripping(String featurePath, List<VirtualFile> roots) {
    // findFileByRelativePath, not VfsUtil.findRelativeFile: the latter treats an absolute path
    // (e.g. C:/... - what Karate 2 emits when the working dir is not the module root) as absolute and
    // ignores the base root entirely, resolving the build-output copy on the first iteration no
    // matter which roots are tried first.
    String candidate = featurePath.replace('\\', '/');
    while (!candidate.isEmpty()) {
      String finalCandidate = candidate;
      VirtualFile found = roots.stream().filter(Objects::nonNull)
        .map(root -> root.findFileByRelativePath(finalCandidate))
        .filter(Objects::nonNull)
        .findFirst().orElse(null);
      if (found != null) {
        return found;
      }
      int slash = candidate.indexOf('/');
      if (slash < 0) {
        return null;
      }
      candidate = candidate.substring(slash + 1);
    }
    return null;
  }
}
