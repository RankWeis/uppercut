package com.rankweis.uppercut.karate.injector;

import com.intellij.formatting.InjectedFormattingOptionsProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateInjectedFormattingOptionsProvider implements InjectedFormattingOptionsProvider {

  @Override public @Nullable Boolean shouldDelegateToTopLevel(@NotNull PsiFile file) {
    return false;
  }
}
