package com.rankweis.uppercut.karate.lexer.karatelabs;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharSequenceReader;
import com.rankweis.uppercut.parser.types.KarateJsElementType;
import io.karatelabs.js.Lexer;
import io.karatelabs.js.Token;
import io.karatelabs.js.Type;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateLexerAdapter extends LexerBase {
  Log log = LogFactory.getLog(KarateLexerAdapter.class);

  private Lexer lexer;
  public static final Map<String, Token> STRING_TOKEN_MAP = new ConcurrentHashMap<>();
  public static final Map<Token, IElementType> TOKEN_TO_ELEMENT = new ConcurrentHashMap<>();
  public static final Map<Type, IElementType> TYPE_TO_ELEMENT = new ConcurrentHashMap<>();
  private Token currentToken;
  int endOffset;

  @Override public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    this.lexer = new Lexer(new CharSequenceReader(buffer));
    lexer.reset(buffer, startOffset, endOffset, initialState);
    this.endOffset = endOffset;
  }

  @Override public int getState() {
    return lexer.yystate();
  }

  @Override public @Nullable IElementType getTokenType() {
    if (currentToken == null) {
      return null;
    }
    return getElement(currentToken);
  }

  @Override public int getTokenStart() {
    return lexer.getTokenStart();
  }

  @Override public int getTokenEnd() {
    return lexer.getTokenEnd();
  }

  @Override public void advance() {
    try {
      currentToken = lexer.yylex();
    } catch (IOException | Error e) {
      log.warn("Error in lexer", e);
      throw new LexerError(e.getMessage(), lexer.yytext().toString(), lexer.getTokenStart(), lexer.getTokenEnd());
    }
  }

  @Override public @NotNull CharSequence getBufferSequence() {
    return lexer.yytext();
  }

  @Override public int getBufferEnd() {
    return endOffset;
  }

  public static IElementType getElement(Token t) {
    return TOKEN_TO_ELEMENT.computeIfAbsent(STRING_TOKEN_MAP.computeIfAbsent(t.name(), k -> t),
      token -> new KarateJsElementType(t.name()));
  }

  public static List<IElementType> getElements(Token... types) {
    return Arrays.stream(types).map(t ->
      TOKEN_TO_ELEMENT.computeIfAbsent(STRING_TOKEN_MAP.computeIfAbsent(t.name(), k -> t),
        token -> new KarateJsElementType(t.name()))).toList();
  }

  public static IElementType getType(Type t) {
    return TYPE_TO_ELEMENT.computeIfAbsent(t, type -> new KarateJsElementType(t.name()));
  }

  public static List<IElementType> getTypes(Type... types) {
    return Arrays.stream(types).map(t -> TYPE_TO_ELEMENT.computeIfAbsent(t, type -> new KarateJsElementType(t.name())))
      .toList();
  }

  public static List<IElementType> getTypes(Token... types) {
    return Arrays.stream(types).map(t -> TOKEN_TO_ELEMENT.computeIfAbsent(t, type -> new KarateJsElementType(t.name())))
      .toList();
  }

  public static IElementType getToken(IElementType e) {
    return TOKEN_TO_ELEMENT.get(STRING_TOKEN_MAP.get(e.toString()));
  }
}
