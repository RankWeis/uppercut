package com.rankweis.uppercut.karate.lexer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.rankweis.uppercut.karate.psi.GherkinKeywordProvider;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import io.karatelabs.js.KarateJsNoPluginExtension;
import java.util.ArrayList;
import java.util.List;
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
    if (!ApplicationManager.getApplication().getExtensionArea()
        .hasExtensionPoint(KarateJavascriptParsingExtensionPoint.EP_NAME.getName())) {
      ApplicationManager.getApplication().getExtensionArea().registerExtensionPoint(
        KarateJavascriptParsingExtensionPoint.EP_NAME.getName(),
        KarateJavascriptParsingExtensionPoint.class.getName(),
        com.intellij.openapi.extensions.ExtensionPoint.Kind.INTERFACE, false);
    }
    ExtensionTestUtil.maskExtensions(KarateJavascriptParsingExtensionPoint.EP_NAME,
      List.of(new KarateJsNoPluginExtension()), getTestRootDisposable());
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

  /**
   * The editor's incremental highlighter requires a gapless token sequence in which every token
   * advances. A cell that ends at or before it starts breaks that sequence and stalls the lexer -
   * see https://github.com/rankweis/uppercut/issues/380, where typing an Examples table froze the IDE.
   */
  private void assertLexesCleanly(String text) {
    lexer.start(text, 0, text.length(), 0);
    int expectedStart = 0;
    int guard = 0;
    while (lexer.getTokenType() != null) {
      final int start = lexer.getTokenStart();
      final int end = lexer.getTokenEnd();
      assertTrue("token " + lexer.getTokenType() + " ends at " + end + " but starts at " + start
        + " in [" + text + "]", end > start);
      assertEquals("gap or overlap before " + lexer.getTokenType() + " in [" + text + "]",
        expectedStart, start);
      expectedStart = end;
      if (++guard > 5000) {
        fail("lexer did not terminate on [" + text + "]");
      }
      lexer.advance();
    }
    assertEquals("tokens do not cover [" + text + "]", text.length(), expectedStart);
  }

  public void testEmptyTableCellDoesNotStallTheLexer() {
    assertLexesCleanly("Examples:\n  |a||b|\n");
  }

  public void testDoublePipeAfterSpaceDoesNotStallTheLexer() {
    assertLexesCleanly("Examples:\n  |dateOfBirth| ||\n");
  }

  public void testUnfinishedTableRowDoesNotStallTheLexer() {
    assertLexesCleanly("Examples:\n  |dateOfBirth| ||+\n");
  }

  public void testLogicalOrOutsideTableIsNotDelimiter() {
    String text = "* if (a || b) print 'x'\n";
    assertLexesCleanly(text);
    lexer.start(text, 0, text.length(), 0);
    while (lexer.getTokenType() != null) {
      assertNotSame("'||' outside a table must not lex as a table delimiter",
        KarateTokenTypes.PIPE, lexer.getTokenType());
      lexer.advance();
    }
  }

  /**
   * A state carrying a sub-lexer's offset means nothing without that sub-lexer, which start() has
   * no way to rebuild. Restarting from one used to read offsets off whichever region the sub-lexer
   * was last pointed at; the lexer must fall back to plain Karate rather than emit a token that
   * ends before it began.
   */
  public void testRestartFromEveryTokenBoundaryStaysValid() {
    String text = """
      Feature: f

        Background:
          * def now = function() { return java.lang.System.currentTimeMillis() }
          * def body = { "id": "#(response.id)", "kind": "#string" }

        Scenario Outline: s
          Examples:
            |a|b|
      """;
    List<Integer> states = new ArrayList<>();
    List<Integer> starts = new ArrayList<>();
    lexer.start(text, 0, text.length(), 0);
    while (lexer.getTokenType() != null) {
      starts.add(lexer.getTokenStart());
      states.add(lexer.getState());
      lexer.advance();
    }
    // the state recorded on token i is what a restart at token i+1 would replay
    for (int i = 0; i < starts.size() - 1; i++) {
      lexer.start(text, starts.get(i + 1), text.length(), states.get(i));
      int expectedStart = starts.get(i + 1);
      int guard = 0;
      while (lexer.getTokenType() != null) {
        assertEquals("gap or overlap restarting at " + starts.get(i + 1) + " in state " + states.get(i),
          expectedStart, lexer.getTokenStart());
        assertTrue("token " + lexer.getTokenType() + " ends at " + lexer.getTokenEnd()
          + " but starts at " + lexer.getTokenStart() + ", restarting at " + starts.get(i + 1)
          + " in state " + states.get(i), lexer.getTokenEnd() > lexer.getTokenStart());
        expectedStart = lexer.getTokenEnd();
        if (++guard > 5000) {
          fail("lexer did not terminate restarting at " + starts.get(i + 1) + " in state " + states.get(i));
        }
        lexer.advance();
      }
      assertEquals("tokens do not reach the end restarting at " + starts.get(i + 1),
        text.length(), expectedStart);
    }
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