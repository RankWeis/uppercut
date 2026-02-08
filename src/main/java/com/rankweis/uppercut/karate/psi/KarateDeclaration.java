package com.rankweis.uppercut.karate.psi;

import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.DECLARATION;

import com.intellij.lang.ASTNode;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.IncorrectOperationException;
import com.rankweis.uppercut.karate.psi.element.KarateNamedElement;
import com.rankweis.uppercut.karate.psi.impl.GherkinPsiElementBase;
import com.rankweis.uppercut.karate.util.KarateUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateDeclaration extends GherkinPsiElementBase implements PsiNameIdentifierOwner,
  GherkinPsiElement, GherkinSuppressionHolder, PomTarget, KarateNamedElement {

  public KarateDeclaration(@NotNull ASTNode node) {
    super(node);
  }

  @Override public String getName() {
    PsiElement id = getNameIdentifier();
    return id != null ? id.getText() : this.getText();
  }

  @Override public PsiReference @NotNull [] getReferences() {
    return CachedValuesManager.getCachedValue(this,
      () -> CachedValueProvider.Result.create(
        ReferenceProvidersRegistry.getReferencesFromProviders(this), this));
  }

  @Override protected void acceptGherkin(GherkinElementVisitor gherkinElementVisitor) {
    gherkinElementVisitor.visitDeclaration(this);
  }

  @Override public @Nullable PsiElement getNameIdentifier() {
    ASTNode keyNode = getNode().findChildByType(DECLARATION);
    return keyNode != null ? keyNode.getPsi() : null;
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    ASTNode keyNode = this.getNode().findChildByType(UppercutElementTypes.DECLARATION);
    if (keyNode != null) {
      GherkinPsiElement property =
        KarateUtil.createProperty(getProject(), name);
      ASTNode newKeyNode = property.getFirstChild().getNode();
      getNode().replaceChild(keyNode, newKeyNode);
    }
    return this;
  }
}
