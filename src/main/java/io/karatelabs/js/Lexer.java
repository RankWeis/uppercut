/*
 * The MIT License
 *
 * Copyright 2024, 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.js;

import java.io.Reader;
import java.util.ArrayDeque;

/**
 * Hand-written JavaScript lexer, based on karate-v2's JsLexer.
 * Maintains the same public API as the previous JFlex-generated lexer
 * so that KarateLexerAdapter, Parser, and other callers work unchanged.
 */
// CHECKSTYLE.OFF: AbbreviationAsWordInName
// CHECKSTYLE.OFF: MemberName
// CHECKSTYLE.OFF: ParameterName
// CHECKSTYLE.OFF: LocalVariableName
// CHECKSTYLE.OFF: MethodName
// CHECKSTYLE.OFF: MissingSwitchDefault
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Lexer {

  public static final int YYEOF = -1;
  public static final int YYINITIAL = 0;
  public static final int TEMPLATE = 2;
  public static final int PLACEHOLDER = 4;

  private CharSequence zzBuffer = "";
  private int zzStartRead;
  private int zzEndRead;
  private int zzCurrentPos;
  private int zzMarkedPos;
  private int zzLexicalState = YYINITIAL;

  private boolean regexAllowed = true;
  private final ArrayDeque<Integer> kkStack = new ArrayDeque<>();

  public Lexer(Reader reader) {
    // reader is ignored; actual input comes via reset()
  }

  // ===== public API (same as JFlex-generated lexer) =====

  public void reset(CharSequence buffer, int start, int end, int initialState) {
    zzBuffer = buffer;
    zzCurrentPos = zzMarkedPos = zzStartRead = start;
    zzEndRead = end;
    zzLexicalState = initialState;
    regexAllowed = true;
  }

  public final int getTokenStart() {
    return zzStartRead;
  }

  public final int getTokenEnd() {
    return zzMarkedPos;
  }

  public final int yystate() {
    return zzLexicalState;
  }

  public final void yybegin(int newState) {
    zzLexicalState = newState;
  }

  public final CharSequence yytext() {
    return zzBuffer.subSequence(zzStartRead, zzMarkedPos);
  }

  public final char yycharat(int pos) {
    return zzBuffer.charAt(zzStartRead + pos);
  }

  public final int yylength() {
    return zzMarkedPos - zzStartRead;
  }

  public void yypushback(int number) {
    if (number > yylength()) {
      throw new Error("pushback value was too large");
    }
    zzMarkedPos -= number;
  }

  public Token yylex() {
    zzStartRead = zzCurrentPos;
    if (zzCurrentPos >= zzEndRead) {
      return null;
    }
    Token token = scanToken();
    zzMarkedPos = zzCurrentPos;
    updateRegexAllowed(token);
    return token;
  }

  // ===== state stack for template literals =====

  private void kkPush() {
    kkStack.push(yystate());
  }

  private int kkPop() {
    return kkStack.pop();
  }

  // ===== character helpers =====

  private boolean isAtEnd() {
    return zzCurrentPos >= zzEndRead;
  }

  private char peek() {
    if (zzCurrentPos >= zzEndRead) {
      return 0;
    }
    return zzBuffer.charAt(zzCurrentPos);
  }

  private char peek(int offset) {
    int idx = zzCurrentPos + offset;
    if (idx >= zzEndRead) {
      return 0;
    }
    return zzBuffer.charAt(idx);
  }

  private char advance() {
    return zzBuffer.charAt(zzCurrentPos++);
  }

  private boolean match(char expected) {
    if (zzCurrentPos < zzEndRead && zzBuffer.charAt(zzCurrentPos) == expected) {
      zzCurrentPos++;
      return true;
    }
    return false;
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isHexDigit(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  private static boolean isIdentStart(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$'
        || (c > 127 && Character.isJavaIdentifierStart(c));
  }

  private static boolean isIdentPart(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9') || c == '_' || c == '$'
        || (c > 127 && Character.isJavaIdentifierPart(c));
  }

  // ===== main scan dispatch =====

  private Token scanToken() {
    if (zzLexicalState == TEMPLATE) {
      return scanTemplateContent();
    }

    char c = peek();

    if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
      return scanWhitespace();
    }

    if (c == '/') {
      return scanSlash();
    }

    if (c == '"') {
      return scanDoubleString();
    }
    if (c == '\'') {
      return scanSingleString();
    }

    if (c == '`') {
      advance();
      kkPush();
      yybegin(TEMPLATE);
      return Token.BACKTICK;
    }

    if (isIdentStart(c)) {
      return scanIdentifier();
    }

    if (isDigit(c)
        || (c == '.' && zzCurrentPos + 1 < zzEndRead && isDigit(peek(1)))) {
      return scanNumber();
    }

    return scanOperator();
  }

  // ===== whitespace =====

  private Token scanWhitespace() {
    boolean hasNewline = false;
    while (zzCurrentPos < zzEndRead) {
      char c = zzBuffer.charAt(zzCurrentPos);
      if (c == ' ' || c == '\t') {
        zzCurrentPos++;
      } else if (c == '\n') {
        zzCurrentPos++;
        hasNewline = true;
      } else if (c == '\r') {
        zzCurrentPos++;
        hasNewline = true;
      } else {
        break;
      }
    }
    return hasNewline ? Token.WS_LF : Token.WS;
  }

  // ===== slash disambiguation: comment, regex, or division =====

  private Token scanSlash() {
    advance(); // consume '/'
    if (match('/')) {
      return scanLineComment();
    }
    if (match('*')) {
      return scanBlockComment();
    }
    if (regexAllowed) {
      return scanRegex();
    }
    return match('=') ? Token.SLASH_EQ : Token.SLASH;
  }

  private Token scanLineComment() {
    while (zzCurrentPos < zzEndRead) {
      char c = zzBuffer.charAt(zzCurrentPos);
      if (c == '\n' || c == '\r') {
        break;
      }
      zzCurrentPos++;
    }
    return Token.L_COMMENT;
  }

  private Token scanBlockComment() {
    while (zzCurrentPos < zzEndRead) {
      char c = zzBuffer.charAt(zzCurrentPos);
      if (c == '*' && zzCurrentPos + 1 < zzEndRead
          && zzBuffer.charAt(zzCurrentPos + 1) == '/') {
        zzCurrentPos += 2;
        break;
      }
      zzCurrentPos++;
    }
    return Token.B_COMMENT;
  }

  private Token scanRegex() {
    // opening '/' already consumed
    boolean inCharClass = false;
    while (!isAtEnd()) {
      char c = peek();
      if (c == '\n' || c == '\r') {
        break;
      }
      if (c == '\\') {
        advance();
        if (!isAtEnd() && peek() != '\n' && peek() != '\r') {
          advance();
        }
        continue;
      }
      if (c == '[') {
        inCharClass = true;
        advance();
        continue;
      }
      if (c == ']' && inCharClass) {
        inCharClass = false;
        advance();
        continue;
      }
      if (c == '/' && !inCharClass) {
        advance(); // consume closing '/'
        // consume flags: g, i, m, s, u, y, d
        while (!isAtEnd() && isIdentPart(peek())) {
          advance();
        }
        break;
      }
      advance();
    }
    return Token.REGEX;
  }

  // ===== strings =====

  private Token scanDoubleString() {
    advance(); // opening "
    while (zzCurrentPos < zzEndRead) {
      char c = zzBuffer.charAt(zzCurrentPos);
      if (c == '"') {
        zzCurrentPos++;
        break;
      }
      if (c == '\\' && zzCurrentPos + 1 < zzEndRead) {
        zzCurrentPos += 2;
        continue;
      }
      zzCurrentPos++;
    }
    return Token.D_STRING;
  }

  private Token scanSingleString() {
    advance(); // opening '
    while (zzCurrentPos < zzEndRead) {
      char c = zzBuffer.charAt(zzCurrentPos);
      if (c == '\'') {
        zzCurrentPos++;
        break;
      }
      if (c == '\\' && zzCurrentPos + 1 < zzEndRead) {
        zzCurrentPos += 2;
        continue;
      }
      zzCurrentPos++;
    }
    return Token.S_STRING;
  }

  // ===== template literals =====

  private Token scanTemplateContent() {
    if (isAtEnd()) {
      return null;
    }

    char c = peek();

    if (c == '`') {
      advance();
      yybegin(kkPop());
      return Token.BACKTICK;
    }

    if (c == '$' && peek(1) == '{') {
      advance();
      advance();
      kkPush();
      yybegin(PLACEHOLDER);
      return Token.DOLLAR_L_CURLY;
    }

    // scan template string content
    while (!isAtEnd()) {
      c = peek();
      if (c == '`') {
        break;
      }
      if (c == '$' && peek(1) == '{') {
        break;
      }
      if (c == '\\') {
        advance();
        if (!isAtEnd()) {
          advance();
        }
        continue;
      }
      advance();
    }
    return Token.T_STRING;
  }

  // ===== numbers =====

  private Token scanNumber() {
    char c = peek();

    // hex: 0x...
    if (c == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
      advance();
      advance();
      while (!isAtEnd() && isHexDigit(peek())) {
        advance();
      }
      return Token.NUMBER;
    }

    // octal: 0o...
    if (c == '0' && (peek(1) == 'o' || peek(1) == 'O')) {
      advance();
      advance();
      while (!isAtEnd() && peek() >= '0' && peek() <= '7') {
        advance();
      }
      return Token.NUMBER;
    }

    // binary: 0b...
    if (c == '0' && (peek(1) == 'b' || peek(1) == 'B')) {
      advance();
      advance();
      while (!isAtEnd() && (peek() == '0' || peek() == '1')) {
        advance();
      }
      return Token.NUMBER;
    }

    // starts with dot (.5)
    if (c == '.') {
      advance();
    } else {
      // integer part
      while (!isAtEnd() && isDigit(peek())) {
        advance();
      }
      // optional fractional part
      if (!isAtEnd() && peek() == '.'
          && (zzCurrentPos + 1 >= zzEndRead || isDigit(peek(1)))) {
        advance(); // consume '.'
      }
    }

    // fractional digits
    while (!isAtEnd() && isDigit(peek())) {
      advance();
    }

    // exponent
    if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
      advance();
      if (!isAtEnd() && (peek() == '+' || peek() == '-')) {
        advance();
      }
      while (!isAtEnd() && isDigit(peek())) {
        advance();
      }
    }

    return Token.NUMBER;
  }

  // ===== identifiers and keywords =====

  private Token scanIdentifier() {
    while (zzCurrentPos < zzEndRead) {
      char c = zzBuffer.charAt(zzCurrentPos);
      if (isIdentPart(c)) {
        zzCurrentPos++;
      } else {
        break;
      }
    }
    return keywordOrIdent(zzStartRead, zzCurrentPos - zzStartRead);
  }

  private Token keywordOrIdent(int start, int len) {
    switch (len) {
      case 2:
        return keyword2(start);
      case 3:
        return keyword3(start);
      case 4:
        return keyword4(start);
      case 5:
        return keyword5(start);
      case 6:
        return keyword6(start);
      case 7:
        return keyword7(start);
      case 8:
        return keyword8(start);
      case 10:
        if (matchKeyword(start, "instanceof")) {
          return Token.INSTANCEOF;
        }
        return Token.IDENT;
      default:
        return Token.IDENT;
    }
  }

  private Token keyword2(int start) {
    char c0 = zzBuffer.charAt(start);
    if (c0 == 'i') {
      if (zzBuffer.charAt(start + 1) == 'f') {
        return Token.IF;
      }
      if (zzBuffer.charAt(start + 1) == 'n') {
        return Token.IN;
      }
    } else if (c0 == 'o' && zzBuffer.charAt(start + 1) == 'f') {
      return Token.OF;
    } else if (c0 == 'd' && zzBuffer.charAt(start + 1) == 'o') {
      return Token.DO;
    }
    return Token.IDENT;
  }

  private Token keyword3(int start) {
    char c0 = zzBuffer.charAt(start);
    if (c0 == 'v' && matchKeyword(start, "var")) {
      return Token.VAR;
    }
    if (c0 == 'l' && matchKeyword(start, "let")) {
      return Token.LET;
    }
    if (c0 == 'n' && matchKeyword(start, "new")) {
      return Token.NEW;
    }
    if (c0 == 't' && matchKeyword(start, "try")) {
      return Token.TRY;
    }
    if (c0 == 'f' && matchKeyword(start, "for")) {
      return Token.FOR;
    }
    return Token.IDENT;
  }

  private Token keyword4(int start) {
    char c0 = zzBuffer.charAt(start);
    if (c0 == 'n' && matchKeyword(start, "null")) {
      return Token.NULL;
    }
    if (c0 == 't' && matchKeyword(start, "true")) {
      return Token.TRUE;
    }
    if (c0 == 'e' && matchKeyword(start, "else")) {
      return Token.ELSE;
    }
    if (c0 == 'c' && matchKeyword(start, "case")) {
      return Token.CASE;
    }
    return Token.IDENT;
  }

  private Token keyword5(int start) {
    char c0 = zzBuffer.charAt(start);
    if (c0 == 'f' && matchKeyword(start, "false")) {
      return Token.FALSE;
    }
    if (c0 == 'c') {
      if (matchKeyword(start, "const")) {
        return Token.CONST;
      }
      if (matchKeyword(start, "catch")) {
        return Token.CATCH;
      }
    }
    if (c0 == 't' && matchKeyword(start, "throw")) {
      return Token.THROW;
    }
    if (c0 == 'w' && matchKeyword(start, "while")) {
      return Token.WHILE;
    }
    if (c0 == 'b' && matchKeyword(start, "break")) {
      return Token.BREAK;
    }
    return Token.IDENT;
  }

  private Token keyword6(int start) {
    char c0 = zzBuffer.charAt(start);
    if (c0 == 'r' && matchKeyword(start, "return")) {
      return Token.RETURN;
    }
    if (c0 == 't' && matchKeyword(start, "typeof")) {
      return Token.TYPEOF;
    }
    if (c0 == 'd' && matchKeyword(start, "delete")) {
      return Token.DELETE;
    }
    if (c0 == 's' && matchKeyword(start, "switch")) {
      return Token.SWITCH;
    }
    return Token.IDENT;
  }

  private Token keyword7(int start) {
    char c0 = zzBuffer.charAt(start);
    if (c0 == 'f' && matchKeyword(start, "finally")) {
      return Token.FINALLY;
    }
    if (c0 == 'd' && matchKeyword(start, "default")) {
      return Token.DEFAULT;
    }
    return Token.IDENT;
  }

  private Token keyword8(int start) {
    char c0 = zzBuffer.charAt(start);
    if (c0 == 'f' && matchKeyword(start, "function")) {
      return Token.FUNCTION;
    }
    return Token.IDENT;
  }

  private boolean matchKeyword(int start, String keyword) {
    int klen = keyword.length();
    for (int i = 0; i < klen; i++) {
      if (zzBuffer.charAt(start + i) != keyword.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  // ===== operators =====

  private Token scanOperator() {
    char c = advance();

    switch (c) {
      case '{':
        return Token.L_CURLY;
      case '}':
        if (zzLexicalState == PLACEHOLDER) {
          yybegin(kkPop());
        }
        return Token.R_CURLY;
      case '[':
        return Token.L_BRACKET;
      case ']':
        return Token.R_BRACKET;
      case '(':
        return Token.L_PAREN;
      case ')':
        return Token.R_PAREN;
      case ',':
        return Token.COMMA;
      case ':':
        return Token.COLON;
      case ';':
        return Token.SEMI;
      case '~':
        return Token.TILDE;
      case '.':
        if (match('.')) {
          if (match('.')) {
            return Token.DOT_DOT_DOT;
          }
          // two dots but not three — push one back
          zzCurrentPos--;
        }
        return Token.DOT;
      case '?':
        if (match('?')) {
          return Token.QUES_QUES;
        }
        return Token.QUES;
      case '=':
        if (match('=')) {
          return match('=') ? Token.EQ_EQ_EQ : Token.EQ_EQ;
        }
        if (match('>')) {
          return Token.EQ_GT;
        }
        return Token.EQ;
      case '<':
        if (match('<')) {
          return match('=') ? Token.LT_LT_EQ : Token.LT_LT;
        }
        return match('=') ? Token.LT_EQ : Token.LT;
      case '>':
        if (match('>')) {
          if (match('>')) {
            return match('=') ? Token.GT_GT_GT_EQ : Token.GT_GT_GT;
          }
          return match('=') ? Token.GT_GT_EQ : Token.GT_GT;
        }
        return match('=') ? Token.GT_EQ : Token.GT;
      case '!':
        if (match('=')) {
          return match('=') ? Token.NOT_EQ_EQ : Token.NOT_EQ;
        }
        return Token.NOT;
      case '|':
        if (match('|')) {
          return match('=') ? Token.PIPE_PIPE_EQ : Token.PIPE_PIPE;
        }
        return match('=') ? Token.PIPE_EQ : Token.PIPE;
      case '&':
        if (match('&')) {
          return match('=') ? Token.AMP_AMP_EQ : Token.AMP_AMP;
        }
        return match('=') ? Token.AMP_EQ : Token.AMP;
      case '^':
        return match('=') ? Token.CARET_EQ : Token.CARET;
      case '+':
        if (match('+')) {
          return Token.PLUS_PLUS;
        }
        return match('=') ? Token.PLUS_EQ : Token.PLUS;
      case '-':
        if (match('-')) {
          return Token.MINUS_MINUS;
        }
        return match('=') ? Token.MINUS_EQ : Token.MINUS;
      case '*':
        if (match('*')) {
          return match('=') ? Token.STAR_STAR_EQ : Token.STAR_STAR;
        }
        return match('=') ? Token.STAR_EQ : Token.STAR;
      case '%':
        return match('=') ? Token.PERCENT_EQ : Token.PERCENT;
    }
    // unknown character — throw to match old JFlex lexer behavior
    throw new Error("Illegal character <" + c + ">");
  }

  // ===== regex-allowed tracking =====

  private void updateRegexAllowed(Token token) {
    if (token == null) {
      return;
    }
    switch (token) {
      // after these tokens, a '/' starts a regex
      case L_PAREN:
      case L_BRACKET:
      case L_CURLY:
      case COMMA:
      case SEMI:
      case COLON:
      case EQ:
      case EQ_EQ:
      case EQ_EQ_EQ:
      case NOT_EQ:
      case NOT_EQ_EQ:
      case NOT:
      case PLUS:
      case PLUS_EQ:
      case MINUS:
      case MINUS_EQ:
      case STAR:
      case STAR_EQ:
      case STAR_STAR:
      case STAR_STAR_EQ:
      case SLASH_EQ:
      case PERCENT:
      case PERCENT_EQ:
      case LT:
      case LT_EQ:
      case LT_LT:
      case LT_LT_EQ:
      case GT:
      case GT_EQ:
      case GT_GT:
      case GT_GT_EQ:
      case GT_GT_GT:
      case GT_GT_GT_EQ:
      case AMP:
      case AMP_EQ:
      case AMP_AMP:
      case AMP_AMP_EQ:
      case PIPE:
      case PIPE_EQ:
      case PIPE_PIPE:
      case PIPE_PIPE_EQ:
      case CARET:
      case CARET_EQ:
      case TILDE:
      case QUES:
      case QUES_QUES:
      case EQ_GT:
      case RETURN:
      case TYPEOF:
      case DELETE:
      case IF:
      case ELSE:
      case IN:
      case OF:
      case DO:
      case WHILE:
      case FOR:
      case NEW:
      case THROW:
      case CASE:
      case INSTANCEOF:
      case VAR:
      case LET:
      case CONST:
        regexAllowed = true;
        break;
      // after these tokens, a '/' is division
      case R_PAREN:
      case R_BRACKET:
      case R_CURLY:
      case IDENT:
      case NUMBER:
      case S_STRING:
      case D_STRING:
      case T_STRING:
      case REGEX:
      case TRUE:
      case FALSE:
      case NULL:
      case PLUS_PLUS:
      case MINUS_MINUS:
        regexAllowed = false;
        break;
      // for whitespace, comments, etc. — keep previous value
    }
  }

}
