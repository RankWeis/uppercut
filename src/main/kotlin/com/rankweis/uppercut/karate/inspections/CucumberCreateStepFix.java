// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.inspections;

import com.intellij.codeInsight.intention.HighPriorityAction;
import org.jetbrains.annotations.NotNull;
import com.rankweis.uppercut.karate.MyBundle;
import com.rankweis.uppercut.karate.psi.GherkinStep;


public class CucumberCreateStepFix extends CucumberCreateStepFixBase implements HighPriorityAction {
  @Override
  @NotNull
  public String getName() {
    return MyBundle.message("cucumber.create.step.title");
  }

  @Override
  protected void createStepOrSteps(GherkinStep step, @NotNull final CucumberStepDefinitionCreationContext fileAndFrameworkType) {
    createFileOrStepDefinition(step, fileAndFrameworkType);
  }
}
