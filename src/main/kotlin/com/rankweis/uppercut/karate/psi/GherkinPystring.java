package com.rankweis.uppercut.karate.psi;

import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectionBackgroundSuppressor;

public interface GherkinPystring extends GherkinPsiElement, PsiLanguageInjectionHost, InjectionBackgroundSuppressor {
}
