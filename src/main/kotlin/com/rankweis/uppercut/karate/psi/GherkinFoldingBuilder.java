// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.JAVASCRIPT;
import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.JSON;
import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.TEXT_BLOCK;
import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.XML;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.rankweis.uppercut.karate.psi.impl.GherkinExamplesBlockImpl;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;


public final class GherkinFoldingBuilder extends FoldingBuilderEx implements DumbAware {

  private static final TokenSet BLOCKS_TO_FOLD = TokenSet.create(UppercutElementTypes.SCENARIO,
    UppercutElementTypes.SCENARIO_OUTLINE,
    UppercutElementTypes.EXAMPLES_BLOCK,
    UppercutElementTypes.PYSTRING, JSON, JAVASCRIPT, XML, TEXT_BLOCK);


  @Override public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document,
    boolean quick) {
    List<FoldingDescriptor> descriptors = new ArrayList<>();
    appendDescriptors(root.getNode(), descriptors);
    return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
  }

  private static void appendDescriptors(ASTNode node, List<FoldingDescriptor> descriptors) {
    if (BLOCKS_TO_FOLD.contains(node.getElementType()) && node.getTextRange().getLength() >= 2) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      appendDescriptors(child, descriptors);
      child = child.getTreeNext();
    }
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    if (node.getPsi() instanceof GherkinStepsHolder ||
        node.getPsi() instanceof GherkinExamplesBlockImpl) {
      ItemPresentation presentation = ((NavigationItem) node.getPsi()).getPresentation();
      if (presentation != null) {
        return presentation.getPresentableText();
      }
    }
    return "...";
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }
}
