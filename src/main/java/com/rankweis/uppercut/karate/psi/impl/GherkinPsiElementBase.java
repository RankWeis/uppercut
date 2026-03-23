// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be 
// found in the LICENSE file.
package com.rankweis.uppercut.karate.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.rankweis.uppercut.karate.psi.GherkinElementVisitor;
import com.rankweis.uppercut.karate.psi.GherkinPsiElement;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public abstract class GherkinPsiElementBase extends ASTWrapperPsiElement implements GherkinPsiElement {

  private static final TokenSet TEXT_FILTER = TokenSet.create(KarateTokenTypes.TEXT);

  public GherkinPsiElementBase(@NotNull final ASTNode node) {
    super(node);
  }

  @NotNull
  protected String getElementText() {
    final ASTNode node = getNode();
    final ASTNode[] children = node.getChildren(TEXT_FILTER);
    return StringUtil.join(children, ASTNode::getText, " ").trim();
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    PsiElement psiChild = getFirstChild();
    if (psiChild == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    List<PsiElement> result = null;
    while (psiChild != null) {
      if (result == null) {
        result = new ArrayList<>();
      }
      result.add(psiChild);
      psiChild = psiChild.getNextSibling();
    }
    return PsiUtilCore.toPsiElementArray(result);
  }


  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return GherkinPsiElementBase.this.getPresentableText();
      }

      @Override
      public Icon getIcon(final boolean open) {
        return GherkinPsiElementBase.this.getIcon(Iconable.ICON_FLAG_VISIBILITY);
      }
    };
  }

  protected String getPresentableText() {
    return toString();
  }

  protected String buildPresentableText(final String prefix) {
    final StringBuilder result = new StringBuilder(prefix);
    final String name = getElementText();
    if (!StringUtil.isEmpty(name)) {
      result.append(": ").append(name);
    }
    return result.toString();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof GherkinElementVisitor v) {
      acceptGherkin(v);
    } else {
      super.accept(visitor);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true; // Same instance
    }
    if (!(obj instanceof GherkinPsiElementBase other)) {
      return false; // Different types
    }

    ASTNode thisNode = this.getNode();
    ASTNode otherNode = other.getNode();

    if (thisNode == null || otherNode == null) {
      return false; // One or both nodes are missing
    }

    return thisNode.getElementType().equals(otherNode.getElementType()) &&
      thisNode.getTextRange().equals(otherNode.getTextRange()) &&
      thisNode.getText().equals(otherNode.getText());
  }

  protected abstract void acceptGherkin(GherkinElementVisitor gherkinElementVisitor);

}
