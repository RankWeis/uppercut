package com.rankweis.uppercut.testrunner.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What a breakpoint condition counts as true. Karate expressions return whatever the scenario holds,
 * so this cannot be a boolean-only rule: a condition of {@code response.id} means "when there is one".
 */
class ConditionTest {

  @Test
  void booleansAreThemselves() {
    assertTrue(DebugAgent.isTrue(Boolean.TRUE));
    assertFalse(DebugAgent.isTrue(Boolean.FALSE));
  }

  @Test
  void nothingIsFalse() {
    assertFalse(DebugAgent.isTrue(null));
    assertFalse(DebugAgent.isTrue(""));
  }

  @Test
  void anythingTheScenarioHoldsIsTrue() {
    assertTrue(DebugAgent.isTrue("sample-id"));
    assertTrue(DebugAgent.isTrue(0));
    assertTrue(DebugAgent.isTrue(Map.of()));
    assertTrue(DebugAgent.isTrue(List.of()));
  }

  @Test
  void anErrorIsReportedByItsInnermostMessage() {
    Throwable wrapped = new IllegalStateException("outer",
      new RuntimeException("wrapper", new IllegalArgumentException("id is not defined")));
    org.junit.jupiter.api.Assertions.assertEquals("id is not defined", DebugAgent.rootMessage(wrapped));
  }
}
