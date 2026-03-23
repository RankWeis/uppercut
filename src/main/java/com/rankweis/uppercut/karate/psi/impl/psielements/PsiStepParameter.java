package com.rankweis.uppercut.karate.psi.impl.psielements;

import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiStepParameter extends LeafPsiElement {

  public PsiStepParameter(@NotNull IElementType type,
    @NotNull CharSequence text) {
    super(type, text);
  }
}
