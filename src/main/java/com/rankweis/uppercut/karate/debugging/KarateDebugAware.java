package com.rankweis.uppercut.karate.debugging;

import com.intellij.debugger.engine.JavaDebugAware;
import com.intellij.psi.PsiFile;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import org.jetbrains.annotations.NotNull;

public class KarateDebugAware extends JavaDebugAware {

  public boolean isBreakpointAware(@NotNull PsiFile psiFile) {
    return psiFile.getLanguage().is(KarateLanguage.INSTANCE);
  }

}
