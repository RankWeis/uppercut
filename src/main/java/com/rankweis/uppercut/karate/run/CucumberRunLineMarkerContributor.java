// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.rankweis.uppercut.karate.run;

import com.intellij.execution.TestStateStorage;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.rankweis.uppercut.karate.CucumberUtil;
import com.rankweis.uppercut.karate.psi.GherkinFile;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class CucumberRunLineMarkerContributor extends RunLineMarkerContributor {

  private static final TokenSet RUN_LINE_MARKER_ELEMENTS = TokenSet
    .create(KarateTokenTypes.FEATURE_KEYWORD, KarateTokenTypes.SCENARIO_KEYWORD,
      KarateTokenTypes.SCENARIO_OUTLINE_KEYWORD,
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
    //    if (type == KarateTokenTypes.TAG &&
    //      PsiTreeUtil.findSiblingBackward(element.getParent(), UppercutElementTypes.TAG, null) != null) {
    //      // This is a hack; without this, if you have @Tag1 @Tag2 @Tag3, all the run configs will come back as @Tag3.
    //      // I can't figure out why this is, so the better UX is to just have one run configuration per line.
    //      return null;
    //    }
    TestStateStorage.Record state = getTestStateStorage(element, type);
    List<AnAction> actions = new ArrayList<>();
    if (type == KarateTokenTypes.TAG) {
      actions.addAll(Arrays.stream(ExecutorAction.getActions(0))
        .map(action -> new ElementSpecificAction(action, element.getText())).toList());
    } else {
      CollectionUtils.addAll(actions, ExecutorAction.getActions());
    }
    boolean isClass = type == KarateTokenTypes.FEATURE_KEYWORD || type == KarateTokenTypes.TAG;
    return new Info(getTestStateIcon(state, isClass), actions.toArray(AnAction[]::new), RUN_TEST_TOOLTIP_PROVIDER);
  }

  private static @Nullable TestStateStorage.Record getTestStateStorage(@NotNull PsiElement element, IElementType type) {
    String tag = type == KarateTokenTypes.TAG ? ":" + element.getText() : "";
    String url =
      element.getContainingFile().getVirtualFile().getUrl() + ":" + CucumberUtil.getLineNumber(element) + tag;
    return TestStateStorage.getInstance(element.getProject()).getState(url);
  }

  /**
   * Wraps an existing AnAction with a custom name and tooltip for a specific PsiElement.
   */
  private static class ElementSpecificAction extends AnAction {

    private final AnAction delegate;
    private final String templateText;
    private final String name;

    ElementSpecificAction(AnAction delegate, String name) {
      super(delegate.getTemplateText(), delegate.getTemplatePresentation().getDescription(),
        delegate.getTemplatePresentation().getIcon());
      this.templateText = delegate.getTemplateText();
      this.name = name;
      ;
      this.delegate = delegate;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      // Delegate the actual behavior back to the original action
      delegate.actionPerformed(e);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      // Provide a unique text presentation and tooltip for this action

      // Update the delegate to ensure any additional changes are propagated
      delegate.update(e);
      Presentation presentation = e.getPresentation();
      presentation.setText(delegate.getTemplateText().replace("context configuration", name));
    }

    @Override public @NotNull ActionUpdateThread getActionUpdateThread() {
      return delegate.getActionUpdateThread();
    }
  }
}