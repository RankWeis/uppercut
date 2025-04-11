package com.rankweis.uppercut.karate.lexer;

import com.intellij.testFramework.LightPlatformTestCase;
import com.rankweis.uppercut.karate.psi.GherkinKeywordProvider;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class UppercutLexerTest extends LightPlatformTestCase {

  private UppercutLexer lexer;

  @Mock GherkinKeywordProvider keywordProvider;

  private static final String[] REAL_SCENARIOS = new String[]{
    """
      Scenario: Process deeply nested JSON with dynamic expressions
        * def input = { "outer": { "inner": { "key": "#[randomString(5)]" } } }
        * def expected = karate.jsonPath(input, '$.outer.inner.key')
        * def dynamicResult = function() {
        var result = karate.jsonPath(input, '$.outer.inner.key');
        return result == expected;
        }
        * match dynamicResult() == true
        * print 'Dynamic result validation passed!
        '""",
    """
          * def flattenJson = function(obj, prefix) {
              var result = {};
              for (var key in obj) {
                  var prefixedKey = prefix ? prefix + '.' + key : key;
                  if (typeof obj[key] === 'object' && obj[key] !== null) {
                      Object.assign(result, flattenJson(obj[key], prefixedKey));
                  } else {
                      result[prefixedKey] = obj[key];
                  }
              }
              return result;
          }
      """
  };

  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.openMocks(this);
    lexer = new UppercutLexer(keywordProvider);
  }

  public void testFindNextMatchingClosingBraceSimpleCase() {
    lexer.start(" { some text }", 0, 14, 0);
    lexer.advance();
    int result = lexer.findNextMatchingClosingBrace();
    assertEquals(13, result);
  }

  public void testFindNextMatchingClosingBraceNestedBraces() {
    lexer.start(" { { nested } text }", 0, 21, 0);
    int result = lexer.findNextMatchingClosingBrace();
    assertEquals(19, result);
  }

  public void testFindNextMatchingClosingBraceNoClosingBrace() {
    lexer.start("{ some text", 0, 11, 0);
    lexer.advance();
    int result = lexer.findNextMatchingClosingBrace();
    assertEquals(-1, result);
  }

  public void testFindingNextMatchingClosingBraceRealScenario() {
    for (String realScenario : REAL_SCENARIOS) {
      int start = realScenario.indexOf("function");
      int end = realScenario.lastIndexOf("}");
      lexer.start(realScenario, start, realScenario.length(), 0, false);
      int result = lexer.findNextMatchingClosingBrace();
      assertEquals(end, result);
    }
  }

  public void testFindNextMatchingClosingBraceEmptyString() {
    lexer.start("", 0, 0, 0);
    lexer.advance();
    assertNull(lexer.getTokenType());
  }

  public void testContainsCharEarlierInLine() {
    String buffer = "* def functions = read(callme)";
    lexer.start(buffer, 0, buffer.length(), 0);
    assertFalse(lexer.containsCharEarlierInLine('='));
    lexer.advance();
    assertNotNull(lexer.getTokenType());
    lexer.start(buffer, buffer.indexOf("function"), buffer.length(), 0);
    assertFalse(lexer.containsCharEarlierInLine('='));
    lexer.start(buffer, buffer.indexOf("read"), buffer.length(), 0);
    assertTrue(lexer.containsCharEarlierInLine('='));
  }

}