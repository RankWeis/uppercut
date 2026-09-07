package com.rankweis.uppercut.karate.debugging.agent;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.rankweis.uppercut.karate.psi.GherkinFileType;
import org.jetbrains.annotations.NotNull;

/**
 * What the debugger's expression fields are edited as. Karate expressions are Gherkin step
 * right-hand sides, so the feature file type gives them the right highlighting and completion.
 *
 * <p>Nothing evaluates them yet - the evaluator arrives with phase 3 - but a breakpoint's condition
 * field and the Evaluate dialog both need a provider before they can be offered at all.</p>
 */
public class KarateDebugEditorsProvider extends XDebuggerEditorsProvider {

  @Override
  public @NotNull FileType getFileType() {
    return GherkinFileType.INSTANCE;
  }
}
