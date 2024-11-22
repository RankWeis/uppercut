package com.rankweis.uppercut.karate.psi.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import com.rankweis.uppercut.karate.psi.GherkinPsiElement;
import org.jetbrains.annotations.NotNull;

public class GherkinSimpleReference implements PsiReference {

  private final GherkinPsiElement myElement;

  public GherkinSimpleReference(GherkinPsiElement element) {
    myElement = element;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return myElement;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return new TextRange(0, myElement.getTextLength());
  }

  @Override
  public PsiElement resolve() {
    return myElement;
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return myElement.getText();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    if (myElement instanceof PsiNamedElement) {
      ((PsiNamedElement) myElement).setName(newElementName);
    }
    return myElement;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return myElement;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    PsiElement myResolved = resolve();
    PsiElement resolved = element.getReference() != null ? element.getReference().resolve() : null;
    if (resolved == null) {
      resolved = element;
    }
    return resolved.equals(myResolved);
  }

  @Override
  public boolean isSoft() {
    return false;
  }
}
