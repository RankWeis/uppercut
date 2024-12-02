// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import static com.rankweis.uppercut.karate.psi.GherkinElementTypes.JSON;

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
import com.rankweis.uppercut.karate.lexer.GherkinLexer;
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

public final class GherkinParserDefinition implements ParserDefinition {
  public static final IFileElementType GHERKIN_FILE = new IFileElementType(KarateLanguage.INSTANCE);

  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new GherkinLexer(JsonGherkinKeywordProvider.getKeywordProvider(true));
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new GherkinParser(this);
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return GHERKIN_FILE;
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
    if (node.getElementType() == GherkinElementTypes.FEATURE) return new GherkinFeatureImpl(node);
    if (node.getElementType() == GherkinElementTypes.FEATURE_HEADER) return new GherkinFeatureHeaderImpl(node);
    if (node.getElementType() == GherkinElementTypes.SCENARIO) return new GherkinScenarioImpl(node);
    if (node.getElementType() == GherkinElementTypes.STEP) return new GherkinStepImpl(node);
    if (node.getElementType() == GherkinElementTypes.SCENARIO_OUTLINE) return new GherkinScenarioOutlineImpl(node);
    if (node.getElementType() == GherkinElementTypes.RULE) return new GherkinRuleImpl(node);
    if (node.getElementType() == GherkinElementTypes.EXAMPLES_BLOCK) return new GherkinExamplesBlockImpl(node);
    if (node.getElementType() == GherkinElementTypes.TABLE) return new GherkinTableImpl(node);
    if (node.getElementType() == GherkinElementTypes.TABLE_ROW) return new GherkinTableRowImpl(node);
    if (node.getElementType() == GherkinElementTypes.TABLE_CELL) return new GherkinTableCellImpl(node);
    if (node.getElementType() == GherkinElementTypes.TABLE_HEADER_ROW) return new GherkinTableHeaderRowImpl(node);
    if (node.getElementType() == GherkinElementTypes.TAG) return new GherkinTagImpl(node);
    if (node.getElementType() == GherkinElementTypes.STEP_PARAMETER) return new GherkinStepParameterImpl(node);
    if (node.getElementType() == GherkinElementTypes.PYSTRING) return new GherkinPystringImpl(node);
    if (node.getElementType() == GherkinElementTypes.DECLARATION
      || node.getElementType() == GherkinElementTypes.VARIABLE) {
      return new KarateDeclaration(node);
    }
    if (node.getElementType() == GherkinElementTypes.PAREN_ELEMENT) {
      return new KarateParenElement(node);
    }
    if ( node.getElementType() == GherkinElementTypes.JAVASCRIPT) {
      return new KarateEmbeddedJavascriptElement(node);
    } else if (node.getElementType() == JSON) {
      return new KarateEmbeddedJsonElement(node);
    } else if (node.getElementType() == GherkinElementTypes.XML) {
      return new KarateEmbeddedJavascriptElement(node);
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
