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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

  private final String key;

  public KarateReference(@NotNull PsiElement element,
    TextRange rangeInElement, boolean soft) {
    super(element, rangeInElement, soft);
    key = element.getText().substring(rangeInElement.getStartOffset(), rangeInElement.getEndOffset());
  }

  @Override public boolean isReferenceTo(@NotNull PsiElement element) {
    return resolve() == element;
  }

  @Override public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    final List<PsiElement> properties = new ArrayList<>();
    PsiElement parent =
      PsiTreeUtil.findFirstParent(myElement, GherkinScenario.class::isInstance);

    List<KarateDeclaration> declarationsInScenario = Arrays.stream(PsiTreeUtil.getChildrenOfType(parent, GherkinStep.class))
      .flatMap(step -> {
        KarateDeclaration[] childrenOfType = PsiTreeUtil.getChildrenOfType(step, KarateDeclaration.class);
        return childrenOfType == null ? Stream.of() : Arrays.stream(childrenOfType);
      }).filter(t -> t.getText().equals(key))
      .toList();
    if (!declarationsInScenario.isEmpty()) {
      return new PsiElementResolveResult[]{new PsiElementResolveResult(declarationsInScenario.get(0))};
    } else {
      List<KarateDeclaration> declarationsInBackground =
        PsiTreeUtil.findChildrenOfType(myElement.getContainingFile(), GherkinScenario.class)
          .stream()
          .filter(GherkinScenario::isBackground)
          .flatMap(step -> {
            GherkinStep[] gherkinSteps = PsiTreeUtil.getChildrenOfType(step, GherkinStep.class);
            return gherkinSteps != null ? Arrays.stream(gherkinSteps) : Stream.of();
          })
          .flatMap(step -> {
            KarateDeclaration[] childrenOfType = PsiTreeUtil.getChildrenOfType(step, KarateDeclaration.class);
            return childrenOfType == null ? Stream.of() : Arrays.stream(childrenOfType);
          }).filter(t -> t.getText().equals(key))
          .toList();
      if (!declarationsInBackground.isEmpty()) {
        return new PsiElementResolveResult[]{new PsiElementResolveResult(declarationsInBackground.get(0))};
      }
    }


    List<ResolveResult> results = new ArrayList<>();

    for (PsiElement property : properties) {
      results.add(new PsiElementResolveResult(property));
    }
    return results.toArray(new ResolveResult[0]);
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