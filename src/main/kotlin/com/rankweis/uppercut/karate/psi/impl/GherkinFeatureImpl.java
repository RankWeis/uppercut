// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi.impl;

import com.rankweis.uppercut.karate.psi.GherkinElementVisitor;
import com.rankweis.uppercut.karate.psi.GherkinFeature;
import com.rankweis.uppercut.karate.psi.GherkinStepsHolder;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;



public class GherkinFeatureImpl extends GherkinPsiElementBase implements GherkinFeature {
  public GherkinFeatureImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "GherkinFeature:" + getFeatureName();
  }

  @Override
  public String getFeatureName() {
    ASTNode node = getNode();
    final ASTNode firstText = node.findChildByType(KarateTokenTypes.TEXT);
    if (firstText != null) {
      return firstText.getText();
    }
    final GherkinFeatureHeaderImpl header = PsiTreeUtil.getChildOfType(this, GherkinFeatureHeaderImpl.class);
    if (header != null) {
      return header.getElementText();
    }
    return getElementText();
  }

  @Override
  public GherkinStepsHolder[] getScenarios() {
    List<GherkinStepsHolder> result = new ArrayList<>();

    GherkinStepsHolder[] scenarios = PsiTreeUtil.getChildrenOfType(this, GherkinStepsHolder.class);
    if (scenarios != null) {
      result.addAll(Arrays.asList(scenarios));
    }
    
    GherkinRuleImpl[] rules = PsiTreeUtil.getChildrenOfType(this, GherkinRuleImpl.class);
    if (rules != null) {
      for (GherkinRuleImpl rule : rules) {
        scenarios = PsiTreeUtil.getChildrenOfType(rule, GherkinStepsHolder.class);
        if (scenarios != null) {
          result.addAll(Arrays.asList(scenarios));
        }
      }
    }

    return result.toArray(GherkinStepsHolder.EMPTY_ARRAY);
  }

  @Override
  protected String getPresentableText() {
    return "Feature: " + getFeatureName();
  }

  @Override
  protected void acceptGherkin(GherkinElementVisitor gherkinElementVisitor) {
    gherkinElementVisitor.visitFeature(this);
  }
}
