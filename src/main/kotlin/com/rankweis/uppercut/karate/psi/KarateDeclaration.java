package com.rankweis.uppercut.karate.psi;

import com.intellij.lang.ASTNode;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.IncorrectOperationException;
import com.rankweis.uppercut.karate.psi.element.KarateNamedElement;
import com.rankweis.uppercut.karate.psi.impl.GherkinPsiElementBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateDeclaration extends GherkinPsiElementBase implements PsiNameIdentifierOwner, 
  GherkinPsiElement, GherkinSuppressionHolder, PomTarget, KarateNamedElement {

  public KarateDeclaration(@NotNull ASTNode node) {
    super(node);
  }

  @Override protected void acceptGherkin(GherkinElementVisitor gherkinElementVisitor) {
    gherkinElementVisitor.visitDeclaration(this);
  }

  @Override public @Nullable PsiElement getNameIdentifier() {
    return getNode().getPsi();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    ASTNode keyNode = this.getNode().findChildByType(GherkinElementTypes.DECLARATION);
    if (keyNode != null) {
//      SimpleProperty property =
//        SimpleElementFactory.createProperty(element.getProject(), newName);
//      ASTNode newKeyNode = property.getFirstChild().getNode();
//      element.getNode().replaceChild(keyNode, newKeyNode);
    }
    return this;
  }
}
