package com.rankweis.uppercut.karate.inspections;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JSInspector implements InspectionSuppressor {
  private static final Set<String> SUPPRESSED_INSPECTIONS = new HashSet<>();

  public void visitElement(@NotNull PsiElement element) {
  }

  @Override public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
    if ("karate".equals(element.getText())) {
      if (toolId.equals("JSUnresolvedReference")) {
        return true;
      }
    }
    return SUPPRESSED_INSPECTIONS.contains(toolId)
      && element.getContainingFile().getLanguage() == KarateLanguage.INSTANCE;
  }

  @Override
  public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    return new SuppressQuickFix[0];
  }

  static {
    SUPPRESSED_INSPECTIONS.add("ReservedWordAsName");
  }
}
