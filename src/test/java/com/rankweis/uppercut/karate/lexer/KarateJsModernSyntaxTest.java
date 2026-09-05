package com.rankweis.uppercut.karate.lexer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import io.karatelabs.js.Lexer;
import io.karatelabs.js.Node;
import io.karatelabs.js.Parser;
import io.karatelabs.js.Source;
import io.karatelabs.js.Token;
import io.karatelabs.js.Type;
import java.io.CharArrayReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Covers the JS syntax karate-js 2.1.1 supports that the vendored engine under
 * {@code io.karatelabs.js} did not: optional chaining, nullish coalescing and assignment,
 * classes, {@code this} / {@code super}, {@code continue}, {@code void} and BigInt literals.
 *
 * <p>The vendored engine is only reached on the fallback path - when the IntelliJ JavaScript
 * plugin is absent (see {@code KarateJsNoPluginExtension}) - where it drives highlighting and
 * error annotations, so a syntax it cannot parse shows up as a false error in a valid feature
 * file. These tests are plain JUnit and need no IDE fixture.
 */
public class KarateJsModernSyntaxTest {

  // ===== lexer =====

  @Test public void optionalChainingLexesAsOneToken() {
    assertTokens("a?.b", Token.IDENT, Token.QUES_DOT, Token.IDENT);
    assertTokens("a?.[k]", Token.IDENT, Token.QUES_DOT, Token.L_BRACKET, Token.IDENT, Token.R_BRACKET);
    assertTokens("f?.()", Token.IDENT, Token.QUES_DOT, Token.L_PAREN, Token.R_PAREN);
  }

  @Test public void ternaryFollowedByALeadingDotNumberIsNotOptionalChaining() {
    // spec lookahead: `?. [lookahead not in DecimalDigit]`, so this stays `flag ? .5 : 0`
    assertTokens("flag ?.5 : 0", Token.IDENT, Token.QUES, Token.NUMBER, Token.COLON, Token.NUMBER);
  }

  @Test public void nullishOperatorsLexLongestMatchFirst() {
    assertTokens("a ?? b", Token.IDENT, Token.QUES_QUES, Token.IDENT);
    assertTokens("a ??= b", Token.IDENT, Token.QUES_QUES_EQ, Token.IDENT);
  }

  @Test public void newKeywordsAreKeywordsNotIdentifiers() {
    assertTokens("class", Token.CLASS);
    assertTokens("extends", Token.EXTENDS);
    assertTokens("super", Token.SUPER);
    assertTokens("this", Token.THIS);
    assertTokens("continue", Token.CONTINUE);
    assertTokens("void", Token.VOID);
    // and they are still only whole words
    assertTokens("classy", Token.IDENT);
    assertTokens("superb", Token.IDENT);
  }

  @Test public void bigIntSuffixIsPartOfTheLiteral() {
    assertTokens("10n", Token.BIGINT);
    assertTokens("0xffn", Token.BIGINT);
    assertTokens("0b101n", Token.BIGINT);
    assertTokens("0o17n", Token.BIGINT);
    // not an integer literal, so the `n` is a separate identifier - as in karate-js 2.1.1
    assertTokens("1.5n", Token.NUMBER, Token.IDENT);
    assertTokens("10", Token.NUMBER);
  }

  // ===== parser =====

  @Test public void optionalChainingParses() {
    parses("var x = a?.b");
    parses("var x = a?.b?.c.d");
    parses("var x = a?.[key]");
    parses("var x = fn?.(1, 2)");
    parses("var x = a?.b?.[0]?.(1).c");
  }

  @Test public void optionalChainStaysFlatAndLeftRecursive() {
    Node program = parses("var x = a?.b?.[0]?.(1)");
    // outermost step is the call; its first child is the bracket step, and so on down to `a`
    assertNotNull(program.findFirst(Type.FN_CALL_EXPR));
    assertNotNull(program.findFirst(Type.REF_BRACKET_EXPR));
    assertNotNull(program.findFirst(Type.REF_DOT_EXPR));
    assertNotNull(program.findFirst(Token.QUES_DOT));
  }

  @Test public void nullishCoalescingAndLogicalAssignmentParse() {
    assertNotNull(parses("var x = a ?? b").findFirst(Type.LOGIC_NULLISH_EXPR));
    assertNotNull(parses("a ??= b").findFirst(Type.ASSIGN_EXPR));
    assertNotNull(parses("a ||= b").findFirst(Type.ASSIGN_EXPR));
    assertNotNull(parses("a &&= b").findFirst(Type.ASSIGN_EXPR));
    assertNotNull(parses("a &= b").findFirst(Type.ASSIGN_EXPR));
    assertNotNull(parses("a |= b").findFirst(Type.ASSIGN_EXPR));
    assertNotNull(parses("a ^= b").findFirst(Type.ASSIGN_EXPR));
  }

  @Test public void classDeclarationParses() {
    assertNotNull(parses("class A { m() { return 1 } }").findFirst(Type.CLASS_EXPR));
    parses("class A extends B { constructor() { super(1) } m() { return this.x } }");
    parses("class A { static create() { return new A() } get x() { return 1 } set x(v) { } }");
    parses("class A { x = 1; y }");
    parses("class A { [key]() { return 1 } }");
    parses("var A = class { m() { return 1 } }");
  }

  @Test public void classExpressionKeepsItsShape() {
    Node program = parses("class A extends B { m() { return this.x } n = 1 }");
    Node clazz = program.findFirst(Type.CLASS_EXPR);
    assertNotNull(clazz);
    assertNotNull(clazz.findFirst(Token.EXTENDS));
    assertNotNull(clazz.findFirst(Type.CLASS_MEMBER));
    assertNotNull(clazz.findFirst(Type.FN_EXPR)); // the method
  }

  @Test public void thisAndSuperAreExpressions() {
    assertNotNull(parses("var x = this.foo").findFirst(Type.REF_EXPR));
    parses("this.doIt(1)");
    assertNotNull(parses("class A extends B { m() { super.m() } }").findFirst(Type.SUPER_EXPR));
  }

  @Test public void continueParses() {
    assertNotNull(parses("for (var i = 0; i < 3; i++) { if (i == 1) { continue } }")
      .findFirst(Type.CONTINUE_STMT));
    parses("while (true) { continue }");
  }

  @Test public void voidIsAUnaryOperator() {
    assertNotNull(parses("var x = void 0").findFirst(Type.UNARY_EXPR));
    parses("if (void fn() === undefined) { var y = 1 }");
  }

  @Test public void bigIntLiteralsParse() {
    parses("var x = 10n");
    parses("var x = 0xffn");
    parses("var x = 1n + 2n");
    parses("var x = -1n");
  }

  @Test public void reservedWordsAreValidPropertyNamesAndObjectKeys() {
    // an IdentifierName, not an Identifier - `a.default` and `{ default: 1 }` are legal JS,
    // and making `class` / `this` / `void` keywords must not break them
    parses("var x = a.default");
    parses("var x = a.class");
    parses("var x = a.this");
    parses("var o = { default: 1, class: 2, this: 3, function: 4 }");
    parses("var x = a?.class");
  }

  @Test public void existingSyntaxStillParses() {
    parses("var x = a ? b : c");
    parses("var x = .5");
    parses("var x = fn(...args)");
    parses("var x = `a ${b} c`");
    parses("var x = /ab+c/gi");
    parses("var f = x => x * 2");
    parses("var o = { a: 1, b: [1, 2] }");
    parses("var t = typeof x");
    parses("try { var a = 1 } catch (e) { var b = 2 } finally { var c = 3 }");
    parses("var x = 2 ** 3 ** 2");
    parses("var x = 1 << 2 >>> 3");
    parses("var response = karate.call('foo.feature', { a: 1 })");
    parses("function fn() {\n  var token = karate.get('token')\n  return { Authorization: token }\n}");
  }

  @Test(timeout = 5000)
  public void unterminatedTemplateLiteralIsAnErrorNotAHang() {
    // The template loop only ever consumed T_STRING, ${ or the closing backtick; at end of input it
    // consumed nothing and never advanced. In the IDE that was a parse that never finished.
    for (String source : new String[]{"var x = `abc", "var x = `a ${b", "class A { m() { `abc"}) {
      try {
        new Parser(new Source(source)).parse();
        fail("expected a parse error for: " + source);
      } catch (RuntimeException expected) {
        // an error is the point; a hang would trip the timeout
      }
    }
  }

  // ===== helpers =====

  private static Node parses(String source) {
    try {
      return new Parser(new Source(source)).parse();
    } catch (Exception e) {
      fail("failed to parse: " + source + "\n" + e.getMessage());
      return null; // unreachable
    }
  }

  private static void assertTokens(String source, Token... expected) {
    assertEquals("tokens for: " + source, List.of(expected), lex(source));
  }

  private static List<Token> lex(String source) {
    Lexer lexer = new Lexer(new CharArrayReader(source.toCharArray()));
    lexer.reset(source, 0, source.length(), 0);
    List<Token> tokens = new ArrayList<>();
    while (true) {
      Token token = lexer.yylex();
      if (token == null) {
        return tokens;
      }
      if (token != Token.WS && token != Token.WS_LF) {
        tokens.add(token);
      }
    }
  }
}
