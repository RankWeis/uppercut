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
import java.util.LinkedList;
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

  TestConsoleProperties testConsoleProperties;
  private Key<?> myCurrentOutputType;
  private ServiceMessageVisitor myCurrentVisitor;
  private final Map<String, LinkedList<KarateItem>> threadToScenarioStack = new HashMap<>();
  private final Map<Integer, KarateItem> idToItem = new HashMap<>();
  private final Map<String, Integer> featureNameToId = new HashMap<>();
  private final Random random = new Random();
  private String currentThreadGroup = "main";
  public static final String UPPERCUT_LOG = "^<<UPPERCUT>>";

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
      super.process(text, outputType);
    }
  }

  @Override protected boolean processServiceMessages(@NotNull String text, @NotNull Key<?> outputType,
    @NotNull ServiceMessageVisitor visitor) {
    myCurrentOutputType = outputType;
    myCurrentVisitor = visitor;
    return processEventText(text);
  }

  private boolean processEventText(final String text) {
    setCurrentThread(text);
    if (featureStartEnd(text) || scenarioStartEnd(text)) {
      return true;
    }
    if (text.contains("karate.env is:")) {
      return doProcessServiceMessages(ServiceMessageBuilder.testsStarted().toString());
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
      super.processServiceMessages(text, this.myCurrentOutputType, this.myCurrentVisitor);
      return true;
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean process(String text) {
    boolean stdOut = !text.contains("ERROR com.intuit.karate");
    LinkedList<KarateItem> karateItems =
      threadToScenarioStack.computeIfAbsent(currentThreadGroup, k -> new LinkedList<>());
    if (!karateItems.isEmpty()) {
      KarateItem scenario = karateItems.peek();
      text = text.replace("<<UPPERCUT>>", "");
      for (String s : text.splitWithDelimiters("\n", 2)) {
        ServiceMessageBuilder msgScenario;
        if (stdOut) {
          msgScenario = ServiceMessageBuilder.testStdOut(scenario.getName()).addAttribute("out", s);
        } else {
          msgScenario = ServiceMessageBuilder.testStdErr(scenario.getName()).addAttribute("out", s);
        }
        finishMessage(msgScenario, scenario);
      }
      return true;
    }
    if (text.startsWith("<<UPPERCUT>>")) {
      return true;
    }
    return false;
  }

  private boolean featureStartEnd(String text) {
    Matcher matcher =
      Pattern.compile(UPPERCUT_LOG + "\\[([^]]*)].* FeatureFileName: ([^,]*), (.*)$").matcher(text.trim());
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
    } else if (startOrFinish.equals("FINISH")) {
      ServiceMessageBuilder message =
        ServiceMessageBuilder.testSuiteFinished(featureName);
      KarateItem item = idToItem.get(featureNameToId.get(featureName));
      finishMessage(message, item);
      LinkedList<KarateItem> karateItems = threadToScenarioStack.get(threadGroup);
      if (!CollectionUtils.isEmpty(karateItems)) {
        karateItems.pop();
      }
      process(text);
      return true;
    }
    return false;
  }

  private boolean scenarioStartEnd(String text) {
    Matcher matcher =
      Pattern.compile(UPPERCUT_LOG + "\\[([^]]*)].* Scenario name: (.*), featureFileName: (.*), id (\\d+), (.*)")
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
              int index = psiFile.getText().indexOf(scenarioName);
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
      LinkedList<KarateItem> karateItems = threadToScenarioStack.get(threadGroup);
      if (CollectionUtils.isEmpty(karateItems)) {
        karateItems = new LinkedList<>();
        threadToScenarioStack.put(threadGroup, karateItems);
      }
      karateItems.push(item);
      finishMessage(scenarioStarted, item);
      process(text);
    } else if (startOrFinish.equals("FINISH")) {
      ServiceMessageBuilder scenarioFinished = ServiceMessageBuilder.testFinished(scenarioName);
      finishMessage(scenarioFinished, item);
      process(text);
      idToItem.remove(scenarioId);
      LinkedList<KarateItem> karateItems = threadToScenarioStack.get(threadGroup);
      if (!CollectionUtils.isEmpty(karateItems)) {
        karateItems.pop();
      }
    } else {
      ServiceMessageBuilder scenarioFailed =
        ServiceMessageBuilder.testFailed(scenarioName).addAttribute("message", startOrFinish);
      finishMessage(scenarioFailed, item);
      process(text);
      idToItem.remove(scenarioId);
    }
    return true;
  }

  private void setCurrentThread(String text) {
    Matcher matcher =
      Pattern.compile(UPPERCUT_LOG + "\\[([^]]*)] ([\\d:.,]+) (\\w+).*").matcher(text.trim());
    String threadGroup;
    if (matcher.matches()) {
      threadGroup = matcher.group(1);
      this.currentThreadGroup = threadGroup;
    } else {
      trySetCurrentThreadNonUppercut(text);
    }
  }

  private void trySetCurrentThreadNonUppercut(String text) {
    Matcher matcher =
      Pattern.compile("[^\\[]*\\[([^]]*)] .*").matcher(text.trim());
    String threadGroup;
    if (matcher.matches()) {
      threadGroup = matcher.group(1);
      this.currentThreadGroup = threadGroup;
    }
  }

  private boolean processStartFinishText(@NotNull final String statement) {
    Matcher matcher =
      Pattern.compile(".*<<(.*+)>> feature (\\d+) of (\\d+) \\(\\d+ remaining\\) (.*)").matcher(statement.trim());
    if (!matcher.matches()) {
      return false;
    }
    String result = matcher.group(1);
    String feature = matcher.group(4).replace("classpath:", "");
    ServiceMessageBuilder message;
    KarateItem item = idToItem.get(featureNameToId.get(feature));
    if (result.equals("pass")) {
      message = ServiceMessageBuilder.testStdOut(feature);
    } else if (result.equals("fail")) {
      message = ServiceMessageBuilder.testSuiteFinished(feature);
    } else {
      throw new RuntimeException("Unknown result: " + result);
    }
    finishMessage(message, item);
    return true;
  }

  private void finishMessage(@NotNull ServiceMessageBuilder msg, KarateItem item) {
    msg.addAttribute("nodeId", String.valueOf(item.getId()));
    msg.addAttribute("parentNodeId", String.valueOf(item.getParentId()));
    doProcessServiceMessages(msg.toString());
  }
}
