package com.rankweis.uppercut.karate.psi.element;

import com.intellij.json.json5.Json5Language;
import com.intellij.json.psi.impl.JsonObjectImpl;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class KarateJsonObject extends JsonObjectImpl {

  public KarateJsonObject(@NotNull ASTNode node) {
    super(node);
  }

  @Override public @NotNull Language getLanguage() {
    return Json5Language.INSTANCE;
  }

  @Override public @NotNull ASTNode getNode() {
    return super.getNode();
  }

  @Override public PsiElement getPrevSibling() {
    return super.getPrevSibling();
  }
}
