package com.rankweis.uppercut.karate.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.rankweis.uppercut.karate.MyBundle;
import com.rankweis.uppercut.karate.psi.GherkinElementVisitor;
import com.rankweis.uppercut.karate.psi.GherkinScenario;
import org.jetbrains.annotations.NotNull;

public final class KarateMisplacedBackgroundInspection extends GherkinInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new GherkinElementVisitor() {
      @Override
      public void visitScenario(GherkinScenario scenario) {
        if (scenario.isBackground()) {

          PsiElement element = scenario.getPrevSibling();

          while (element != null) {
            if (element instanceof GherkinScenario) {
              if (!((GherkinScenario) element).isBackground()) {
                holder.registerProblem(scenario.getFirstChild(),
                  MyBundle.message("inspection.gherkin.background.after.scenario.error.message"),
                  ProblemHighlightType.ERROR);
                break;
              }
            }
            element = element.getPrevSibling();
          }
        }
      }
    };
  }

  @NotNull
  @Override
  public String getShortName() {
    return "KarateMisplacedBackground";
  }
}
