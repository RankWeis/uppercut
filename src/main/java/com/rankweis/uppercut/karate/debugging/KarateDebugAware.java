package com.rankweis.uppercut.karate.debugging;

import com.intellij.debugger.engine.JavaDebugAware;
import com.intellij.psi.PsiFile;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import com.rankweis.uppercut.karate.run.KarateLibraries;
import org.jetbrains.annotations.NotNull;

/**
 * Lets a Karate 1 feature file take <b>Java</b> line breakpoints, which is how v1 debugging works:
 * {@code KaratePositionManager} binds them to the bytecode of the step-definition method.
 *
 * <p>Karate 2 files are deliberately excluded. They have their own breakpoint type
 * ({@code KarateBreakpointType}), and a line that accepts two types is drawn by the platform as
 * several breakpoint circles the user has to choose between - for a choice that has one right answer.
 * A Java breakpoint on a v2 feature line could never bind anyway: v2 has no per-step Java method.</p>
 */
public class KarateDebugAware extends JavaDebugAware {

  public boolean isBreakpointAware(@NotNull PsiFile psiFile) {
    return psiFile.getLanguage().is(KarateLanguage.INSTANCE)
      && !KarateLibraries.isKarateV2(psiFile.getProject(), psiFile.getVirtualFile());
  }

}
