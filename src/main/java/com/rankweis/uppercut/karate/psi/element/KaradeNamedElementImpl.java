package com.rankweis.uppercut.karate.psi.element;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public abstract class KaradeNamedElementImpl extends ASTWrapperPsiElement implements KarateNamedElement {

  public KaradeNamedElementImpl(@NotNull ASTNode node) {
    super(node);
  }
}
