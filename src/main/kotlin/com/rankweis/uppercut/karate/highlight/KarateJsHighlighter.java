package com.rankweis.uppercut.karate.highlight;

import static io.karatelabs.js.Token.AMP;
import static io.karatelabs.js.Token.AMP_AMP;
import static io.karatelabs.js.Token.AMP_AMP_EQ;
import static io.karatelabs.js.Token.AMP_EQ;
import static io.karatelabs.js.Token.BACKTICK;
import static io.karatelabs.js.Token.BREAK;
import static io.karatelabs.js.Token.B_COMMENT;
import static io.karatelabs.js.Token.CARET;
import static io.karatelabs.js.Token.CARET_EQ;
import static io.karatelabs.js.Token.CASE;
import static io.karatelabs.js.Token.CATCH;
import static io.karatelabs.js.Token.COLON;
import static io.karatelabs.js.Token.COMMA;
import static io.karatelabs.js.Token.CONST;
import static io.karatelabs.js.Token.DEFAULT;
import static io.karatelabs.js.Token.DELETE;
import static io.karatelabs.js.Token.DO;
import static io.karatelabs.js.Token.DOLLAR_L_CURLY;
import static io.karatelabs.js.Token.DOT;
import static io.karatelabs.js.Token.DOT_DOT_DOT;
import static io.karatelabs.js.Token.D_STRING;
import static io.karatelabs.js.Token.ELSE;
import static io.karatelabs.js.Token.EQ;
import static io.karatelabs.js.Token.EQ_EQ;
import static io.karatelabs.js.Token.EQ_EQ_EQ;
import static io.karatelabs.js.Token.EQ_GT;
import static io.karatelabs.js.Token.FALSE;
import static io.karatelabs.js.Token.FINALLY;
import static io.karatelabs.js.Token.FOR;
import static io.karatelabs.js.Token.FUNCTION;
import static io.karatelabs.js.Token.GT;
import static io.karatelabs.js.Token.GT_EQ;
import static io.karatelabs.js.Token.IDENT;
import static io.karatelabs.js.Token.IF;
import static io.karatelabs.js.Token.IN;
import static io.karatelabs.js.Token.INSTANCEOF;
import static io.karatelabs.js.Token.LET;
import static io.karatelabs.js.Token.LT;
import static io.karatelabs.js.Token.LT_EQ;
import static io.karatelabs.js.Token.L_BRACKET;
import static io.karatelabs.js.Token.L_COMMENT;
import static io.karatelabs.js.Token.L_CURLY;
import static io.karatelabs.js.Token.L_PAREN;
import static io.karatelabs.js.Token.MINUS;
import static io.karatelabs.js.Token.MINUS_EQ;
import static io.karatelabs.js.Token.MINUS_MINUS;
import static io.karatelabs.js.Token.NEW;
import static io.karatelabs.js.Token.NOT;
import static io.karatelabs.js.Token.NOT_EQ;
import static io.karatelabs.js.Token.NOT_EQ_EQ;
import static io.karatelabs.js.Token.NULL;
import static io.karatelabs.js.Token.NUMBER;
import static io.karatelabs.js.Token.OF;
import static io.karatelabs.js.Token.PERCENT;
import static io.karatelabs.js.Token.PERCENT_EQ;
import static io.karatelabs.js.Token.PIPE;
import static io.karatelabs.js.Token.PIPE_EQ;
import static io.karatelabs.js.Token.PIPE_PIPE;
import static io.karatelabs.js.Token.PIPE_PIPE_EQ;
import static io.karatelabs.js.Token.PLUS;
import static io.karatelabs.js.Token.PLUS_EQ;
import static io.karatelabs.js.Token.PLUS_PLUS;
import static io.karatelabs.js.Token.QUES;
import static io.karatelabs.js.Token.QUES_QUES;
import static io.karatelabs.js.Token.RETURN;
import static io.karatelabs.js.Token.R_BRACKET;
import static io.karatelabs.js.Token.R_CURLY;
import static io.karatelabs.js.Token.R_PAREN;
import static io.karatelabs.js.Token.SEMI;
import static io.karatelabs.js.Token.SLASH;
import static io.karatelabs.js.Token.SLASH_EQ;
import static io.karatelabs.js.Token.STAR;
import static io.karatelabs.js.Token.STAR_EQ;
import static io.karatelabs.js.Token.STAR_STAR;
import static io.karatelabs.js.Token.STAR_STAR_EQ;
import static io.karatelabs.js.Token.SWITCH;
import static io.karatelabs.js.Token.S_STRING;
import static io.karatelabs.js.Token.THROW;
import static io.karatelabs.js.Token.TILDE;
import static io.karatelabs.js.Token.TRUE;
import static io.karatelabs.js.Token.TRY;
import static io.karatelabs.js.Token.TYPEOF;
import static io.karatelabs.js.Token.T_STRING;
import static io.karatelabs.js.Token.VAR;
import static io.karatelabs.js.Token.WHILE;
import static io.karatelabs.js.Token.WS;
import static io.karatelabs.js.Token.WS_LF;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter;
import io.karatelabs.js.Token;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class KarateJsHighlighter extends SyntaxHighlighterBase {

  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<>();

  static {
    register(HighlighterColors.NO_HIGHLIGHTING, WS_LF, WS);
    register(DefaultLanguageHighlighterColors.STRING, BACKTICK, S_STRING, D_STRING, T_STRING);
    register(DefaultLanguageHighlighterColors.BRACES, L_CURLY, R_CURLY);
    register(DefaultLanguageHighlighterColors.BRACKETS, L_BRACKET, R_BRACKET);
    register(DefaultLanguageHighlighterColors.PARENTHESES, L_PAREN, R_PAREN);
    register(HighlighterColors.TEXT, COMMA, COLON, SEMI, GT, LT, LT_EQ, GT_EQ,
      DOT_DOT_DOT, DOT, EQ_EQ_EQ, EQ_EQ, EQ, EQ_GT, NOT_EQ_EQ, NOT_EQ, NOT, PIPE_PIPE_EQ, PIPE_PIPE, PIPE_EQ, PIPE,
      AMP_AMP_EQ, AMP_AMP, AMP_EQ, AMP, CARET_EQ, CARET, QUES_QUES, QUES, PLUS_PLUS, PLUS_EQ, PLUS, MINUS_MINUS,
      MINUS_EQ, MINUS, STAR_STAR_EQ, STAR_STAR, STAR_EQ, STAR, SLASH_EQ, SLASH, PERCENT_EQ, PERCENT, TILDE,
      PLUS_PLUS, PLUS_EQ, PLUS, MINUS_MINUS, MINUS_EQ, MINUS, STAR_STAR_EQ, STAR_STAR, STAR_EQ, STAR, SLASH_EQ,
      SLASH, PERCENT_EQ, PERCENT, TILDE);
    register(DefaultLanguageHighlighterColors.KEYWORD, NULL, TRUE, FALSE, RETURN, TRY, CATCH, FINALLY, THROW,
      NEW, VAR, LET, CONST, IF, ELSE, TYPEOF, INSTANCEOF, DELETE, FOR, IN, OF, DO, WHILE, SWITCH, CASE, DEFAULT, BREAK);
    register(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION, FUNCTION);
    register(DefaultLanguageHighlighterColors.LINE_COMMENT, L_COMMENT);
    register(DefaultLanguageHighlighterColors.BLOCK_COMMENT, B_COMMENT);
    register(DefaultLanguageHighlighterColors.NUMBER, NUMBER);
    register(DefaultLanguageHighlighterColors.IDENTIFIER, IDENT, DOLLAR_L_CURLY);
    register(HighlighterColors.NO_HIGHLIGHTING, WS_LF, WS);
  }

  public KarateJsHighlighter() {
  }

  @Override public @NotNull Lexer getHighlightingLexer() {
    return new KarateLexerAdapter();
  }

  @Override public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    TextAttributesKey textAttributesKey = ATTRIBUTES.get(KarateLexerAdapter.getToken(tokenType));
    if (textAttributesKey == null) {
      return TextAttributesKey.EMPTY_ARRAY;
    }
    return SyntaxHighlighterBase.pack(textAttributesKey);
  }

  private static void register(TextAttributesKey key, Token... token) {
    Arrays.stream(token).forEach(
      t -> ATTRIBUTES.put(KarateLexerAdapter.getElement(t),
        TextAttributesKey.createTextAttributesKey("KARATE_JS_" + t.name(), key)));
  }
}
