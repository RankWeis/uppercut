package com.rankweis.uppercut.karate.steps.reference;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.rankweis.uppercut.karate.psi.impl.GherkinStepImpl;
import org.jetbrains.annotations.NotNull;

public final class KarateReferenceContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(GherkinStepImpl.class),
      new KarateStepReferenceProvider());
  }
}
