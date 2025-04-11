package com.rankweis.uppercut.karate.run;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import org.jetbrains.annotations.NotNull;

public class KarateTestConsoleConfiguration extends SMTRunnerConsoleProperties implements SMCustomMessagesParsing {


  public KarateTestConsoleConfiguration(@NotNull RunConfiguration config,
    @NotNull String testFrameworkName, @NotNull Executor executor) {
    super(config, testFrameworkName, executor);
    setUsePredefinedMessageFilter(true);
    setIdBasedTestTree(true);
  }


  @Override public OutputToGeneralTestEventsConverter createTestEventsConverter(@NotNull String testFrameworkName,
    @NotNull TestConsoleProperties consoleProperties) {
    return new KarateOutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties);
  }


}
