package com.rankweis.uppercut.karate.psi;

import com.intellij.lang.ASTNode;
import com.rankweis.uppercut.karate.psi.impl.GherkinPsiElementBase;
import org.jetbrains.annotations.NotNull;

public class KarateEmbeddedJsonElement extends GherkinPsiElementBase {

  public KarateEmbeddedJsonElement(@NotNull ASTNode node) {
    super(node);
  }

  @Override protected void acceptGherkin(GherkinElementVisitor gherkinElementVisitor) {
    gherkinElementVisitor.visitElement(this);
  }
}
