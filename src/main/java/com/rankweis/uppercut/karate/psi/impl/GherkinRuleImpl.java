// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi.impl;

import com.rankweis.uppercut.karate.psi.GherkinElementVisitor;
import com.rankweis.uppercut.karate.psi.GherkinRule;
import com.rankweis.uppercut.karate.psi.GherkinStepsHolder;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;



public class GherkinRuleImpl extends GherkinPsiElementBase implements GherkinRule {
  public GherkinRuleImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public String toString() {
    return "GherkinRule:" + getRuleName();
  }

  @Override
  public String getRuleName() {
    ASTNode node = getNode();
    final ASTNode firstText = node.findChildByType(KarateTokenTypes.TEXT);
    if (firstText != null) {
      return firstText.getText();
    }
    return getElementText();
  }

  @Override
  public GherkinStepsHolder[] getScenarios() {
    final GherkinStepsHolder[] children = PsiTreeUtil.getChildrenOfType(this, GherkinStepsHolder.class);
    return children == null ? GherkinStepsHolder.EMPTY_ARRAY : children;
  }

  @Override
  protected String getPresentableText() {
    return "Rule: " + getRuleName();
  }

  @Override
  protected void acceptGherkin(@NotNull GherkinElementVisitor gherkinElementVisitor) {
    gherkinElementVisitor.visitRule(this);
  }
}
