package com.rankweis.uppercut.karate.psi.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

  private final String key;

  public KarateReference(@NotNull PsiElement element,
    TextRange rangeInElement, boolean soft) {
    super(element, rangeInElement, soft);
    key = element.getText().substring(rangeInElement.getStartOffset(), rangeInElement.getEndOffset());
  }

  @Override public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    return new ResolveResult[0];
  }

  @Override public @Nullable PsiElement resolve() {
    return null;
  }

  @Override public Object @NotNull [] getVariants() {
    return super.getVariants();
  }
}