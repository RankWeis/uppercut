package com.rankweis.uppercut.testrunner;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.LoggerFactory;

public class KarateTestRunner {

  final Map<String, List<String>> params = new HashMap<>();
  final Map<Object, Integer> scenarioIdMap = new ConcurrentHashMap<>();
  final private Random random = new Random();

  void doTest() throws Exception {
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
    Optional<String> env =
      Optional.ofNullable(params.get("environment")).orElse(List.of())
        .stream().filter(s -> !s.isBlank())
        .findFirst();
    int parallelism =
      Optional.ofNullable(params.get("parallelism"))
        .map(l -> l.get(0))
        .map(Integer::parseInt)
        .orElse(1);

    if (tags.length == 0 && testNames.length == 0) {
      testNames = new String[]{"classpath:test-files"};
      tags = new String[]{"@Test"};
    }

      Object hook = createRuntimeHook();
      Class<?> clazz = Class.forName("com.intuit.karate.junit5.Karate",
        true, Thread.currentThread().getContextClassLoader());
      Object invoke = clazz.getDeclaredConstructor().newInstance();
      Method mSetHook = clazz.getMethod("hook", Class.forName("com.intuit.karate.RuntimeHook"));
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
      if (env.isPresent()) {
        mKarateEnv.invoke(invoke, env.get());
      }
      invoke = mSetHook.invoke(invoke, hook);
      mParallel.invoke(invoke, parallelism);
  }

  Object createRuntimeHook() {
      Logger myLogger = (Logger) LoggerFactory.getLogger(KarateTestRunner.class);
      // Load the RuntimeHook class using reflection
    Class<?> runtimeHookClass;
    try {
      runtimeHookClass =
        Class.forName("com.intuit.karate.RuntimeHook", true, Thread.currentThread().getContextClassLoader());
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Must have karate on the classpath", e);
    }

    // Create a proxy instance of RuntimeHook
    // Access 'parentRuntime' field from caller
    return Proxy.newProxyInstance(
      Thread.currentThread().getContextClassLoader(),
      new Class<?>[]{runtimeHookClass},
      (proxy, method, args) -> {
        if ("beforeScenario".equals(method.getName())
          || "afterScenario".equals(method.getName()) && args.length == 1) {
          Class<?> scenarioRuntimeClass = Class.forName("com.intuit.karate.core.ScenarioRuntime", true,
            Thread.currentThread().getContextClassLoader());
          Object scenarioRuntime = args[0];
          Integer scenarioId =
            scenarioIdMap.computeIfAbsent(scenarioRuntime, (o -> random.nextInt(0, Integer.MAX_VALUE)));
          Method getScenarioInfoMethod = scenarioRuntimeClass.getMethod("getScenarioInfo");
          Map<?, ?> scenarioInfo = (Map<?, ?>) getScenarioInfoMethod.invoke(scenarioRuntime);
          Class<?> loggerClass = Class.forName("com.intuit.karate.Logger", true,
            Thread.currentThread().getContextClassLoader());
          Field logger = scenarioRuntimeClass.getField("logger");
          Field featureRuntime = scenarioRuntimeClass.getField("featureRuntime");
          Object loggerInstance = logger.get(scenarioRuntime);
          Object featureRuntimeInstance = featureRuntime.get(scenarioRuntime);
          String featureName = featureRuntimeInstance.toString().replace("classpath:", "");
          Method loggerInfoMethod = loggerClass.getMethod("info", String.class, Object[].class);
          String startOrFinish;
          if (scenarioInfo.get("errorMessage") != null) {
            startOrFinish = scenarioInfo.get("errorMessage").toString();
          } else if ("afterScenario".equals(method.getName())) {
            startOrFinish = "FINISH";
          } else {
            startOrFinish = "START";
          }
          loggerInfoMethod.invoke(loggerInstance,
            "Scenario name: {}, featureFileName: {}, id {}, {}", new Object[]{scenarioInfo.get("scenarioName"),
              featureName, scenarioId, startOrFinish});

          return true;
        }
        if (("afterFeature".equals(method.getName()) || "beforeFeature".equals(method.getName()))
          && args.length == 1) {
          String startOrFinish;
          if ("afterFeature".equals(method.getName())) {
            startOrFinish = "FINISH";
          } else {
            startOrFinish = "START";
          }
          Class<?> featureRuntimeClass = Class.forName("com.intuit.karate.core.FeatureRuntime", true,
            Thread.currentThread().getContextClassLoader());
          Object featureRuntime = args[0];

          // Access 'parentRuntime' field from caller
          Field resultField = featureRuntimeClass.getDeclaredField("result");
          Object resultInstance = resultField.get(featureRuntime);
          Method displayField = resultInstance.getClass().getMethod("getDisplayName");
          String featureName = (String) displayField.invoke(resultInstance);
          myLogger.info("FeatureFileName: {}, {}", new Object[]{featureName, startOrFinish});
        }
        if ("toString".equals(method.getName())) {
          return "Proxy for Interface";
        }
        return true;
      });
  }

  public static void main(String[] args) throws Exception {
    getOutputStreamAppender();
    KarateTestRunner runner = new KarateTestRunner();
    runner.parseArgs(args);
    try {
      runner.doTest();
    } catch(ClassNotFoundException e) {
      throw new RuntimeException("Must have karate on the classpath", e);
    }
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

  private static void getOutputStreamAppender() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = context.getLogger(KarateTestRunner.class);
    Logger intuitLogger = context.getLogger("com.intuit");

    OutputStreamAppender<ILoggingEvent> outputStreamAppender = new OutputStreamAppender<>();
    outputStreamAppender.setContext(context);
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern("<<UPPERCUT>>[%thread] %d{HH:mm:ss} %-5level %logger{36} - %msg%n");
    encoder.start();
    outputStreamAppender.setName("KarateAppender");
    outputStreamAppender.setEncoder(encoder);
    outputStreamAppender.setOutputStream(System.out);
    outputStreamAppender.start();
    logger.addAppender(outputStreamAppender);
    intuitLogger.addAppender(outputStreamAppender);
  }

}
