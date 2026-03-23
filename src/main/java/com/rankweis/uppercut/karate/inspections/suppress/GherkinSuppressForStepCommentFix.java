package com.rankweis.uppercut.karate.inspections.suppress;

import com.intellij.codeInsight.daemon.impl.actions.AbstractBatchSuppressByNoInspectionCommentFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.rankweis.uppercut.karate.MyBundle;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import org.jetbrains.annotations.NotNull;

public class GherkinSuppressForStepCommentFix extends AbstractBatchSuppressByNoInspectionCommentFix {
  GherkinSuppressForStepCommentFix(@NotNull final String toolId) {
    super(toolId, false);
  }

  @NotNull
  @Override
  public String getText() {
    return MyBundle.message("cucumber.inspection.suppress.step");
  }

  @Override
  public PsiElement getContainer(PsiElement context) {
    // step
    return PsiTreeUtil.getNonStrictParentOfType(context, GherkinStep.class);
  }
}

