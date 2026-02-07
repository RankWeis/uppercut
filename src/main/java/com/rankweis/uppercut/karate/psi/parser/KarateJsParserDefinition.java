package com.rankweis.uppercut.karate.psi.parser;

import static com.rankweis.uppercut.karate.psi.UppercutParserDefinition.KARATE_FILE;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter;
import com.rankweis.uppercut.karate.psi.KarateEmbeddedJavascriptElement;
import com.rankweis.uppercut.karate.psi.impl.KarateJsFileImpl;
import io.karatelabs.js.KarateJsParser;
import io.karatelabs.js.Token;
import org.jetbrains.annotations.NotNull;

public class KarateJsParserDefinition implements ParserDefinition {

  @Override public @NotNull Lexer createLexer(Project project) {
    return new KarateLexerAdapter();
  }

  @Override public @NotNull PsiParser createParser(Project project) {
    return new KarateJsParser();
  }

  @Override public @NotNull IFileElementType getFileNodeType() {
    return KARATE_FILE;
  }

  @Override public @NotNull TokenSet getCommentTokens() {
    return TokenSet.create(KarateLexerAdapter.getElement(Token.L_COMMENT),
      KarateLexerAdapter.getElement(Token.B_COMMENT));
  }

  @Override public @NotNull TokenSet getStringLiteralElements() {
    return TokenSet.create(KarateLexerAdapter.getElement(Token.T_STRING),
      KarateLexerAdapter.getElement(Token.S_STRING), KarateLexerAdapter.getElement(Token.D_STRING));
  }

  @Override public @NotNull PsiElement createElement(ASTNode node) {
    return new KarateEmbeddedJavascriptElement(node);
  }

  @Override public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new KarateJsFileImpl(viewProvider);
  }

  @Override public @NotNull TokenSet getWhitespaceTokens() {
    return TokenSet.create(KarateLexerAdapter.getElement(Token.WS), KarateLexerAdapter.getElement(Token.WS_LF));
  }
}
