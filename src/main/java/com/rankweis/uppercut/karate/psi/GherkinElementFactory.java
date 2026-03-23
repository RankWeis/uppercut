// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.rankweis.uppercut.karate.CucumberElementFactory;
import com.rankweis.uppercut.karate.psi.i18n.JsonGherkinKeywordProvider;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public final class GherkinElementFactory {
  private static final Logger LOG = Logger.getInstance(GherkinElementFactory.class.getName());

  private GherkinElementFactory() {
  }

  public static GherkinFeature createFeatureFromText(final Project project, @NotNull final String text) {
    final PsiElement[] list = getTopLevelElements(project, text);
    for (PsiElement psiElement : list) {
      if (psiElement instanceof GherkinFeature) {
        return (GherkinFeature)psiElement;
      }
    }

    LOG.error("Failed to create Feature from text:\n" + text);
    return null;
  }

  public static GherkinStepsHolder createScenarioFromText(final Project project, final String language, @NotNull final String text) {
    final GherkinKeywordProvider provider = JsonGherkinKeywordProvider.getKeywordProvider();
    final GherkinKeywordTable keywordsTable = provider.getKeywordsTable(language);
    String featureText = "# language: " + language + "\n" + keywordsTable.getFeatureSectionKeyword() + ": Dummy\n" + text;
    GherkinFeature feature = createFeatureFromText(project, featureText);
    return Objects.requireNonNull(feature).getScenarios() [0];
  }

  public static PsiElement[] getTopLevelElements(final Project project, @NotNull final String text) {
    return CucumberElementFactory.createTempPsiFile(project, text).getChildren();
  }
}
