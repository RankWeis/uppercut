package com.rankweis.uppercut.karate.run;

import static com.rankweis.uppercut.karate.run.KarateOutputToGeneralTestEventsConverter.KarateConfigState.FAILED;
import static com.rankweis.uppercut.karate.run.KarateOutputToGeneralTestEventsConverter.KarateConfigState.NO_RESULT;
import static com.rankweis.uppercut.karate.run.KarateOutputToGeneralTestEventsConverter.KarateConfigState.SUCCEEDED;

import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.ServiceMessageBuilder;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootManager;
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
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;

public class KarateOutputToGeneralTestEventsConverter extends OutputToGeneralTestEventsConverter {

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
  public static final String UPPERCUT_LOG = "^<<UPPERCUT>>";
  public static final Pattern UPPERCUT_LOG_PATTERN =
    Pattern.compile(UPPERCUT_LOG + "\\[([^]]+)] ([\\d:.,]+) (\\w+) ?(.*)\n?");
  public static final Pattern SCENARIO_NAME =
    Pattern.compile(UPPERCUT_LOG
      + "\\[([^]]*)].* Scenario name: (.*), featureFileName: (.*), id (\\d+), featureId (\\d+), (.*) <<UPPERCUT>>\n?$");
  public static final Pattern FEATURE_FILE_NAME =
    Pattern.compile(".*KarateTestRunner - FeatureFileName: ([^,]*), id: (\\d+), (.*) <<UPPERCUT>>\n?");

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
    if (text.startsWith("<<UPPERCUT>>") || text.strip().endsWith("<<UPPERCUT>>")) {
      // Safety guard, should never hit this.
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
    Matcher matcher = UPPERCUT_LOG_PATTERN.matcher(text);
    if (text.contains("[config]") || (karateConfigItem != null && text.contains(
      ">> " + karateConfigItem.getName() + " failed"))) {

      if (karateJsStartedFailed(text)) {
        return true;
      }
    }

    if (karateConfigState == NO_RESULT && karateConfigItem != null) {
      if (text.replace("<<NEWLINE>>", "\n").matches("feature: \\S+\n?")) {
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
    // Always consume anything with <<UPPERCUT>> so it will never be shown to user.
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

  private boolean karateJsStartedFailed(String text) {
    Pattern p = Pattern.compile("\\[config] (\\S+)\n?");
    String karateConfigName = karateConfigItem == null ? "null" : karateConfigItem.getName();
    Pattern failedPattern = Pattern.compile("\n>> " + karateConfigName + " failed\n");
    Matcher m = p.matcher(text);
    if (karateConfigItem == null && m.find()) {
      karateConfigName = Arrays.stream(m.group(1).split(":")).toList().getLast();
      int rand = new Random().nextInt();
      karateConfigItem = addFeatureToTree(karateConfigName, rand);
      return true;
    } else if (karateConfigState == NO_RESULT && failedPattern.matcher(text.replace("<<NEWLINE>>", "\n")).find()) {
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
          int lineNumber = ReadAction.compute(
            () -> {
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
        if (!CollectionUtils.isEmpty(karateItems)) {
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
        if (!CollectionUtils.isEmpty(karateItems)) {
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
}
