package com.rankweis.uppercut.karate.run;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.ServiceMessageBuilder;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.openapi.util.Key;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor;
import org.jetbrains.annotations.NotNull;

public class KarateOutputToGeneralTestEventsConverter extends OutputToGeneralTestEventsConverter {

  TestConsoleProperties testConsoleProperties;
  private Key myCurrentOutputType;
  private ServiceMessageVisitor myCurrentVisitor;
  private boolean testSuiteStarted = false;

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
    if (text.contains("karate.env is:")) {
      return doProcessServiceMessages(ServiceMessageBuilder.testsStarted().toString());
    } else if (text.contains("HTML report")) {
      return false;
    }
    return process(text);
  }

  private boolean doProcessServiceMessages(@NotNull final String text) throws ParseException {
    if (this.myCurrentOutputType == null || this.myCurrentVisitor == null) {
      return false;
    }
    return super.processServiceMessages(text, this.myCurrentOutputType, this.myCurrentVisitor);
  }

  private boolean process(@NotNull final String statement) throws ParseException {
    try {
      Matcher matcher =
        Pattern.compile(".*<<(.*)>> feature (\\d+) of (\\d+)+ \\(\\d+ remaining\\) (.*)").matcher(statement.trim());
      if(!matcher.matches()) {
        return false;
      }
      String result = matcher.group(1);
      int testCount = Integer.parseInt(matcher.group(2));
      String totalCount = matcher.group(3);
      String feature = matcher.group(4).replace("classpath:", "");
      String[] split = feature.split("/");
      ServiceMessageBuilder testStarted = ServiceMessageBuilder.testStarted(split[split.length - 1]);
      ServiceMessageBuilder message;
      if (!testSuiteStarted) {
        final ServiceMessageBuilder testCountMessage =
          new ServiceMessageBuilder("testCount").addAttribute("count", totalCount);
        if (doProcessServiceMessages(testCountMessage.toString())) {
          testSuiteStarted = true;
        }
      }
      if (result.equals("pass")) {
        message = ServiceMessageBuilder.testFinished(feature).addAttribute("captureStandardOutput", "true");
      } else if (result.equals("fail")) {
        message = ServiceMessageBuilder.testFailed(feature).addAttribute("message", "");
      } else {
        throw new ParseException("Unknown result: " + result, 0);
      }
      return finishMessage(testStarted, testCount, 0) & finishMessage(message, testCount, 0);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean finishMessage(@NotNull ServiceMessageBuilder msg, int testId, int parentId) throws ParseException {
    msg.addAttribute("nodeId", String.valueOf(testId));
    msg.addAttribute("parentNodeId", String.valueOf(parentId));
    return doProcessServiceMessages(msg.toString());
  }

  
}
