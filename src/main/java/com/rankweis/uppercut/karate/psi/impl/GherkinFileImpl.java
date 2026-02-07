// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.rankweis.uppercut.karate.lexer.UppercutLexer;
import com.rankweis.uppercut.karate.psi.GherkinFeature;
import com.rankweis.uppercut.karate.psi.GherkinFile;
import com.rankweis.uppercut.karate.psi.GherkinFileType;
import com.rankweis.uppercut.karate.psi.GherkinKeywordProvider;
import com.rankweis.uppercut.karate.psi.GherkinKeywordTable;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import com.rankweis.uppercut.karate.psi.PlainKarateKeywordProvider;
import com.rankweis.uppercut.karate.psi.i18n.JsonGherkinKeywordProvider;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;


public class GherkinFileImpl extends PsiFileBase implements GherkinFile {
  private final PlainKarateKeywordProvider keywordProvider = new PlainKarateKeywordProvider();
  public GherkinFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, KarateLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return GherkinFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "KarateFile:" + getName();
  }

  @Override
  public List<String> getStepKeywords() {
    final GherkinKeywordProvider provider = JsonGherkinKeywordProvider.getKeywordProvider(this);

    // find language comment
    final String language = getLocaleLanguage();

    // step keywords
    final GherkinKeywordTable table = provider.getKeywordsTable(language);

    return new ArrayList<>(table.getStepKeywords());
  }

  @Override public List<String> getActionKeywords() {
    return new ArrayList<>(keywordProvider.getActionKeywords());
  }

  @Override
  public String getLocaleLanguage() {
    final ASTNode node = getNode();

    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      if (child.getElementType() == KarateTokenTypes.COMMENT) {
        final String text = child.getText().substring(1).trim();

        final String lang = UppercutLexer.fetchLocationLanguage(text);
        if (lang != null) {
          return lang;
        }
      } else {
        if (child.getElementType() != TokenType.WHITE_SPACE) {
          break;
        }
      }
      child = child.getTreeNext();
    }
    return getDefaultLocale();
  }

  @Override
  public GherkinFeature[] getFeatures() {
    return findChildrenByClass(GherkinFeature.class);
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
