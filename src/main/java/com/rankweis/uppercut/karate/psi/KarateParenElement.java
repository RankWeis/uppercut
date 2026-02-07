package com.rankweis.uppercut.karate.psi;

import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.OPEN_PAREN;

import com.intellij.lang.ASTNode;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.IncorrectOperationException;
import com.rankweis.uppercut.karate.psi.element.KarateNamedElement;
import com.rankweis.uppercut.karate.psi.impl.GherkinPsiElementBase;
import com.rankweis.uppercut.util.KarateUtil;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateParenElement extends GherkinPsiElementBase implements PsiNameIdentifierOwner,
  GherkinPsiElement, GherkinSuppressionHolder, PomTarget, KarateNamedElement {

  ConcurrentLinkedQueue<PsiReference> references = new ConcurrentLinkedQueue<>();

  public KarateParenElement(@NotNull ASTNode node) {
    super(node);
  }

  @Override public String getName() {
    return this.getText();
  }

  @Override public PsiReference getReference() {
    return super.getReference();
  }

  @Override public PsiReference @NotNull [] getReferences() {
    return this.references.stream().filter(r -> r.isReferenceTo(this)).toList().toArray(new PsiReference[0]);
  }

  @Override public PsiReference findReferenceAt(int offset) {
    return super.findReferenceAt(offset);
  }

  public void addReference(PsiReference reference) {
    this.references.add(reference);
  }

  @Override protected void acceptGherkin(GherkinElementVisitor gherkinElementVisitor) {
    gherkinElementVisitor.visitElement(this);
  }

  @Override public @Nullable PsiElement getNameIdentifier() {
    ASTNode keyNode = getNode().findChildByType(OPEN_PAREN);
    PsiElement element = Objects.requireNonNull(keyNode).getPsi();
    while (element != null) {
      element = element.getNextSibling();
      if (element instanceof PsiWhiteSpace) {
        continue;
      }
      break;
    }
    return element;
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    ASTNode keyNode = this.getNode().findChildByType(OPEN_PAREN);
    if (keyNode != null) {
      GherkinPsiElement property =
        KarateUtil.createProperty(getProject(), name);
      ASTNode newKeyNode = property.getFirstChild().getNode();
      getNode().replaceChild(keyNode, newKeyNode);
    }
    return this;
  }
}
