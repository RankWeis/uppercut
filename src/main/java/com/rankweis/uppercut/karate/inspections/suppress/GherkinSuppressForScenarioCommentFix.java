package com.rankweis.uppercut.karate.inspections.suppress;

import com.intellij.codeInsight.daemon.impl.actions.AbstractBatchSuppressByNoInspectionCommentFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.rankweis.uppercut.karate.MyBundle;
import com.rankweis.uppercut.karate.psi.GherkinStepsHolder;
import org.jetbrains.annotations.NotNull;

public class GherkinSuppressForScenarioCommentFix extends AbstractBatchSuppressByNoInspectionCommentFix {
  GherkinSuppressForScenarioCommentFix(@NotNull final String toolId) {
    super(toolId, false);
  }

  @NotNull
  @Override
  public String getText() {
    return MyBundle.message("cucumber.inspection.suppress.scenario");
  }

  @Override
  public PsiElement getContainer(PsiElement context) {
    // steps holder
    return PsiTreeUtil.getNonStrictParentOfType(context, GherkinStepsHolder.class);
  }
}
