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

  private static final String USERS = "build/resources/test/sample/users.feature";
  private static final String CALLED = "build/resources/test/sample/called.feature";

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
  public void calledFeaturesGetNoNodesOfTheirOwn() {
    process("FEATURE_ENTER",
      "{\"path\":\"" + USERS + "\",\"name\":\"sample feature\",\"line\":1,\"callDepth\":0,\"tags\":[]}");
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
      "{\"name\":\"sample feature\",\"relativePath\":\"" + USERS + "\",\"callDepth\":0,\"passed\":true}");

    // The tree holds only what the user ran. Nodes for called features - nested under the caller, or
    // at the top level - turned a chain of calls into a staircase or a run of identical siblings (#404).
    assertEquals(4, sink.messages.size());
    // top-level feature: suite under root
    assertContains(sink.messages.get(0), "testSuiteStarted", "name='sample feature'",
      "locationHint='file://resolved/" + USERS + ":1'", "|node=1|parent=0");
    // scenario under its feature
    assertContains(sink.messages.get(1), "testStarted", "name='first scenario'",
      "locationHint='file://resolved/" + USERS + ":3'", "|node=2|parent=1");
    assertContains(sink.messages.get(2), "testFinished", "name='first scenario'", "duration='90'", "|node=2");
    assertContains(sink.messages.get(3), "testSuiteFinished", "name='sample feature'", "|node=1");
  }

  @Test
  public void anonymousScenarioIsNamedByFileAndLine() {
    process("FEATURE_ENTER", "{\"path\":\"" + CALLED + "\",\"name\":null,\"line\":2,\"callDepth\":0}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + CALLED + "\",\"name\":null,\"line\":4,\"refId\":\"[1:4]\",\"callDepth\":0}");

    assertContains(sink.messages.get(0), "testSuiteStarted", "name='called.feature'");
    assertContains(sink.messages.get(1), "testStarted", "name='called.feature:4'", "|node=2|parent=1");
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
  public void passedStepsAreNotListedByDefault() {
    // The run from #404: every passed step was echoed into the run output, which then read like the
    // feature file with its keywords stripped. Passed steps are now left out unless asked for.
    process("FEATURE_ENTER", "{\"path\":\"" + USERS + "\",\"name\":\"f\",\"line\":1,\"callDepth\":0}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + USERS + "\",\"name\":\"create a user\",\"line\":3,\"refId\":\"[1:3]\",\"callDepth\":0}");
    process("STEP_EXIT", "{\"line\":4,\"prefix\":\"*\",\"keyword\":\"url\","
      + "\"text\":\"'https://jsonplaceholder.typicode.com/users'\",\"status\":\"passed\"}");
    process("STEP_EXIT",
      "{\"line\":5,\"prefix\":\"*\",\"keyword\":\"method\",\"text\":\"post\",\"status\":\"passed\"}");
    process("STEP_EXIT", "{\"line\":6,\"prefix\":\"*\",\"keyword\":\"status\",\"text\":\"201\",\"status\":\"passed\"}");

    assertEquals("feature and scenario started, nothing else: " + sink.messages, 2, sink.messages.size());
  }

  @Test
  public void stepsThatDidNotPassAreListedByDefault() {
    process("FEATURE_ENTER", "{\"path\":\"" + USERS + "\",\"name\":\"f\",\"line\":1,\"callDepth\":0}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + USERS + "\",\"name\":\"s\",\"line\":3,\"refId\":\"[1:3]\",\"callDepth\":0}");
    process("STEP_EXIT", "{\"line\":4,\"prefix\":\"*\",\"keyword\":\"def\",\"text\":\"id = 1\",\"status\":\"passed\"}");
    process("STEP_EXIT",
      "{\"line\":5,\"prefix\":\"*\",\"keyword\":\"match\",\"text\":\"id == 2\",\"status\":\"failed\"}");
    process("STEP_EXIT", "{\"line\":6,\"prefix\":\"*\",\"keyword\":\"print\",\"text\":\"id\",\"status\":\"skipped\"}");

    // Where the scenario went wrong stays visible; the error itself arrives on SCENARIO_EXIT.
    assertEquals(4, sink.messages.size());
    assertContains(sink.messages.get(2), "testStdOut", "name='s'", "out='* match id == 2   |[failed|]|n'");
    assertContains(sink.messages.get(3), "testStdOut", "name='s'", "out='* print id   |[skipped|]|n'");
  }

  @Test
  public void printOutputOfPassedStepIsKeptWhenTheStepIsNotListed() {
    // A project with no logging provider has a silent console; this is its only print output.
    process("FEATURE_ENTER", "{\"path\":\"" + USERS + "\",\"name\":\"f\",\"line\":1,\"callDepth\":0}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + USERS + "\",\"name\":\"prints\",\"line\":3,\"refId\":\"[1:3]\",\"callDepth\":0}");
    process("STEP_EXIT", "{\"line\":4,\"prefix\":\"*\",\"keyword\":\"print\",\"text\":\"'env is', env\","
      + "\"status\":\"passed\",\"stepLog\":\"env is dev\\nsecond line\\n\"}");

    assertContains(sink.messages.get(2), "testStdOut", "name='prints'", "out='env is dev|nsecond line|n'");
  }

  @Test
  public void failureInsideCallIsListedBeneathTheCallStepByDefault() {
    process("FEATURE_ENTER", "{\"path\":\"" + USERS + "\",\"name\":\"caller\",\"line\":1,\"callDepth\":0}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + USERS + "\",\"name\":\"calling\",\"line\":3,\"refId\":\"[1:3]\",\"callDepth\":0}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + CALLED + "\",\"name\":null,\"line\":4,\"refId\":\"[1:4]\",\"callDepth\":1}");
    process("STEP_EXIT", "{\"line\":5,\"prefix\":\"*\",\"keyword\":\"def\",\"text\":\"a = 1\",\"status\":\"passed\"}");
    process("STEP_EXIT",
      "{\"line\":6,\"prefix\":\"*\",\"keyword\":\"match\",\"text\":\"a == 2\",\"status\":\"failed\"}");
    process("SCENARIO_EXIT",
      "{\"feature\":\"" + CALLED + "\",\"name\":null,\"line\":4,\"refId\":\"[1:4]\",\"callDepth\":1,\"passed\":false}");
    process("STEP_EXIT", "{\"line\":4,\"prefix\":\"*\",\"keyword\":\"call\",\"text\":\"read('called.feature')\","
      + "\"status\":\"failed\"}");

    // The failed call step, then the failed step inside it; the callee's passed step is left out.
    assertContains(sink.messages.get(sink.messages.size() - 1), "testStdOut", "name='calling'",
      "out='* call read(|'called.feature|')   |[failed|]|n  * match a == 2   |[failed|]|n'");
  }

  @Test
  public void stepsAreReportedAsOutputOnTheirScenario() {
    showPassedSteps();
    process("FEATURE_ENTER",
      "{\"path\":\"" + USERS + "\",\"name\":\"sample feature\",\"line\":1,\"callDepth\":0}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + USERS + "\",\"name\":\"first scenario\",\"line\":3,\"refId\":\"[1:3]\",\"callDepth\":0}");
    process("STEP_EXIT", "{\"line\":4,\"prefix\":\"*\",\"keyword\":\"def\",\"text\":\"id = 1\","
      + "\"status\":\"passed\",\"durationMillis\":12.3}");
    process("STEP_EXIT", "{\"line\":5,\"prefix\":\"*\",\"keyword\":\"match\",\"text\":\"id == 2\","
      + "\"status\":\"failed\",\"durationMillis\":1.0}");

    // Steps attach to the scenario that ran them, so the tree shows what a run did - not only
    // whether it passed. The v1 path gets this from its log lines; v2 reports structurally.
    assertContains(sink.messages.get(2), "testStdOut", "name='first scenario'", "out='* def id = 1|n'", "|node=2");
    assertContains(sink.messages.get(3), "testStdOut", "name='first scenario'", "* match id == 2", "failed");
  }

  @Test
  public void theKeywordKarateSendsApartIsWrittenBeforeTheText() {
    // Karate 2's StepRunEvent sends `* def user =` as text "user =" and `* method post` as "post";
    // the runner adds the keyword. Without it #404's log read `* user =` and `* post`.
    showPassedSteps();
    process("FEATURE_ENTER", "{\"path\":\"" + USERS + "\",\"name\":\"f\",\"line\":1,\"callDepth\":0}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + USERS + "\",\"name\":\"s\",\"line\":3,\"refId\":\"[1:3]\",\"callDepth\":0}");
    process("STEP_EXIT", "{\"line\":4,\"prefix\":\"*\",\"keyword\":\"def\",\"text\":\"user =\",\"status\":\"passed\"}");
    process("STEP_EXIT",
      "{\"line\":5,\"prefix\":\"*\",\"keyword\":\"method\",\"text\":\"post\",\"status\":\"passed\"}");
    // A bare expression, and Given/When prose, have no keyword
    process("STEP_EXIT", "{\"line\":6,\"prefix\":\"*\",\"text\":\"karate.log('x')\",\"status\":\"passed\"}");
    process("STEP_EXIT", "{\"line\":7,\"prefix\":\"Given\",\"text\":\"a user\",\"status\":\"passed\"}");

    assertContains(sink.messages.get(2), "out='* def user =|n'");
    assertContains(sink.messages.get(3), "out='* method post|n'");
    assertContains(sink.messages.get(4), "out='* karate.log(|'x|')|n'");
    assertContains(sink.messages.get(5), "out='Given a user|n'");
  }

  @Test
  public void stepOutputIsShownBeneathItsStep() {
    showPassedSteps();
    process("FEATURE_ENTER", "{\"path\":\"" + USERS + "\",\"name\":\"f\",\"line\":1,\"callDepth\":0}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + USERS + "\",\"name\":\"prints\",\"line\":3,\"refId\":\"[1:3]\",\"callDepth\":0}");
    // What print/karate.log produced: v2 keeps it on StepResult.getLog(), which the runner adds to
    // the STEP_EXIT payload because the aggregate carrying it arrives after the nodes are closed.
    process("STEP_EXIT",
      "{\"line\":4,\"prefix\":\"*\",\"keyword\":\"print\",\"text\":\"'env is', env\",\"status\":\"passed\","
        + "\"stepLog\":\"env is dev\\nsecond line\\n\"}");

    // Service messages escape quotes and newlines (' -> |', \n -> |n), so assert on the unescaped
    // fragments: the step text, then each log line indented beneath it.
    assertContains(sink.messages.get(2), "testStdOut", "name='prints'",
      "* print ", " env", "    env is dev", "    second line");
  }

  @Test
  public void stepsOfCalledFeatureGoToTheCallingScenarioIndentedByCallDepth() {
    showPassedSteps();
    process("FEATURE_ENTER", "{\"path\":\"" + USERS + "\",\"name\":\"caller\",\"line\":1,\"callDepth\":0}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + USERS + "\",\"name\":\"calling scenario\",\"line\":3,\"refId\":\"[1:3]\",\"callDepth\":0}");
    process("FEATURE_ENTER", "{\"path\":\"" + CALLED + "\",\"name\":\"callee\",\"line\":2,\"callDepth\":1}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + CALLED + "\",\"name\":null,\"line\":4,\"refId\":\"[1:4]\",\"callDepth\":1}");
    process("STEP_EXIT",
      "{\"line\":5,\"prefix\":\"*\",\"keyword\":\"def\",\"text\":\"greeting = 'hi'\",\"status\":\"passed\","
        + "\"stepLog\":\"from the callee\\n\"}");
    process("SCENARIO_EXIT",
      "{\"feature\":\"" + CALLED + "\",\"name\":null,\"line\":4,\"refId\":\"[1:4]\",\"callDepth\":1,\"passed\":true}");
    process("FEATURE_EXIT", "{\"name\":\"callee\",\"relativePath\":\"" + CALLED + "\",\"callDepth\":1}");
    process("STEP_EXIT",
      "{\"line\":4,\"prefix\":\"*\",\"keyword\":\"call\",\"text\":\"read('called.feature')\",\"status\":\"passed\"}");

    // No nodes for the callee: its step lands on the calling scenario, beneath the call step that
    // ran it although it finished first, indented one level with its own output beneath it.
    assertEquals(3, sink.messages.size());
    String out = sink.messages.get(2);
    assertContains(out, "testStdOut", "name='calling scenario'", "|node=2",
      "out='* call read", "  * def greeting", "      from the callee");
    assertTrue(out, out.indexOf("call read") < out.indexOf("def greeting"));
  }

  @Test
  public void nestedCallsListEachLevelBeneathItsCallStep() {
    showPassedSteps();
    process("FEATURE_ENTER", "{\"path\":\"" + USERS + "\",\"name\":\"a\",\"line\":1,\"callDepth\":0}");
    process("SCENARIO_ENTER",
      "{\"feature\":\"" + USERS + "\",\"name\":\"top\",\"line\":3,\"refId\":\"[1:3]\",\"callDepth\":0}");
    process("SCENARIO_ENTER", "{\"feature\":\"b.feature\",\"name\":\"b\",\"line\":2,\"callDepth\":1}");
    process("SCENARIO_ENTER", "{\"feature\":\"c.feature\",\"name\":\"c\",\"line\":2,\"callDepth\":2}");
    process("STEP_EXIT", "{\"line\":3,\"prefix\":\"*\",\"text\":\"c step\",\"status\":\"passed\"}");
    process("SCENARIO_EXIT", "{\"feature\":\"c.feature\",\"name\":\"c\",\"line\":2,\"callDepth\":2}");
    process("STEP_EXIT", "{\"line\":3,\"prefix\":\"*\",\"text\":\"b calls c\",\"status\":\"passed\"}");
    process("STEP_EXIT", "{\"line\":4,\"prefix\":\"*\",\"text\":\"b after\",\"status\":\"passed\"}");
    process("SCENARIO_EXIT", "{\"feature\":\"b.feature\",\"name\":\"b\",\"line\":2,\"callDepth\":1}");
    process("STEP_EXIT", "{\"line\":4,\"prefix\":\"*\",\"text\":\"top calls b\",\"status\":\"passed\"}");

    // Read top to bottom, the output is the call tree: each call step, then what it ran.
    String out = sink.messages.get(sink.messages.size() - 1);
    assertContains(out, "out='* top calls b|n  * b calls c|n    * c step|n  * b after|n'");
  }

  @Test
  public void secondFeatureWithTheSameTitleIsToldApartByItsFile() {
    process("FEATURE_ENTER", "{\"path\":\"x/A1.feature\",\"name\":\"Prints stuff\",\"line\":1,\"callDepth\":0}");
    process("FEATURE_ENTER", "{\"path\":\"x/A1a.feature\",\"name\":\"Prints stuff\",\"line\":1,\"callDepth\":0}");
    process("FEATURE_ENTER", "{\"path\":\"x/Other.feature\",\"name\":\"Other\",\"line\":1,\"callDepth\":0}");

    assertContains(sink.messages.get(0), "name='Prints stuff'");
    assertContains(sink.messages.get(1), "name='Prints stuff (A1a.feature)'");
    assertContains(sink.messages.get(2), "name='Other'");
  }

  @Test
  public void outlineExamplesAreNumberedOnlyWhenTheirNamesWouldRepeat() {
    String outline = "build/resources/test/sample/outline.feature";
    process("FEATURE_ENTER", "{\"path\":\"" + outline + "\",\"name\":\"o\",\"line\":1,\"callDepth\":0}");
    // Placeholders in the title already make each example's name distinct.
    process("SCENARIO_ENTER", "{\"feature\":\"" + outline + "\",\"name\":\"greeting alice\",\"line\":7,"
      + "\"refId\":\"[1.1:7]\",\"callDepth\":0,\"isOutlineExample\":true,\"exampleIndex\":0,"
      + "\"outlineSlug\":\"" + outline + ":greeting <name>\"}");
    // Without placeholders every example has the outline's title: number them by example row.
    process("SCENARIO_ENTER", "{\"feature\":\"" + outline + "\",\"name\":\"Retrieve user by ID\",\"line\":12,"
      + "\"refId\":\"[2.2:12]\",\"callDepth\":0,\"isOutlineExample\":true,\"exampleIndex\":1,"
      + "\"outlineSlug\":\"" + outline + ":Retrieve user by ID\"}");

    assertContains(sink.messages.get(1), "name='greeting alice'");
    assertContains(sink.messages.get(2), "name='Retrieve user by ID |[2|]'");
  }

  @Test
  public void interleavedParallelScenariosKeepTheirOwnSteps() {
    showPassedSteps();
    // Karate runs scenarios on several threads and their events interleave on one stdout stream.
    // The runner stamps each event with its thread; without keying on it, a step lands on whichever
    // scenario opened most recently - which under parallelism is usually the wrong one.
    process("FEATURE_ENTER",
      "{\"path\":\"" + USERS + "\",\"name\":\"f\",\"line\":1,\"callDepth\":0,\"thread\":\"t1\"}");
    process("SCENARIO_ENTER", "{\"feature\":\"" + USERS + "\",\"name\":\"alpha\",\"line\":3,"
      + "\"refId\":\"[1:3]\",\"callDepth\":0,\"thread\":\"t1\"}");
    process("SCENARIO_ENTER", "{\"feature\":\"" + USERS + "\",\"name\":\"beta\",\"line\":9,"
      + "\"refId\":\"[2:9]\",\"callDepth\":0,\"thread\":\"t2\"}");
    // beta opened last; a step from t1 still belongs to alpha
    process("STEP_EXIT",
      "{\"line\":4,\"prefix\":\"*\",\"text\":\"alpha step\",\"status\":\"passed\",\"thread\":\"t1\"}");
    process("STEP_EXIT",
      "{\"line\":10,\"prefix\":\"*\",\"text\":\"beta step\",\"status\":\"passed\",\"thread\":\"t2\"}");

    assertContains(sink.messages.get(3), "testStdOut", "name='alpha'", "alpha step");
    assertContains(sink.messages.get(4), "testStdOut", "name='beta'", "beta step");
  }

  @Test
  public void parallelCallsOfTheSameFeatureKeepStepsWithTheirCaller() {
    showPassedSteps();
    // Two top-level scenarios on different threads call the same feature at the same depth. Each
    // thread's steps, called or not, must land on that thread's scenario, at that thread's depth.
    process("FEATURE_ENTER",
      "{\"path\":\"" + USERS + "\",\"name\":\"f\",\"line\":1,\"callDepth\":0,\"thread\":\"t1\"}");
    process("SCENARIO_ENTER", "{\"feature\":\"" + USERS + "\",\"name\":\"alpha\",\"line\":3,"
      + "\"refId\":\"[1:3]\",\"callDepth\":0,\"thread\":\"t1\"}");
    process("SCENARIO_ENTER", "{\"feature\":\"" + USERS + "\",\"name\":\"beta\",\"line\":9,"
      + "\"refId\":\"[2:9]\",\"callDepth\":0,\"thread\":\"t2\"}");
    // both call login.feature concurrently: same path, refId and depth
    process("FEATURE_ENTER",
      "{\"path\":\"" + CALLED + "\",\"name\":\"login\",\"line\":1,\"callDepth\":1,\"thread\":\"t1\"}");
    process("SCENARIO_ENTER", "{\"feature\":\"" + CALLED + "\",\"name\":\"do login\",\"line\":4,"
      + "\"refId\":\"[1:4]\",\"callDepth\":1,\"thread\":\"t1\"}");
    process("FEATURE_ENTER",
      "{\"path\":\"" + CALLED + "\",\"name\":\"login\",\"line\":1,\"callDepth\":1,\"thread\":\"t2\"}");
    process("SCENARIO_ENTER", "{\"feature\":\"" + CALLED + "\",\"name\":\"do login\",\"line\":4,"
      + "\"refId\":\"[1:4]\",\"callDepth\":1,\"thread\":\"t2\"}");
    // ids are allocated to nodes only: f=1, alpha=2, beta=3; the calls get none
    assertEquals(3, sink.messages.size());
    // t1's call finishes first; a step from t1 after it is alpha's own, unindented
    process("SCENARIO_EXIT", "{\"feature\":\"" + CALLED + "\",\"name\":\"do login\",\"line\":4,"
      + "\"refId\":\"[1:4]\",\"callDepth\":1,\"passed\":true,\"thread\":\"t1\"}");
    process("STEP_EXIT",
      "{\"line\":5,\"prefix\":\"*\",\"text\":\"after call\",\"status\":\"passed\",\"thread\":\"t1\"}");
    assertContains(sink.messages.get(sink.messages.size() - 1), "testStdOut", "name='alpha'", "|node=2",
      "out='* after call");
    assertEquals(4, sink.messages.size());
    // t2 is still inside its call: its step waits for t2's call step, and closing the call does not
    // close beta
    process("STEP_EXIT",
      "{\"line\":5,\"prefix\":\"*\",\"text\":\"login step\",\"status\":\"passed\",\"thread\":\"t2\"}");
    process("SCENARIO_EXIT", "{\"feature\":\"" + CALLED + "\",\"name\":\"do login\",\"line\":4,"
      + "\"refId\":\"[1:4]\",\"callDepth\":1,\"passed\":true,\"thread\":\"t2\"}");
    assertEquals(4, sink.messages.size());
    process("STEP_EXIT",
      "{\"line\":10,\"prefix\":\"*\",\"text\":\"beta calls login\",\"status\":\"passed\",\"thread\":\"t2\"}");
    assertContains(sink.messages.get(4), "testStdOut", "name='beta'", "|node=3",
      "out='* beta calls login|n  * login step");
  }

  @Test
  public void unexpectedFieldShapeCostsOneEventNotTheRun() {
    // Payload shapes are Karate's; if a newer 2.x turns 'error' into an object, the scenario still closes
    process("FEATURE_ENTER", "{\"path\":\"" + USERS + "\",\"name\":\"f\",\"line\":1,\"callDepth\":0}");
    process("SCENARIO_ENTER", "{\"feature\":\"" + USERS + "\",\"name\":\"alpha\",\"line\":3,"
      + "\"refId\":\"[1:3]\",\"callDepth\":0}");
    process("SCENARIO_EXIT", "{\"feature\":\"" + USERS + "\",\"name\":\"alpha\",\"line\":3,"
      + "\"refId\":\"[1:3]\",\"callDepth\":0,\"passed\":false,\"error\":{\"message\":\"boom\"}}");
    assertContains(sink.messages.get(sink.messages.size() - 1), "testFailed", "name='alpha'", "boom");
    // and a line that is a boolean where an int is expected is a no-op, not an exception
    assertTrue(processor.process(
      "<<UPPERCUT-V2>> FEATURE_ENTER {\"path\":\"x.feature\",\"line\":true,\"callDepth\":\"zero\"}"));
  }

  @Test
  public void unknownEventTypesAreConsumedNotPrinted() {
    // A false return would have the SM framework print the raw protocol line into the console;
    // anything carrying the prefix is ours, known type or not.
    assertTrue(processor.process("<<UPPERCUT-V2>> SOMETHING_NEW {\"a\":1}"));
    assertTrue(processor.process("<<UPPERCUT-V2>> EMIT_ERROR {\"message\":\"reflection failed\"}"));
    assertTrue(sink.messages.isEmpty());
  }

  @Test
  public void malformedPayloadsAreConsumedNotPrinted() {
    assertTrue(processor.process("<<UPPERCUT-V2>> SCENARIO_ENTER not-json-at-all"));
    assertTrue(processor.process("<<UPPERCUT-V2>> FEATURE_ENTER [1,2,3]"));
    assertTrue(sink.messages.isEmpty());
  }

  /** The "list passed steps" setting turned on, for tests of how the full listing is laid out. */
  private void showPassedSteps() {
    processor = new KarateV2EventProcessor(sink, true);
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
