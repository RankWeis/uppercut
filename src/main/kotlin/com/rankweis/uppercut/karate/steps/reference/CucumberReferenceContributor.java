package com.rankweis.uppercut.karate.steps.reference;

import static com.intellij.patterns.StandardPatterns.or;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.rankweis.uppercut.karate.psi.KarateDeclaration;
import com.rankweis.uppercut.karate.psi.impl.GherkinStepImpl;
import org.jetbrains.annotations.NotNull;

public final class CucumberReferenceContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      or(
        PlatformPatterns.psiElement(GherkinStepImpl.class),
        PlatformPatterns.psiElement(KarateDeclaration.class)),
      new CucumberStepReferenceProvider());

  }
}
