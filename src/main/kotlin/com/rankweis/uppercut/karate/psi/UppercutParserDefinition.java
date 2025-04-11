// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.rankweis.uppercut.karate.psi;

import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.JSON;
import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.TEXT_BLOCK;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.rankweis.uppercut.karate.lexer.UppercutLexer;
import com.rankweis.uppercut.karate.psi.i18n.JsonGherkinKeywordProvider;
import com.rankweis.uppercut.karate.psi.impl.GherkinExamplesBlockImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinFeatureHeaderImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinFeatureImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinFileImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinPystringImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinRuleImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinScenarioImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinScenarioOutlineImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinStepImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinStepParameterImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinTableHeaderRowImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinTableImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinTableRowImpl;
import com.rankweis.uppercut.karate.psi.impl.GherkinTagImpl;
import org.jetbrains.annotations.NotNull;

public final class UppercutParserDefinition implements ParserDefinition {

  public static final IFileElementType KARATE_FILE = new IFileElementType(KarateLanguage.INSTANCE);

  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new UppercutLexer(JsonGherkinKeywordProvider.getKeywordProvider(true));
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new UppercutParser();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return KARATE_FILE;
  }

  @Override
  @NotNull
  public TokenSet getCommentTokens() {
    return KarateTokenTypes.COMMENTS;
  }

  @Override
  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @Override
  @NotNull
  public PsiElement createElement(ASTNode node) {
    if (node.getElementType() == UppercutElementTypes.FEATURE) {
      return new GherkinFeatureImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.FEATURE_HEADER) {
      return new GherkinFeatureHeaderImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.SCENARIO) {
      return new GherkinScenarioImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.STEP) {
      return new GherkinStepImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.SCENARIO_OUTLINE) {
      return new GherkinScenarioOutlineImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.RULE) {
      return new GherkinRuleImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.EXAMPLES_BLOCK) {
      return new GherkinExamplesBlockImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.TABLE) {
      return new GherkinTableImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.TABLE_ROW) {
      return new GherkinTableRowImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.TABLE_CELL) {
      return new GherkinTableCellImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.TABLE_HEADER_ROW) {
      return new GherkinTableHeaderRowImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.TAG) {
      return new GherkinTagImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.STEP_PARAMETER) {
      return new GherkinStepParameterImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.PYSTRING) {
      return new GherkinPystringImpl(node);
    }
    if (node.getElementType() == UppercutElementTypes.DECLARATION
      || node.getElementType() == UppercutElementTypes.VARIABLE) {
      return new KarateDeclaration(node);
    }
    if (node.getElementType() == UppercutElementTypes.PAREN_ELEMENT) {
      return new KarateParenElement(node);
    }
    if (node.getElementType() == UppercutElementTypes.JAVASCRIPT) {
      return new KarateEmbeddedJavascriptElement(node);
    } else if (node.getElementType() == JSON) {
      return new KarateEmbeddedJsonElement(node);
    } else if (node.getElementType() == UppercutElementTypes.XML) {
      return new KarateEmbeddedJavascriptElement(node);
    } else if (node.getElementType() == TEXT_BLOCK) {
      return new GherkinPystringImpl(node);
    }
    return PsiUtilCore.NULL_PSI_ELEMENT;
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new GherkinFileImpl(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    // Line break between line comment and other elements
    final IElementType leftElementType = left.getElementType();
    if (leftElementType == KarateTokenTypes.COMMENT) {
      return SpaceRequirements.MUST_LINE_BREAK;
    }
    if (right.getElementType() == KarateTokenTypes.EXAMPLES_KEYWORD) {
      return SpaceRequirements.MUST_LINE_BREAK;
    }
    return SpaceRequirements.MAY;
  }
}
