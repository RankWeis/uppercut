// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import com.rankweis.uppercut.karate.steps.AbstractStepDefinition;
import com.rankweis.uppercut.karate.steps.reference.CucumberStepReference;
import com.intellij.lang.ASTNode;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiNamedElement;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface GherkinStep extends GherkinPsiElement, GherkinSuppressionHolder, PomTarget, PsiNamedElement {
  GherkinStep[] EMPTY_ARRAY = new GherkinStep[0];

  ASTNode getKeyword();

  @Override
  @NotNull
  String getName();

  @Nullable
  GherkinTable getTable();

  @Nullable
  GherkinPystring getPystring();

  GherkinStepsHolder getStepHolder();

  /**
   * @return List with not empty unique possible substitutions names
   */
  List<String> getParamsSubstitutions();

  @Nullable
  String getSubstitutedName();

  /**
   * @return all step definitions (may be heavy). Works just like {@link CucumberStepReference#resolveToDefinition()}
   * @see CucumberStepReference#resolveToDefinition()
   */
  @NotNull
  Collection<AbstractStepDefinition> findDefinitions();


  /**
   * Checks if step can be renamed (actually, all definitions are asked).
   * See {@link AbstractStepDefinition#supportsRename(String)}.
   *
   * @param newName new name (to check if renaming to it is supported) or null to check if step could be renamed at all.
   *                Steps with out of defintiions can't be renamed as well.
   * @return true it could be
   */
  boolean isRenameAllowed(@Nullable String newName);
}
