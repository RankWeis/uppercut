package com.rankweis.uppercut.testrunner.debug;

import java.util.Map;

/**
 * What the debugger can ask of a thread that is parked on a breakpoint. Implemented per Karate major
 * - the v2 adapter reads a {@code ScenarioRuntime}; a v1 adapter will read {@code engine.vars} - so
 * {@link DebugAgent} serves variables and evaluation without knowing either.
 */
public interface SuspendedFrame {

  /** The scenario's variables as Karate sees them, in Karate's own order. */
  Map<String, Object> variables();

  /**
   * Evaluates a Karate expression in the paused scenario. Implementations throw whatever Karate
   * throws; the agent turns that into an error for the IDE rather than letting it escape.
   */
  Object evaluate(String expression) throws Exception;
}
