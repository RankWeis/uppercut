package com.rankweis.uppercut.karate.inspections.suppress;

import com.rankweis.uppercut.karate.MyBundle;
import com.intellij.codeInsight.daemon.impl.actions.AbstractBatchSuppressByNoInspectionCommentFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.rankweis.uppercut.karate.psi.GherkinFeature;
import org.jetbrains.annotations.NotNull;

public class GherkinSuppressForFeatureCommentFix extends AbstractBatchSuppressByNoInspectionCommentFix {
  GherkinSuppressForFeatureCommentFix(@NotNull final String toolId) {
    super(toolId, false);
  }

  @NotNull
  @Override
  public String getText() {
    return MyBundle.message("cucumber.inspection.suppress.feature");
  }

  @Override
  public PsiElement getContainer(PsiElement context) {
    // step
    return PsiTreeUtil.getNonStrictParentOfType(context, GherkinFeature.class);
  }
}
