package com.rankweis.uppercut.testrunner;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Installs the {@code <<UPPERCUT>>}-prefixed console appender the IDE console parses.
 *
 * <p>Every logback reference lives in this class, and nothing on the main path references it
 * directly - {@link KarateTestRunner} only reaches it reflectively. Karate 1.x pulled logback in
 * transitively, but Karate 2.x depends on slf4j alone, so on a v2 project these classes are absent
 * and merely linking against them kills the runner JVM before a single test event is emitted.
 */
final class UppercutLogbackAppender {

  private UppercutLogbackAppender() {
  }

  @SuppressWarnings("unchecked")
  static void install() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

    ConsoleAppender<ILoggingEvent> outputStreamAppender = new ConsoleAppender<>();
    outputStreamAppender.setContext(context);
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern("<<UPPERCUT>>[%thread] %d{HH:mm:ss} %-5level %logger{36} - %msg%n");
    encoder.start();
    outputStreamAppender.setName("KarateAppender");
    outputStreamAppender.setEncoder(encoder);
    outputStreamAppender.start();
    Logger intuitLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
    List<ConsoleAppender<ILoggingEvent>> consoleAppenders = new ArrayList<>();
    intuitLogger.iteratorForAppenders().forEachRemaining(appender -> {
      if (appender instanceof ConsoleAppender) {
        consoleAppenders.add((ConsoleAppender<ILoggingEvent>) appender);
      }
    });
    consoleAppenders.forEach(intuitLogger::detachAppender);
    intuitLogger.addAppender(outputStreamAppender);
  }
}
