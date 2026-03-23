// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.rankweis.uppercut.karate;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MapParameterTypeManager implements ParameterTypeManager {

  public static final MapParameterTypeManager DEFAULT = new MapParameterTypeManager(
    CucumberUtil.STANDARD_PARAMETER_TYPES);

  private final Map<String, String> myParameterTypes;
  private final Map<String, SmartPsiElementPointer<PsiElement>> myParameterTypeDeclarations;

  public MapParameterTypeManager(Map<String, String> parameterTypes) {
    this(parameterTypes, null);
  }

  public MapParameterTypeManager(Map<String, String> parameterTypes,
    Map<String, SmartPsiElementPointer<PsiElement>> parameterTypeDeclarations) {
    myParameterTypes = parameterTypes;
    myParameterTypeDeclarations = parameterTypeDeclarations;
  }

  @Nullable
  @Override
  public String getParameterTypeValue(@NotNull String name) {
    return myParameterTypes.get(name);
  }

  @Override
  public PsiElement getParameterTypeDeclaration(@NotNull String name) {
    if (myParameterTypeDeclarations == null) {
      return null;
    }
    SmartPsiElementPointer<PsiElement> smartPointer = myParameterTypeDeclarations.get(name);
    return smartPointer != null ? smartPointer.getElement() : null;
  }
}
