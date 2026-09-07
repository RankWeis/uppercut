package com.rankweis.uppercut.testrunner.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The adapter reaches Karate 1 entirely by reflection, so a stand-in engine with the same method
 * names exercises it without Karate on the test classpath.
 */
class KarateV1DebugAdapterTest {

  /** Stands in for {@code com.intuit.karate.core.Variable}. */
  public static final class FakeVariable {

    private final Object value;

    FakeVariable(Object value) {
      this.value = value;
    }

    public Object getValue() {
      return value;
    }
  }

  /** Stands in for {@code com.intuit.karate.core.ScenarioEngine}. */
  public static final class FakeEngine {

    public final Map<String, FakeVariable> vars = new LinkedHashMap<>();
    private Throwable failedReason;

    public Throwable getFailedReason() {
      return failedReason;
    }

    public void setFailedReason(Throwable failedReason) {
      this.failedReason = failedReason;
    }

    public Object evalKarateExpression(String expression) {
      if ("boom".equals(expression)) {
        // What Karate 1 does: records the failure on the engine as well as throwing.
        failedReason = new IllegalStateException("js failed: " + expression);
        throw (IllegalStateException) failedReason;
      }
      return new FakeVariable("evaluated " + expression);
    }
  }

  @Test
  void unwrapsVariablesSoOneRendererServesBothMajors() {
    FakeEngine engine = new FakeEngine();
    engine.vars.put("id", new FakeVariable("sample-id"));
    engine.vars.put("num", new FakeVariable(5));

    Map<String, Object> variables = new KarateV1DebugAdapter.ScenarioEngineFrame(engine).variables();
    assertEquals("sample-id", variables.get("id"));
    assertEquals(5, variables.get("num"));
  }

  @Test
  void aFailedEvaluationDoesNotFailTheScenario() {
    // Karate 1 records an evaluation failure on the engine, which used to turn a typo in the
    // Evaluate window into "passed: 1 | failed: 1" for the run.
    FakeEngine engine = new FakeEngine();
    KarateV1DebugAdapter.ScenarioEngineFrame frame = new KarateV1DebugAdapter.ScenarioEngineFrame(engine);

    assertThrows(IllegalStateException.class, () -> frame.evaluate("boom"));
    assertNull(engine.getFailedReason(), "the engine must be left as it was found");
  }

  @Test
  void anEvaluationRestoresAFailureTheScenarioAlreadyHad() {
    FakeEngine engine = new FakeEngine();
    Throwable original = new IllegalStateException("the step that really failed");
    engine.setFailedReason(original);
    KarateV1DebugAdapter.ScenarioEngineFrame frame = new KarateV1DebugAdapter.ScenarioEngineFrame(engine);

    assertThrows(IllegalStateException.class, () -> frame.evaluate("boom"));
    assertSame(original, engine.getFailedReason());
  }

  @Test
  void evaluationUnwrapsItsResult() throws Exception {
    FakeEngine engine = new FakeEngine();
    assertEquals("evaluated id",
      new KarateV1DebugAdapter.ScenarioEngineFrame(engine).evaluate("id"));
  }

  @Test
  void aScenarioWithNoEngineIsAnErrorRatherThanACrash() {
    KarateV1DebugAdapter.ScenarioEngineFrame frame = new KarateV1DebugAdapter.ScenarioEngineFrame(null);
    assertEquals(Map.of(), frame.variables());
    assertThrows(IllegalStateException.class, () -> frame.evaluate("id"));
  }
}
