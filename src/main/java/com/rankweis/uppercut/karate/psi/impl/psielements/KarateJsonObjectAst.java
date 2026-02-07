package com.rankweis.uppercut.karate.psi.impl.psielements;

import com.intellij.json.json5.Json5Language;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class KarateJsonObjectAst extends LeafPsiElement {

  public KarateJsonObjectAst(@NotNull IElementType type,
    @NotNull CharSequence text) {
    super(type, text);
  }

  @Override public void accept(@NotNull PsiElementVisitor visitor) {
    super.accept(visitor);
  }

  @Override public @NotNull Language getLanguage() {
    return Json5Language.INSTANCE;
  }
}
