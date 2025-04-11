package io.karatelabs.js;

import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.TEXT_BLOCK;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.rankweis.uppercut.karate.psi.KarateJsLanguage;
import com.rankweis.uppercut.karate.psi.parser.KarateJsParserDefinition;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KarateJsParser implements PsiParser {

  Logger logger = LoggerFactory.getLogger(KarateJsParser.class);
  KarateJsParserDefinition karateJsParserDefinition = new KarateJsParserDefinition();

  @Override public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder b) {
    return doParse(root, b).getTreeBuilt();
  }

  public PsiBuilder doParse(IElementType root, PsiBuilder b) {
    long start = System.currentTimeMillis();
    StringBuilder sb = new StringBuilder();
    PsiBuilder.Marker markRoot = b.mark();
    PsiBuilder.Marker mark = b.mark();
    boolean hasError = false;
    do {
      sb.append(b.getTokenText());
      if (b.lookAhead(1) == TokenType.ERROR_ELEMENT) {
        hasError = true;
        PsiBuilder.Marker errorMark = b.mark();
        b.rawAdvanceLexer(1);
        errorMark.error("Invalid javaScript");
        continue;
      }
      b.rawAdvanceLexer(1);
    } while (!b.eof() && (Objects.requireNonNull(b.getTokenType()).getLanguage() == KarateJsLanguage.INSTANCE
      || b.getTokenType().getLanguage() == Language.ANY));

    if (hasError) {
      mark.done(TEXT_BLOCK);
      markRoot.done(root);
      return b;
    }
    int currentOffset = b.getCurrentOffset();
    mark.rollbackTo();
    try {
      new Parser(new Source(sb.toString()), b, currentOffset).parse();
    } catch (Exception e) {
      logger.warn("Error parsing", e);
      int errorOffset = b.getCurrentOffset();
      markRoot.rollbackTo();
      PsiBuilder.Marker error = b.mark();
      while (!b.eof() && b.getCurrentOffset() < currentOffset) {
        if (b.getCurrentOffset() == errorOffset) {
          b.mark().error(("Invalid javaScript - " + e.getMessage()));
        }
        b.advanceLexer();
      }
      error.done(TEXT_BLOCK);
      return b;
    }
    markRoot.done(root);
    logger.debug("Parsed in {}ms", (System.currentTimeMillis() - start));
    return b;
  }
}
