package com.rankweis.uppercut.karate.lexer;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import java.util.List;
import java.util.function.Consumer;

public interface KarateJavascriptParsingExtensionPoint {

  ExtensionPointName<KarateJavascriptParsingExtensionPoint> EP_NAME =
    ExtensionPointName.create("com.rankweis.uppercut.karateJavascriptParsingExtensionPoint");

  Lexer getLexer(boolean highlighting);

  List<Block> getJsSubBlocks(ASTNode astNode, Alignment alignment);

  SyntaxHighlighterBase getJsSyntaxHighlighter();

  Consumer<PsiBuilder> parseJs();

  boolean isJsLanguage(Language l);
}
