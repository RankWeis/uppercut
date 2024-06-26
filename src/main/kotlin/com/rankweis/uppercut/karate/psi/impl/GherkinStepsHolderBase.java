package com.rankweis.uppercut.karate.psi.impl;

import com.rankweis.uppercut.karate.psi.GherkinStep;
import com.rankweis.uppercut.karate.psi.GherkinStepsHolder;
import com.rankweis.uppercut.karate.psi.GherkinTag;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public abstract class GherkinStepsHolderBase extends GherkinPsiElementBase implements GherkinStepsHolder {
  protected GherkinStepsHolderBase(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public final String getScenarioName() {
    final StringBuilder result = new StringBuilder();

    ASTNode node = getNode().getFirstChildNode();
    while (node != null && node.getElementType() != KarateTokenTypes.COLON) {
      node = node.getTreeNext();
    }
    if (node != null) {
      node = node.getTreeNext();
    }

    while (node != null && !node.getText().contains("\n")) {
      result.append(node.getText());
      node = node.getTreeNext();
    }
    return result.toString().trim();
  }

  @Override
  public final GherkinStep @NotNull [] getSteps() {
    final GherkinStep[] steps = PsiTreeUtil.getChildrenOfType(this, GherkinStep.class);
    return steps == null ? GherkinStep.EMPTY_ARRAY : steps;
  }

  @Override
  public final GherkinTag[] getTags() {
    final GherkinTag[] tags = PsiTreeUtil.getChildrenOfType(this, GherkinTag.class);
    return tags == null ? GherkinTag.EMPTY_ARRAY : tags;
  }

  @Override
  @NotNull
  public String getScenarioKeyword() {
    return getFirstChild().getText();
  }
}
