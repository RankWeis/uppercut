package com.rankweis.uppercut.testrunner;

import static ch.qos.logback.classic.Level.INFO;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

public class KarateTestRunner {

  final Map<String, List<String>> params = new HashMap<>();

  int doTest() {
    String[] testNames =
      Optional.ofNullable(params.get("testname")).orElse(List.of()).stream().map(s -> "classpath:" + s).toList()
        .toArray(new String[0]);
    String[] workingDirectories =
      Optional.ofNullable(params.get("working-dir")).orElse(List.of()).stream().toList()
        .toArray(new String[0]);
    String[] tags =
      Optional.ofNullable(params.get("tag")).orElse(List.of())
        .stream().filter(s -> !s.isBlank())
        .map(s -> !s.startsWith("@") ? "@" + s : s)
        .toList()
        .toArray(new String[0]);
    String env =
      Optional.ofNullable(params.get("environment")).orElse(List.of())
        .stream().filter(s -> !s.isBlank())
        .findFirst()
        .orElse("DEV");
    int parallelism =
      Optional.ofNullable(params.get("parallelism"))
        .map(l -> l.get(0))
        .map(Integer::parseInt)
        .orElse(1);

    if (tags.length == 0 && testNames.length == 0) {
      testNames = new String[]{"classpath:test-files"};
      tags = new String[]{"@Test"};
    }

    try {
      Class<?> clazz = Class.forName("com.intuit.karate.junit5.Karate",
        true, Thread.currentThread().getContextClassLoader());
      Object invoke = clazz.getDeclaredConstructor().newInstance();
      Method mRun = clazz.getMethod("path", String[].class);
      Method mTags = clazz.getMethod("tags", String[].class);
      Method mWorkingDir = clazz.getMethod("workingDir", File.class);
      Method mKarateEnv = clazz.getMethod("karateEnv", String.class);
      Method mParallel = clazz.getMethod("parallel", int.class);
      if (tags.length > 0) {
        invoke = mRun.invoke(invoke, new Object[]{workingDirectories});
        invoke = mTags.invoke(invoke, new Object[]{tags});
      } else {
        invoke = mRun.invoke(invoke, new Object[]{testNames});
      }
      invoke = mWorkingDir.invoke(invoke, new File(workingDirectories[0]));
      invoke = mKarateEnv.invoke(invoke, env);
      invoke = mParallel.invoke(invoke, parallelism);
      return 0;

    } catch (Exception e) {
      throw new RuntimeException("Must have Karate installed on classpath", e);
    }
  }

  public static void main(String[] args) {
    setLoggingLevel();
    KarateTestRunner runner = new KarateTestRunner();
    runner.parseArgs(args);
    runner.doTest();
  }

  public void parseArgs(String[] args) {
    if (args.length % 2 != 0) {
      throw new RuntimeException("Invalid number of arguments");
    }
    for (int i = 0; i < args.length; i += 2) {
      String key = args[i].toLowerCase();
      key = key.startsWith("--") ? key.substring(2) : key.substring(1);
      if (params.containsKey(key)) {
        params.get(key).add(args[i + 1]);
      } else {
        List<String> list = new ArrayList<>();
        list.add(args[i + 1]);
        params.put(key, list);
      }
    }
  }

  public static void setLoggingLevel() {
    Logger logger = (Logger) LoggerFactory.getLogger(ROOT_LOGGER_NAME);
    logger.setLevel(INFO);
    logger.iteratorForAppenders()
      .forEachRemaining(appender -> {
        if (appender.getClass().getCanonicalName().contains("ConsoleAppender")) {
          logger.detachAppender(appender);
        }
      });
    OutputStreamAppender<ILoggingEvent> outputStreamAppender =
      getOutputStreamAppender();
    logger.addAppender(outputStreamAppender);
  }

  private static @NotNull OutputStreamAppender<ILoggingEvent> getOutputStreamAppender() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    OutputStreamAppender<ILoggingEvent> outputStreamAppender = new OutputStreamAppender<>();
    outputStreamAppender.setContext(context);
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern("%d{HH:mm:ss} %-5level %logger{36} - %msg%n");
    encoder.start();
    outputStreamAppender.setName("KarateAppender");
    outputStreamAppender.setEncoder(encoder);
    outputStreamAppender.setOutputStream(System.out);
    outputStreamAppender.start();
    return outputStreamAppender;
  }

}
