package io.karatelabs.js;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
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
    return List.of();
  }

  @Override public Consumer<PsiBuilder> parseJs() {
    return b -> {
      StringBuilder sb = new StringBuilder();
      PsiBuilder.Marker mark = b.mark();
      do {
        sb.append(b.getTokenText());
        b.advanceLexer();
      } while (!b.eof() && b.getTokenType().getLanguage() == KarateJsLanguage.INSTANCE);
      if (!sb.toString().trim().isEmpty()) {
        Node parsedNode = new Parser(new Source(sb.toString())).parse();
        mark.rollbackTo();
        doParseRecursive(b, parsedNode, 0);
      }
    };
  }

  private StringBuilder doParseRecursive(PsiBuilder b, Node node, int level) {
    PsiBuilder.Marker mark = b.mark();
    StringBuilder sb = new StringBuilder();

    node.children.forEach(n -> {
      if (!sb.isEmpty()) {
        sb.append(" ");
      }
      sb.append(doParseRecursive(b, n, level + 1).toString().strip());
    });
    while ((!node.toString().equals(sb.toString().trim())) && !b.eof()) {
      b.advanceLexer();
      if (b.getTokenType().getLanguage() != KarateJsLanguage.INSTANCE || sb.length() > node.toString().length()) {
        break;
      }
      sb.append(b.getTokenText());
    }
    b.advanceLexer();
    mark.done(KarateLexerAdapter.getType(node.type));
    return sb;
  }

  @Override public boolean isJSLanguage(Language l) {
    return KarateJsLanguage.INSTANCE == l;
  }
}
