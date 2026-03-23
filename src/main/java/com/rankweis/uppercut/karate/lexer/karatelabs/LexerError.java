package com.rankweis.uppercut.karate.lexer.karatelabs;

public class LexerError extends RuntimeException {

  private final String tokenText;
  private final int tokenStart;
  private final int tokenEnd;

  public LexerError(String message, String tokenText, int tokenStart, int tokenEnd) {
    super(message);
    this.tokenText = tokenText;
    this.tokenStart = tokenStart;
    this.tokenEnd = tokenEnd;
  }
}
