package com.rankweis.uppercut.karate.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.ILazyParseableElementTypeBase;
import org.jetbrains.annotations.NotNull;

public class JsonEmbeddedContentTokenType implements ILazyParseableElementTypeBase {
  

  @Override public ASTNode parseContents(@NotNull ASTNode chameleon) {
    return null;
  }

  @Override public boolean reuseCollapsedTokens() {
    return ILazyParseableElementTypeBase.super.reuseCollapsedTokens();
  }
}
