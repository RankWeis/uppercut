package com.rankweis.uppercut.karate.framework;

import com.rankweis.uppercut.karate.KarateIcons;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.lang.Language;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.IncorrectOperationException;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateTestFramework implements TestFramework {

  @Override public @NotNull @NlsSafe String getName() {
    return "Karate";
  }

  @Override public @NotNull Icon getIcon() {
    return KarateIcons.FILE;
  }

  @Override public boolean isLibraryAttached(@NotNull Module module) {
    return false;
  }

  @Override public @Nullable String getLibraryPath() {
    return "";
  }

  @Override public @Nullable String getDefaultSuperClass() {
    return "";
  }

  @Override public boolean isTestClass(@NotNull PsiElement clazz) {
    return clazz.getContainingFile().getFileType().getName().equals("feature");
  }

  @Override public boolean isPotentialTestClass(@NotNull PsiElement clazz) {
    return true;
  }

  @Override public @Nullable PsiElement findSetUpMethod(@NotNull PsiElement clazz) {
    return null;
  }

  @Override public @Nullable PsiElement findTearDownMethod(@NotNull PsiElement clazz) {
    return null;
  }

  @Override public @Nullable PsiElement findOrCreateSetUpMethod(@NotNull PsiElement clazz)
    throws IncorrectOperationException {
    return null;
  }

  @Override public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return null;
  }

  @Override public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return null;
  }

  @Override public @NotNull FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return null;
  }

  @Override public boolean isIgnoredMethod(PsiElement element) {
    return false;
  }

  @Override public boolean isTestMethod(PsiElement element) {
    return false;
  }

  @Override public @NotNull Language getLanguage() {
    return null;
  }
}
