package com.rankweis.uppercut.testrunner;

import static ch.qos.logback.classic.Level.INFO;
import static ch.qos.logback.classic.Level.WARN;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.intuit.karate.Results;
import com.intuit.karate.junit5.Karate;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.LoggerFactory;

public class KarateTestRunner {

  final Map<String, List<String>> params = new HashMap<>();

  Results doTest() {
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
    
    if(tags.length == 0 && testNames.length == 0) {
      testNames = new String[]{"classpath:test-files"};
      tags = new String[]{"@Test"};
    }
    Karate k = tags.length > 0 ? Karate.run(workingDirectories).tags(tags) : Karate.run(testNames);
    return k
      .workingDir(new File(workingDirectories[0]))
      .karateEnv(env)
      .parallel(parallelism);
  }

  public static void main(String[] args) {
    setLoggingLevel(Level.INFO);
    KarateTestRunner runner = new KarateTestRunner();
    runner.parseArgs(args);
    Results parallel = runner.doTest();
    if (parallel.getFeaturesTotal() == 0) {
      throw new RuntimeException("No tests selected");
    }
  }

  public void parseArgs(String[] args) {
    if (args.length % 2 != 0) {
      throw new RuntimeException("Invalid number of arguments");
    }
    for (int i = 0; i < args.length; i += 2) {
      String key = args[i].toLowerCase();
      key = key.startsWith("--") ? key.substring(2) : key.substring(1);
      if(params.containsKey(key)) {
        params.get(key).add(args[i + 1]);
      } else {
        List<String> list = new ArrayList<>();
        list.add(args[i + 1]);
        params.put(key, list);
      }
    }
  }
  public static void setLoggingLevel(Level level) {
    LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
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
    Logger intuitLogger = (Logger) LoggerFactory.getLogger("com.intuit");
    Logger thymeleafLogger = (Logger) LoggerFactory.getLogger("org.thymeleaf");
    Logger apacheLogger = (Logger) LoggerFactory.getLogger("org.apache");
    intuitLogger.setLevel(Level.INFO);
    apacheLogger.setLevel(WARN);
    thymeleafLogger.setLevel(Level.OFF);
    Logger logger = (Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
    logger.setLevel(INFO);
    logger.detachAndStopAllAppenders();
    logger.addAppender(outputStreamAppender);
  }

}
