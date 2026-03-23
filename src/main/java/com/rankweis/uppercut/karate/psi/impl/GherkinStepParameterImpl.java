package com.rankweis.uppercut.karate.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.rankweis.uppercut.karate.psi.GherkinElementFactory;
import com.rankweis.uppercut.karate.psi.GherkinElementVisitor;
import com.rankweis.uppercut.karate.psi.GherkinStepParameter;
import java.util.Objects;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class GherkinStepParameterImpl extends GherkinPsiElementBase implements GherkinStepParameter {
  public GherkinStepParameterImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  protected void acceptGherkin(GherkinElementVisitor gherkinElementVisitor) {
    gherkinElementVisitor.visitStepParameter(this);
  }

  @Override
  public String toString() {
    return "GherkinStepParameter:" + getText();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final LeafPsiElement content = PsiTreeUtil.getChildOfType(this, LeafPsiElement.class);
    PsiElement[] elements = GherkinElementFactory.getTopLevelElements(getProject(), name);
    getNode().replaceChild(Objects.requireNonNull(content), elements[0].getNode());
    return this;
  }

  @Override
  public PsiReference getReference() {
    return new GherkinStepParameterReference(this);
  }

  @Override
  public String getName() {
    return getText();
  }

  @Override
  public PsiElement getNameIdentifier() {
    return this;
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    return new LocalSearchScope(getContainingFile());
  }
}
