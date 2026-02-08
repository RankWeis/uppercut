package com.rankweis.uppercut.karate.psi.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.rankweis.uppercut.karate.psi.GherkinScenario;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import com.rankweis.uppercut.karate.psi.KarateDeclaration;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

  private final String key;

  public KarateReference(@NotNull PsiElement element,
    TextRange rangeInElement, boolean soft) {
    super(element, rangeInElement, soft);
    key = rangeInElement.substring(element.getText());
  }

  @Override public boolean isReferenceTo(@NotNull PsiElement element) {
    return resolve() == element;
  }

  @Override public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    PsiElement parent =
      PsiTreeUtil.findFirstParent(myElement, GherkinScenario.class::isInstance);

    KarateDeclaration match = findDeclarationInSteps(
      PsiTreeUtil.getChildrenOfType(parent, GherkinStep.class));
    if (match != null) {
      return new PsiElementResolveResult[]{new PsiElementResolveResult(match)};
    }

    if (!myElement.isValid()) {
      return new ResolveResult[0];
    }

    Collection<GherkinScenario> scenarios =
      PsiTreeUtil.findChildrenOfType(myElement.getContainingFile(), GherkinScenario.class);
    for (GherkinScenario scenario : scenarios) {
      if (!scenario.isBackground()) {
        continue;
      }
      match = findDeclarationInSteps(
        PsiTreeUtil.getChildrenOfType(scenario, GherkinStep.class));
      if (match != null) {
        return new PsiElementResolveResult[]{new PsiElementResolveResult(match)};
      }
    }

    return new ResolveResult[0];
  }

  private @Nullable KarateDeclaration findDeclarationInSteps(GherkinStep @Nullable [] steps) {
    if (steps == null) {
      return null;
    }
    for (GherkinStep step : steps) {
      KarateDeclaration[] decls = PsiTreeUtil.getChildrenOfType(step, KarateDeclaration.class);
      if (decls == null) {
        continue;
      }
      for (KarateDeclaration decl : decls) {
        if (decl.getText().equals(key)) {
          return decl;
        }
      }
    }
    return null;
  }

  @Override public @Nullable PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @Override public Object @NotNull [] getVariants() {
    return super.getVariants();
  }

  public String getKey() {
    return key;
  }
}