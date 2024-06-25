package com.rankweis.uppercut.karate.inspections.model;

import com.rankweis.uppercut.karate.inspections.suppress.GherkinSuppressionUtil;
import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GherkinInspectionSuppressor implements InspectionSuppressor {
  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
    return GherkinSuppressionUtil.isSuppressedFor(element, toolId);
  }

  @Override
  public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    return GherkinSuppressionUtil.getDefaultSuppressActions(toolId);
  }
}
