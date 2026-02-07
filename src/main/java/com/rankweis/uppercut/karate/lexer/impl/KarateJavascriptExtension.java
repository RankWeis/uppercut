package com.rankweis.uppercut.karate.lexer.impl;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.ecmascript6.parsing.ES6StatementParser;
import com.intellij.lang.javascript.DialectOptionHolder;
import com.intellij.lang.javascript.JSFlexAdapter;
import com.intellij.lang.javascript.JSLanguageDialect;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.ecmascript6.parsing.jsx.JSXFunctionParser;
import com.intellij.lang.javascript.ecmascript6.parsing.jsx.JSXParser;
import com.intellij.lang.javascript.highlighting.JSHighlighter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.psi.formatter.KarateJavascriptFormat;
import java.util.List;
import java.util.function.Consumer;

public class KarateJavascriptExtension implements KarateJavascriptParsingExtensionPoint {


  static class CustomJsLanguageOptionHolder extends DialectOptionHolder {

    private static final CustomJsLanguageOptionHolder INSTANCE = new CustomJsLanguageOptionHolder();

    public static DialectOptionHolder getInstance() {
      return INSTANCE;
    }

    public CustomJsLanguageOptionHolder() {
      super("JSX", false, true);
    }
  }

  static class CustomJsLanguageDialect extends JSLanguageDialect {

    private static final CustomJsLanguageDialect INSTANCE = new CustomJsLanguageDialect();

    public static JSLanguageDialect getInstance() {
      return INSTANCE;
    }

    public CustomJsLanguageDialect() {
      super("KarateJS", new CustomJsLanguageOptionHolder());
    }
  }

  private static final DialectOptionHolder holder = CustomJsLanguageOptionHolder.getInstance();
  private static final JSLanguageDialect dialect = CustomJsLanguageDialect.getInstance();


  @Override public Lexer getLexer(boolean highlighting) {
    return new JSFlexAdapter(holder, highlighting);
  }

  public SyntaxHighlighterBase getJsSyntaxHighlighter() {
    return new JSHighlighter(holder);
  }

  @Override public List<Block> getJsSubBlocks(ASTNode astNode, Alignment alignment) {
    return new KarateJavascriptFormat(dialect).getJsSubBlocks(astNode, alignment);
  }

  @Override public Consumer<PsiBuilder> parseJs() {
    return (builder) -> {
      while (!builder.eof() && builder.getTokenType() != null
        && builder.getTokenType().getLanguage() == dialect.getBaseLanguage()) {
        if (builder.getTokenType() == JSTokenTypes.FUNCTION_KEYWORD) {
          new JSXFunctionParser<>(new JSXParser(dialect, builder))
            .parseFunctionExpression();
        } else {
          new ES6StatementParser<>(new JSXParser(dialect, builder))
            .parseStatement();
        }
      }
    };
  }

  @Override public boolean isJsLanguage(Language l) {
    return JavascriptLanguage.INSTANCE == l;
  }
}
