// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.spellchecker;

import static com.rankweis.uppercut.karate.psi.PlainKarateKeywordProvider.DEFAULT_KEYWORD_TABLE;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.rankweis.uppercut.karate.psi.KarateElementType;
import org.jetbrains.annotations.NotNull;

public final class GherkinSpellcheckerStrategy extends SpellcheckingStrategy implements DumbAware {
  @NotNull
  @Override
  public Tokenizer getTokenizer(final PsiElement element) {
    if (element instanceof LeafElement) {
      final ASTNode node = element.getNode();
      if (node != null && (node.getElementType() instanceof KarateElementType k)) {
        if (DEFAULT_KEYWORD_TABLE.tableContainsKeyword(k, node.getText())) {
          return EMPTY_TOKENIZER;
        }
        return TEXT_TOKENIZER;
      }
    }
    return super.getTokenizer(element);
  }

}