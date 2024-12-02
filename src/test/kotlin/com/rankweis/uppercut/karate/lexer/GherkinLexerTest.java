package com.rankweis.uppercut.karate.lexer;

import com.intellij.testFramework.LightPlatformTestCase;
import com.rankweis.uppercut.karate.psi.GherkinKeywordProvider;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GherkinLexerTest extends LightPlatformTestCase {

  private GherkinLexer lexer;

  @Mock GherkinKeywordProvider keywordProvider;

  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.openMocks(this);
    lexer = new GherkinLexer(keywordProvider);
  }

  public void testFindNextMatchingClosingBrace_SimpleCase() {
    lexer.start(" { some text }", 0, 14, 0);
    lexer.advance();
    int result = lexer.findNextMatchingClosingBrace();
    assertEquals(13, result);
  }

  public void testFindNextMatchingClosingBrace_NestedBraces() {
    lexer.start(" { { nested } text }", 0, 21, 0);
    int result = lexer.findNextMatchingClosingBrace();
    assertEquals(19, result);
  }

  public void testFindNextMatchingClosingBrace_NoClosingBrace() {
    lexer.start("{ some text", 0, 11, 0);
    lexer.advance();
    int result = lexer.findNextMatchingClosingBrace();
    assertEquals(-1, result);
  }

  public void testFindingNextMatchingClosingBrace_RealScenario() {
    String realScenario = """
      Scenario: Process deeply nested JSON with dynamic expressions
        * def input = { "outer": { "inner": { "key": "#[randomString(5)]" } } }
        * def expected = karate.jsonPath(input, '$.outer.inner.key')
        * def dynamicResult = function() {
        var result = karate.jsonPath(input, '$.outer.inner.key');
        return result == expected;
        }
        * match dynamicResult() == true
        * print 'Dynamic result validation passed!
        '""";
    int start = realScenario.indexOf("function");
    int end = realScenario.lastIndexOf("}");
    lexer.start(realScenario, start, realScenario.length(), 0, false);
    int result = lexer.findNextMatchingClosingBrace();
    assertEquals(end, result);
  }

  public void testFindNextMatchingClosingBrace_EmptyString() {
    lexer.start("", 0, 0, 0);
    lexer.advance();
    assertNull(lexer.getTokenType());
  }

}