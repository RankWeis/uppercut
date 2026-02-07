package com.rankweis.uppercut.karate.inspections;

import com.rankweis.uppercut.karate.CucumberUtil;
import com.rankweis.uppercut.karate.steps.AbstractStepDefinition;
import com.rankweis.uppercut.karate.steps.reference.CucumberStepReference;
import com.rankweis.uppercut.karate.MyBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import com.rankweis.uppercut.karate.psi.GherkinFeature;
import com.rankweis.uppercut.karate.psi.GherkinFile;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import com.rankweis.uppercut.karate.psi.GherkinStepsHolder;

public class CucumberCreateAllStepsFix extends CucumberCreateStepFixBase {
  @NotNull
  @Override
  public String getName() {
    return MyBundle.message("cucumber.create.all.steps.title");
  }

  @Override
  protected void createStepOrSteps(GherkinStep sourceStep, @NotNull final CucumberStepDefinitionCreationContext fileAndFrameworkType) {
    final PsiFile probableGherkinFile = sourceStep.getContainingFile();
    if (!(probableGherkinFile instanceof GherkinFile gherkinFile)) {
      return;
    }

    final Set<String> createdStepDefPatterns = new HashSet<>();
    for (GherkinFeature feature : gherkinFile.getFeatures()) {
      for (GherkinStepsHolder stepsHolder : feature.getScenarios()) {
        for (GherkinStep step : stepsHolder.getSteps()) {
          final PsiReference[] references = step.getReferences();
          for (PsiReference reference : references) {
            if (!(reference instanceof CucumberStepReference)) continue;

            final AbstractStepDefinition definition = ((CucumberStepReference)reference).resolveToDefinition();
            if (definition == null) {
              String pattern = Pattern.quote(step.getName());
              pattern = StringUtil.trimEnd(StringUtil.trimStart(pattern, "\\Q"), "\\E");
              pattern = CucumberUtil.prepareStepRegexp(pattern);
              if (!createdStepDefPatterns.contains(pattern)) {
                if (!createFileOrStepDefinition(step, fileAndFrameworkType)) {
                  return;
                }
                createdStepDefPatterns.add(pattern);
              }
            }
          }
        }
      }
    }
  }

  @Override
  protected boolean shouldRunTemplateOnStepDefinition() {
    return false;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return IntentionPreviewInfo.EMPTY;
  }
}
