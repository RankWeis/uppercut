// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.rankweis.uppercut.karate.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiUtilCore;
import com.rankweis.uppercut.karate.MyBundle;
import com.rankweis.uppercut.karate.psi.GherkinElementFactory;
import com.rankweis.uppercut.karate.psi.GherkinElementVisitor;
import com.rankweis.uppercut.karate.psi.GherkinFile;
import com.rankweis.uppercut.karate.psi.GherkinKeywordTable;
import com.rankweis.uppercut.karate.psi.GherkinScenario;
import com.rankweis.uppercut.karate.psi.GherkinScenarioOutline;
import com.rankweis.uppercut.karate.psi.GherkinUtil;
import com.rankweis.uppercut.karate.psi.UppercutElementTypes;
import com.rankweis.uppercut.karate.psi.i18n.JsonGherkinKeywordProvider;
import java.util.Collection;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public final class KarateScenarioToScenarioOutlineInspection extends GherkinInspection {

  private static final class Holder {

    static final LocalQuickFix CONVERT_SCENARIO_TO_OUTLINE_FIX = new ConvertScenarioToOutlineFix();
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
    boolean isOnTheFly,
    @NotNull LocalInspectionToolSession session) {
    return new GherkinElementVisitor() {
      @Override
      public void visitScenario(GherkinScenario scenario) {
        if (scenario instanceof GherkinScenarioOutline) {
          return;
        }

        if (Stream.of(scenario.getChildren())
          .anyMatch(p -> PsiUtilCore.getElementType(p) == UppercutElementTypes.EXAMPLES_BLOCK)) {
          holder.registerProblem(scenario, scenario.getFirstChild().getTextRangeInParent(),
            MyBundle.message("inspection.gherkin.scenario.with.examples.section.error.message"),
            Holder.CONVERT_SCENARIO_TO_OUTLINE_FIX);
        }
      }
    };
  }

  private static class ConvertScenarioToOutlineFix implements LocalQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return MyBundle.message("inspection.gherkin.scenario.with.examples.section.quickfix.name");
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      GherkinScenario scenario = (GherkinScenario) descriptor.getPsiElement();
      String language = GherkinUtil.getFeatureLanguage((GherkinFile) scenario.getContainingFile());

      GherkinKeywordTable keywordsTable = JsonGherkinKeywordProvider.getKeywordProvider().getKeywordsTable(language);
      Collection<String> scenarioKeywords = keywordsTable.getScenarioKeywords();
      String scenarioRegexp = StringUtil.join(scenarioKeywords, "|");
      String scenarioOutlineKeyword = keywordsTable.getScenarioOutlineKeyword();

      String scenarioOutlineText = scenario.getText().replaceFirst(scenarioRegexp, scenarioOutlineKeyword);

      GherkinScenarioOutline scenarioOutline =
        (GherkinScenarioOutline) GherkinElementFactory.createScenarioFromText(project, language, scenarioOutlineText);
      scenario.replace(scenarioOutline);
    }
  }
}
