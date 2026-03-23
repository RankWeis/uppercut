package com.rankweis.uppercut.karate.inspections;

import com.intellij.psi.PsiFile;
import com.rankweis.uppercut.karate.BDDFrameworkType;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

public class CucumberStepDefinitionCreationContext {

  private @Nullable PsiFile psiFile;
  private @Nullable BDDFrameworkType frameworkType;

  public CucumberStepDefinitionCreationContext() {
    this(null, null);
  }

  public CucumberStepDefinitionCreationContext(@Nullable PsiFile psiFile,
    @Nullable BDDFrameworkType frameworkType) {
    this.psiFile = psiFile;
    this.frameworkType = frameworkType;
  }

  public @Nullable PsiFile getPsiFile() {
    return psiFile;
  }

  public void setPsiFile(@Nullable PsiFile psiFile) {
    this.psiFile = psiFile;
  }

  public @Nullable BDDFrameworkType getFrameworkType() {
    return frameworkType;
  }

  public void setFrameworkType(@Nullable BDDFrameworkType frameworkType) {
    this.frameworkType = frameworkType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CucumberStepDefinitionCreationContext that)) return false;
    return Objects.equals(psiFile, that.psiFile)
      && Objects.equals(frameworkType, that.frameworkType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(psiFile, frameworkType);
  }
}
