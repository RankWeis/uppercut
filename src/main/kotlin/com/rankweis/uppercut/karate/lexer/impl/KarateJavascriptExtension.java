package com.rankweis.uppercut.karate.lexer.impl;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.javascript.DialectOptionHolder;
import com.intellij.lang.javascript.JSFlexAdapter;
import com.intellij.lang.javascript.JSLanguageDialect;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.ecmascript6.parsing.jsx.JSXParser;
import com.intellij.lang.javascript.highlighting.JSHighlighter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.psi.formatter.KarateJavascriptBlock;
import java.util.List;
import java.util.function.Consumer;

public class KarateJavascriptExtension implements KarateJavascriptParsingExtensionPoint {

  private Lexer lexer = null;
  private static final DialectOptionHolder holder = JavascriptLanguage.INSTANCE.getOptionHolder();
  private static final JSLanguageDialect language = JavascriptLanguage.INSTANCE;

  @Override public Lexer getLexer(boolean highlighting) {
    return new JSFlexAdapter(holder, highlighting);
  }

  public SyntaxHighlighterBase getJsSyntaxHighlighter() {
    return new JSHighlighter(holder, false);
  }

  @Override public List<Block> getJsSubBlocks(ASTNode astNode, Alignment alignment) {
    return new KarateJavascriptBlock(language, holder).getJsSubBlocks(astNode, alignment);
  }

  @Override public Consumer<PsiBuilder> parseJs() {
    return (builder) -> {
      new JSXParser(language, builder)
        .getStatementParser()
        .parseStatement();
    };
  }

  @Override public boolean isJSLanguage(Language l) {
    return JavascriptLanguage.INSTANCE == l;
  }
}
