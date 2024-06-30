// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.run;

import com.intellij.execution.TestStateStorage;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.rankweis.uppercut.karate.CucumberUtil;
import com.rankweis.uppercut.karate.psi.GherkinFile;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class CucumberRunLineMarkerContributor extends RunLineMarkerContributor {
  private static final TokenSet RUN_LINE_MARKER_ELEMENTS = TokenSet
    .create(KarateTokenTypes.FEATURE_KEYWORD, KarateTokenTypes.SCENARIO_KEYWORD, KarateTokenTypes.SCENARIO_OUTLINE_KEYWORD,
            KarateTokenTypes.RULE_KEYWORD, KarateTokenTypes.EXAMPLE_KEYWORD, KarateTokenTypes.TAG);

  @Nullable
  @Override
  public Info getInfo(@NotNull PsiElement element) {
    if (!(element instanceof LeafElement)) {
      return null;
    }
    PsiFile psiFile = element.getContainingFile();
    if (!(psiFile instanceof GherkinFile)) {
      return null;
    }
    IElementType type = PsiUtilCore.getElementType(element);
    if (!RUN_LINE_MARKER_ELEMENTS.contains(type)) {
      return null;
    }
    TestStateStorage.Record state = getTestStateStorage(element);
    AnAction[] actions = ExecutorAction.getActions(0);
    boolean isClass = false;
    if (type == KarateTokenTypes.FEATURE_KEYWORD || type == KarateTokenTypes.TAG) {
      isClass = true;
    }
    return new Info(getTestStateIcon(state, isClass),  actions, RUN_TEST_TOOLTIP_PROVIDER);
  }
  
  private static @Nullable TestStateStorage.Record getTestStateStorage(@NotNull PsiElement element) {
    String url = element.getContainingFile().getVirtualFile().getUrl() + ":" + CucumberUtil.getLineNumber(element);
    return TestStateStorage.getInstance(element.getProject()).getState(url);
  }
}