// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.rankweis.uppercut.karate.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.rankweis.uppercut.karate.psi.KarateJsFileType;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import com.rankweis.uppercut.karate.psi.PlainKarateKeywordProvider;
import org.jetbrains.annotations.NotNull;


public class KarateJsFileImpl extends PsiFileBase {

  private final PlainKarateKeywordProvider keywordProvider = new PlainKarateKeywordProvider();

  public KarateJsFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, KarateLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return KarateJsFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "KarateJsFile:" + getName();
  }

  public static String getDefaultLocale() {
    return "en";
  }

  @Override
  public PsiElement findElementAt(int offset) {
    PsiElement result = super.findElementAt(offset);
    if (result == null && offset == getTextLength()) {
      final PsiElement last = getLastChild();
      result = last != null ? last.getLastChild() : null;
    }
    return result;
  }
}
