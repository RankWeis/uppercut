// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.inspections;

import com.rankweis.uppercut.karate.CucumberUtil;
import com.rankweis.uppercut.karate.steps.AbstractStepDefinition;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.rankweis.uppercut.karate.steps.CucumberStepHelper;
import com.rankweis.uppercut.karate.steps.reference.CucumberStepReference;
import org.jetbrains.annotations.NotNull;
import com.rankweis.uppercut.karate.psi.GherkinElementVisitor;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import com.rankweis.uppercut.karate.psi.GherkinStepsHolder;


public final class CucumberStepInspection extends GherkinInspection {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String getShortName() {
    return "CucumberUndefinedStep";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new GherkinElementVisitor() {
      @Override
      public void visitStep(GherkinStep step) {
        super.visitStep(step);

        final PsiElement parent = step.getParent();
        if (parent instanceof GherkinStepsHolder) {
          CucumberStepReference reference = CucumberUtil.getCucumberStepReference(step);
          if (reference == null) {
            return;
          }
          final AbstractStepDefinition definition = reference.resolveToDefinition();
          if (definition == null) {
            CucumberCreateStepFix createStepFix = null;
            CucumberCreateAllStepsFix createAllStepsFix = null;
            if (CucumberStepHelper.getExtensionCount() > 0) {
              createStepFix = new CucumberCreateStepFix();
              createAllStepsFix = new CucumberCreateAllStepsFix();
            }
            // steps all are undefined
//            holder.registerProblem(reference.getElement(), reference.getRangeInElement(),
//                                   MyBundle.message("karate.inspection.undefined.step.msg.name"),
//                                   createStepFix, createAllStepsFix);
          }
        }
      }
    };
  }
}
