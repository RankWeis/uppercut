// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveVisitor;
import org.jetbrains.annotations.NotNull;


public class GherkinRecursiveElementVisitor extends GherkinElementVisitor implements PsiRecursiveVisitor {
  @Override
  public void visitElement(@NotNull PsiElement element) {
    element.acceptChildren(this);
  }
}
