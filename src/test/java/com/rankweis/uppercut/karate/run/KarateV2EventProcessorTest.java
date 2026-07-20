package com.rankweis.uppercut.karate.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.intellij.execution.testframework.sm.ServiceMessageBuilder;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

/**
 * Event payload shapes below are taken from a live run of karate-core 2.1.1 (see docs/KARATE2.md),
 * encoded the way KarateV2TestRunner emits them.
 */
public class KarateV2EventProcessorTest {

  private static final String USERS = "build/resources/test/spike/users.feature";
  private static final String CALLED = "build/resources/test/spike/called.feature";

  private RecordingSink sink;
  private KarateV2EventProcessor processor;

  static class RecordingSink implements KarateV2EventProcessor.EventSink {

    final List<String> messages = new ArrayList<>();
    boolean testsStarted = false;

    @Override public void testsStarted() {
      testsStarted = true;
    }

    @Override public void emit(@NotNull ServiceMessageBuilder message, int nodeId, int parentNodeId) {
      messages.add(message.toString() + "|node=" + nodeId + "|parent=" + parentNodeId);
    }

    @Override public String resolveLocation(@NotNull String featurePath, int line) {
      return "file://resolved/" + featurePath + ":" + line;
    }
  }

  @Before
  public void setUp() {
    sink = new RecordingSink();
    processor = new KarateV2EventProcessor(sink);
  }

  @Test
  public void ignoresNonEventLines() {
    assertFalse(processor.process("some random output"));
    assertFalse(processor.process("<<UPPERCUT>>[main] 01:02:03 INFO foo - bar"));
    assertTrue(sink.messages.isEmpty());
  }

  @Test
  public void suiteEnterStartsTests() {
    assertTrue(processor.process("<<UPPERCUT-V2>> SUITE_ENTER {\"env\":null,\"threads\":1}"));
    assertTrue(sink.testsStarted);
  }

  @Test
  public void featureAndScenarioLifecycleWithCalledFeatureNesting() {
    process("FEATURE_ENTER",
      "{\"path\":\"" + USERS + "\",\"name\":\"spike feature\",\"line\":1,\"callDepth\":0,\"tags\":[]}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + USERS + "\",\"name\":\"first scenario\",\"line\":3,\"refId\":\"[1:3]\","
        + "\"callDepth\":0,\"tags\":[]}");
    // called feature arrives between the calling step's events with callDepth=1
    process("FEATURE_ENTER",
      "{\"path\":\"" + CALLED + "\",\"name\":\"called feature\",\"line\":2,\"callDepth\":1,\"tags\":[\"ignore\"]}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + CALLED + "\",\"name\":null,\"line\":4,\"refId\":\"[1:4]\",\"callDepth\":1}");
    process("SCENARIO_EXIT",
      "{\"feature\":\"" + CALLED + "\",\"name\":null,\"line\":4,\"refId\":\"[1:4]\",\"callDepth\":1,"
        + "\"passed\":true,\"skipped\":false,\"durationMillis\":1}");
    process("FEATURE_EXIT",
      "{\"name\":\"called feature\",\"relativePath\":\"" + CALLED + "\",\"callDepth\":1,\"passed\":true}");
    process("SCENARIO_EXIT",
      "{\"feature\":\"" + USERS + "\",\"name\":\"first scenario\",\"line\":3,\"refId\":\"[1:3]\","
        + "\"callDepth\":0,\"passed\":true,\"skipped\":false,\"durationMillis\":90}");
    process("FEATURE_EXIT",
      "{\"name\":\"spike feature\",\"relativePath\":\"" + USERS + "\",\"callDepth\":0,\"passed\":true}");

    assertEquals(8, sink.messages.size());
    // top-level feature: suite under root
    assertContains(sink.messages.get(0), "testSuiteStarted", "name='spike feature'",
      "locationHint='file://resolved/" + USERS + ":1'", "|node=1|parent=0");
    // scenario under its feature
    assertContains(sink.messages.get(1), "testStarted", "name='first scenario'",
      "locationHint='file://resolved/" + USERS + ":3'", "|node=2|parent=1");
    // called feature nests under the calling scenario
    assertContains(sink.messages.get(2), "testSuiteStarted", "name='called feature'", "|node=3|parent=2");
    // anonymous scenario gets basename:line as display name, nests under called feature. Called
    // scenarios are suites, not tests, so they stay visible without inflating the run's test count.
    assertContains(sink.messages.get(3), "testSuiteStarted", "name='called.feature:4'", "|node=4|parent=3");
    assertContains(sink.messages.get(4), "testSuiteFinished", "name='called.feature:4'", "|node=4");
    assertContains(sink.messages.get(5), "testSuiteFinished", "name='called feature'", "|node=3");
    assertContains(sink.messages.get(6), "testFinished", "name='first scenario'", "duration='90'", "|node=2");
    assertContains(sink.messages.get(7), "testSuiteFinished", "name='spike feature'", "|node=1");
  }

  @Test
  public void failedScenarioEmitsTestFailedWithErrorMessage() {
    process("FEATURE_ENTER", "{\"path\":\"" + USERS + "\",\"name\":\"f\",\"line\":1,\"callDepth\":0}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + USERS + "\",\"name\":\"failing match\",\"line\":5,\"refId\":\"[1:5]\",\"callDepth\":0}");
    process("SCENARIO_EXIT",
      "{\"feature\":\"" + USERS + "\",\"name\":\"failing match\",\"line\":5,\"refId\":\"[1:5]\",\"callDepth\":0,"
        + "\"passed\":false,\"skipped\":false,\"durationMillis\":64,\"error\":\"match failed: EQUALS\"}");

    assertContains(sink.messages.get(2), "testFailed", "name='failing match'", "match failed: EQUALS", "|node=2");
  }

  @Test
  public void errorAndSuiteExitEventsAreConsumedSilently() {
    assertTrue(processor.process(
      "<<UPPERCUT-V2>> ERROR {\"feature\":\"" + USERS + "\",\"scenario\":\"x\",\"message\":\"boom\"}"));
    assertTrue(processor.process("<<UPPERCUT-V2>> SUITE_EXIT {\"features\":[]}"));
    assertTrue(sink.messages.isEmpty());
  }

  @Test
  public void unknownEventTypesAreNotConsumed() {
    assertFalse(processor.process("<<UPPERCUT-V2>> SOMETHING_NEW {\"a\":1}"));
  }

  private void process(String type, String json) {
    assertTrue(processor.process("<<UPPERCUT-V2>> " + type + " " + json + "\n"));
  }

  private static void assertContains(String actual, String... expectedParts) {
    for (String part : expectedParts) {
      assertTrue("expected [" + part + "] in [" + actual + "]", actual.contains(part));
    }
  }
}
