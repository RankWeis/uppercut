package io.karatelabs.js;

import static com.rankweis.uppercut.karate.psi.GherkinElementTypes.TEXT_BLOCK;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter;
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
    if (!sb.toString().trim().isEmpty()) {
      Node parsedNode;
      try {
        parsedNode = new Parser(new Source(sb.toString())).parse();
      } catch (Exception e) {
        mark.error("Invalid javaScript");
        while (!b.eof() && Objects.requireNonNull(b.getTokenType()).getLanguage() == KarateJsLanguage.INSTANCE) {
          b.advanceLexer();
        }
        markRoot.done(root);
        return b;
      }
      mark.rollbackTo();
      doParseRecursive(b, parsedNode, 0);
      // Get rid of any lingering whitespace.
      while (!b.eof() && Objects.requireNonNull(b.getTokenType()).getLanguage() == KarateJsLanguage.INSTANCE) {
        b.advanceLexer();
      }
      markRoot.done(root);
    }
    logger.debug("Parsed in {}ms", (System.currentTimeMillis() - start));
    return b;
  }

  private StringBuilder doParseRecursive(PsiBuilder b, Node node, int level) {
    while (karateJsParserDefinition.getWhitespaceTokens().contains(Objects.requireNonNull(b.getTokenType()))) {
      b.advanceLexer();
    }
    PsiBuilder.Marker mark = advanceComments(b, b.mark());
    StringBuilder sb = new StringBuilder();

    if (node.isChunk()) {
      while (b.isWhitespaceOrComment(Objects.requireNonNull(b.getTokenType()))
        || karateJsParserDefinition.getWhitespaceTokens()
        .contains(b.getTokenType()) || karateJsParserDefinition.getCommentTokens().contains(b.getTokenType())) {
        b.advanceLexer();
      }
      sb.append(Objects.requireNonNull(b.getTokenText()).strip());
      b.advanceLexer();
      mark.drop();
      //      mark.done(KarateLexerAdapter.getType(node.type));
      return sb;
    }
    node.children.forEach(n -> {
      if (!sb.isEmpty()) {
        sb.append(" ");
      }
      sb.append(doParseRecursive(b, n, level + 1).toString().strip());
    });
    PsiBuilder.Marker lastElem = b.mark();
    while (((!node.toString().equals(sb.toString().trim())) && !b.eof())) {
      lastElem.drop();
      lastElem = b.mark();
      b.advanceLexer();
      if (karateJsParserDefinition.getWhitespaceTokens()
        .contains(b.getTokenType())) {
        continue;
      }
      if (b.getTokenType().getLanguage() != KarateJsLanguage.INSTANCE || sb.length() > node.toString().length()) {
        break;
      }
      if (!sb.isEmpty()) {
        sb.append(" ");
      }
      sb.append(Objects.requireNonNull(b.getTokenText()).strip());
    }
    mark.done(KarateLexerAdapter.getType(node.type));
    advanceComments(b, lastElem).drop();
    return sb;
  }

  private PsiBuilder.Marker advanceComments(PsiBuilder b, PsiBuilder.Marker lastElem) {
    while (karateJsParserDefinition.getCommentTokens().contains(b.getTokenType())) {
      IElementType type = b.getTokenType();
      b.advanceLexer();
      lastElem.done(type);
      lastElem = b.mark();
    }
    return lastElem;
  }
}
