package com.rankweis.uppercut.karate.run;

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
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.NotNull;

public class KarateOutputToGeneralTestEventsConverter extends OutputToGeneralTestEventsConverter {

  TestConsoleProperties testConsoleProperties;
  private Key myCurrentOutputType;
  private ServiceMessageVisitor myCurrentVisitor;
  private boolean testSuiteStarted = false;
  private final Map<String, KarateItem> threadToScenario = new HashMap<>();
  private final Map<Integer, KarateItem> idToItem = new HashMap<>();
  private final Map<String, Integer> featureNameToId = new HashMap<>();
  private final Random random = new Random();
  private String currentThreadGroup = "empty";

  @Data
  @Builder
  @EqualsAndHashCode
  static class KarateItem {

    private String name;
    private int parentId;
    private int id;
    @Builder.Default
    StringBuilder output = new StringBuilder();
  }

  public KarateOutputToGeneralTestEventsConverter(@NotNull String testFrameworkName,
    @NotNull TestConsoleProperties consoleProperties) {
    super(testFrameworkName, consoleProperties);
    this.testConsoleProperties = consoleProperties;
  }

  @Override public void process(String text, Key outputType) {
    if (text != null) {
      super.process(text, outputType);
    }
  }

  @Override protected boolean processServiceMessages(@NotNull String text, @NotNull Key<?> outputType,
    @NotNull ServiceMessageVisitor visitor) throws ParseException {
    myCurrentOutputType = outputType;
    myCurrentVisitor = visitor;
    return processEventText(text);
  }

  private boolean processEventText(final String text) throws ParseException {
    if (featureStartEnd(text)) {
      return true;
    } else if (scenarioStartEnd(text)) {
      return true;
    }
    setCurrentThread(text);
    if (text.contains("karate.env is:")) {
      return doProcessServiceMessages(ServiceMessageBuilder.testsStarted().toString());
    } else if (text.contains("HTML report")
      || text.contains("---------------------------------------------------------")
      || text.startsWith("feature: ") || text.startsWith(
      "scenarios: ")) {
      return false;
    } else if (processStartFinishText(text)) {
      return true;
    } else {
      return process(text);
    }
  }

  private boolean doProcessServiceMessages(@NotNull final String text) {
    if (this.myCurrentOutputType == null || this.myCurrentVisitor == null) {
      return false;
    }
    try {
      return super.processServiceMessages(text, this.myCurrentOutputType, this.myCurrentVisitor);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean process(String text) {
    KarateItem scenario = threadToScenario.get(currentThreadGroup);
    if (scenario != null) {
      ServiceMessageBuilder msgScenario =
        ServiceMessageBuilder.testStdOut(scenario.getName()).addAttribute("out", text);
      finishMessage(msgScenario, scenario);
      return true;
    } else {
      return false;
    }
  }

  private boolean featureStartEnd(String text) {
    Matcher matcher =
      Pattern.compile("\\[([^]]*)].* FeatureFileName: (.*), (.*)").matcher(text.trim());
    if (!matcher.matches()) {
      return false;
    }
    String threadGroup = matcher.group(1);
    String featureName = matcher.group(2);
    String startOrFinish = matcher.group(3);
    if (startOrFinish.equals("START")) {
      ServiceMessageBuilder testStarted = ServiceMessageBuilder.testSuiteStarted(featureName);
      Arrays.stream(ModuleManager.getInstance(testConsoleProperties.getProject()).getModules())
        .flatMap(m -> Arrays.stream(ModuleRootManager.getInstance(m).getSourceRoots()))
        .map(root -> VfsUtil.findRelativeFile(featureName, root)).filter(Objects::nonNull).findFirst()
        .ifPresent(file -> testStarted.addAttribute("locationHint", "file://" + file.getPath() + ":1"));
      int featureId = random.nextInt();
      KarateItem item = KarateItem.builder().id(featureId).name(featureName).parentId(0).build();
      featureNameToId.put(featureName, featureId);
      idToItem.put(featureId, item);
      finishMessage(testStarted, item);
      return true;
    }
    return false;
  }

  private boolean scenarioStartEnd(String text) {
    Matcher matcher =
      Pattern.compile("\\[([^]]*)].* Scenario name: (.*), featureFileName: (.*), id ([\\d]+), (.*)")
        .matcher(text.trim());
    if (!matcher.matches()) {
      return false;
    }
    String threadGroup = matcher.group(1);
    String scenarioName = matcher.group(2);
    String featureName = matcher.group(3);
    Integer scenarioId = Integer.parseInt(matcher.group(4));
    String startOrFinish = matcher.group(5);
    KarateItem item = idToItem.getOrDefault(scenarioId,
      KarateItem.builder().id(scenarioId).name(scenarioName).parentId(featureNameToId.getOrDefault(featureName, 0))
        .build());
    if (startOrFinish.equals("START")) {
      ServiceMessageBuilder scenarioStarted = ServiceMessageBuilder.testStarted(scenarioName);
      Arrays.stream(ModuleManager.getInstance(testConsoleProperties.getProject()).getModules())
        .flatMap(m -> {
          ArrayList<VirtualFile> vfs = new ArrayList<>();
          vfs.addAll(Arrays.stream(ModuleRootManager.getInstance(m).getSourceRoots()).toList());
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
              int index = psiFile.getText().indexOf(scenarioName);
              int num = 1;
              if (index > 0) {
                num = PsiDocumentManager.getInstance(testConsoleProperties.getProject()).getDocument(psiFile)
                  .getLineNumber(index) + 1;
              }
              return num;
            });
          scenarioStarted.addAttribute("locationHint", "file://" + file.getPath() + ":" + lineNumber);
        });
      threadToScenario.put(threadGroup, item);
      finishMessage(scenarioStarted, item);
    } else if (startOrFinish.equals("FINISH")) {
      ServiceMessageBuilder scenarioFinished = ServiceMessageBuilder.testFinished(scenarioName);
      finishMessage(scenarioFinished, item);
      idToItem.remove(scenarioId);
    } else {
      ServiceMessageBuilder scenarioFailed =
        ServiceMessageBuilder.testFailed(scenarioName).addAttribute("message", startOrFinish);
      finishMessage(scenarioFailed, item);
      idToItem.remove(scenarioId);
    }
    return true;
  }

  private void setCurrentThread(String text) {
    Matcher matcher =
      Pattern.compile("\\[([^]]*)].*").matcher(text.trim());
    String threadGroup;
    if (matcher.matches()) {
      threadGroup = matcher.group(1);
      this.currentThreadGroup = threadGroup;
    }
  }

  private void processAllPrevious(KarateItem item) {
    KarateItem featureName = idToItem.get(item.getParentId());
    for (String s : item.getOutput().toString().split("\n")) {
      ServiceMessageBuilder msgScenario =
        ServiceMessageBuilder.testStdOut(item.getName()).addAttribute("out", s + "\n");
      finishMessage(msgScenario, item);
    }
    item.setOutput(new StringBuilder());
  }

  //  private void processFeatureName(String text) {
  //    Matcher matcher = Pattern.compile(".*feature: classpath:(.*)").matcher(text.trim());
  //    if (matcher.matches()) {
  //      String[] split = matcher.group(1).split("/");
  //      String featureName = split[split.length - 1];
  //      featureToThread.putIfAbsent(featureName, currentThreadGroup);
  //    }
  //  }

  private boolean processStartFinishText(@NotNull final String statement) {
    Matcher matcher =
      Pattern.compile(".*<<(.*)>> feature (\\d+) of (\\d+)+ \\(\\d+ remaining\\) (.*)").matcher(statement.trim());
    if (!matcher.matches()) {
      return false;
    }
    String result = matcher.group(1);
    int testCount = Integer.parseInt(matcher.group(2));
    String totalCount = matcher.group(3);
    String feature = matcher.group(4).replace("classpath:", "");
    String[] split = feature.split("/");
    ServiceMessageBuilder message;
    if (result.equals("pass")) {
      message = ServiceMessageBuilder.testSuiteFinished(feature).addAttribute("captureStandardOutput", "true");
    } else if (result.equals("fail")) {
      message = ServiceMessageBuilder.testSuiteFinished(feature).addAttribute("captureStandardOutput", "true");
      //      message = ServiceMessageBuilder.testFailed(feature).addAttribute("message", "");
    } else {
      throw new RuntimeException("Unknown result: " + result);
    }
    //    testToCount.putIfAbsent(threadToScenario.get(currentThreadGroup), testCount);
    KarateItem item = idToItem.get(featureNameToId.get(feature));
    processAllPrevious(item);
    finishMessage(message, item);
    return true;
  }

  private boolean finishMessage(@NotNull ServiceMessageBuilder msg, KarateItem item) {
    msg.addAttribute("nodeId", String.valueOf(item.getId()));
    msg.addAttribute("parentNodeId", String.valueOf(item.getParentId()));
    return doProcessServiceMessages(msg.toString());
  }
}
