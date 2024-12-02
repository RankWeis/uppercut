package com.rankweis.uppercut.karate.lexer.impl;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.javascript.DialectOptionHolder;
import com.intellij.lang.javascript.JSFlexAdapter;
import com.intellij.lang.javascript.JSLanguageDialect;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.ecmascript6.parsing.jsx.JSXParser;
import com.intellij.lang.javascript.highlighting.JSHighlighter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.psi.formatter.KarateJavascriptFormat;
import java.util.List;
import java.util.function.Consumer;

public class KarateJavascriptExtension implements KarateJavascriptParsingExtensionPoint {


  static class CustomJSLanguageOptionHolder extends DialectOptionHolder {
    private static CustomJSLanguageOptionHolder INSTANCE = new CustomJSLanguageOptionHolder();
    public static DialectOptionHolder getInstance() {
      return INSTANCE;
    }
    public CustomJSLanguageOptionHolder() {
      super("JSX", false, true);
    }
  }

  static class CustomJSLanguageDialect extends JSLanguageDialect {
    private static CustomJSLanguageDialect INSTANCE = new CustomJSLanguageDialect();
    public static JSLanguageDialect getInstance() {
      return INSTANCE;
    }
    public CustomJSLanguageDialect() {
      super("KarateJS", new CustomJSLanguageOptionHolder());
    }
  }

  private static final DialectOptionHolder holder = CustomJSLanguageOptionHolder.getInstance();
  private static final JSLanguageDialect dialect = CustomJSLanguageDialect.getInstance();


  @Override public Lexer getLexer(boolean highlighting) {
    return new JSFlexAdapter(holder, highlighting);
  }

  public SyntaxHighlighterBase getJsSyntaxHighlighter() {
    return new JSHighlighter(holder, false);
  }

  @Override public List<Block> getJsSubBlocks(ASTNode astNode, Alignment alignment) {
    return new KarateJavascriptFormat(dialect, holder).getJsSubBlocks(astNode, alignment);
  }

  @Override public Consumer<PsiBuilder> parseJs() {
    return (builder) -> {
      while (!builder.eof() && builder.getTokenType().getLanguage() == dialect.getBaseLanguage()) {
        if (  builder.getTokenType() == JSTokenTypes.FUNCTION_KEYWORD) {
          new JSXParser(dialect, builder).getFunctionParser().parseFunctionExpression();
        } else {
          new JSXParser(dialect, builder).getStatementParser().parseStatement();
        }
      }
    };
  }

  @Override public boolean isJSLanguage(Language l) {
    return JavascriptLanguage.INSTANCE == l;
  }
}
