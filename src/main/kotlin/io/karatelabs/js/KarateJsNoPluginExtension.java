package io.karatelabs.js;

import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.JAVASCRIPT;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.rankweis.uppercut.karate.format.KarateJsFormatter;
import com.rankweis.uppercut.karate.highlight.KarateJsHighlighter;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter;
import com.rankweis.uppercut.karate.psi.KarateJsLanguage;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KarateJsNoPluginExtension implements KarateJavascriptParsingExtensionPoint {

  Logger logger = LoggerFactory.getLogger(KarateJsNoPluginExtension.class);

  @Override public Lexer getLexer(boolean highlighting) {
    return new KarateLexerAdapter();
  }

  public SyntaxHighlighterBase getJsSyntaxHighlighter() {
    return new KarateJsHighlighter();
  }

  @Override public List<Block> getJsSubBlocks(ASTNode astNode, Alignment alignment) {
    return new KarateJsFormatter().getJsSubBlocks(astNode, alignment);
  }

  @Override public Consumer<PsiBuilder> parseJs() {
    {
      return b -> new KarateJsParser().doParse(JAVASCRIPT, b);
    }
  }

  @Override public boolean isJsLanguage(Language l) {
    return KarateJsLanguage.INSTANCE == l;
  }
}
