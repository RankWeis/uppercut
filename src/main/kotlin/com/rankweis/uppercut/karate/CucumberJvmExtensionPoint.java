package com.rankweis.uppercut.karate;

import com.rankweis.uppercut.karate.psi.GherkinFile;
import com.rankweis.uppercut.karate.psi.impl.GherkinStepImpl;
import com.rankweis.uppercut.karate.steps.AbstractStepDefinition;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CucumberJvmExtensionPoint {
  ExtensionPointName<CucumberJvmExtensionPoint> EP_NAME =
    ExtensionPointName.create("com.rankweis.cucumberJvmExtensionPoint");

  // ToDo: remove parent
  /**
   * Checks if the child could be step definition file
   * @param child a PsiFile
   * @param parent container of the child
   * @return true if the child could be step definition file, else otherwise
   */
  boolean isStepLikeFile(@NotNull PsiElement child, @NotNull PsiElement parent);

  /**
   * Checks if the child could be a step definition container
   * @param child PsiElement to check
   * @param parent it's container
   * @return true if child could be step definition container and it's possible to write in it
   */
  boolean isWritableStepLikeFile(@NotNull PsiElement child, @NotNull PsiElement parent);

  /**
   * Provides type of step definition file
   * @return type
   */
  @NotNull
  BDDFrameworkType getStepFileType();


  @NotNull
  StepDefinitionCreator getStepDefinitionCreator();

  /**
   * Provides all possible step definitions available from current feature file.
   */
  @Nullable
  List<AbstractStepDefinition> loadStepsFor(@Nullable PsiFile featureFile, @NotNull Module module);

  Collection<? extends PsiFile> getStepDefinitionContainers(@NotNull GherkinFile file);
  
  default boolean isGherkin6Supported(@NotNull Module module) {
    return false;
  }
  
  @Nullable
  default String getStepName(@NotNull PsiElement step) {
    if (!(step instanceof GherkinStepImpl)) {
      return null;
    }
    return ((GherkinStepImpl)step).getSubstitutedName();
  }
}
