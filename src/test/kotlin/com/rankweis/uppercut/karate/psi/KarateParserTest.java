package com.rankweis.uppercut.karate.psi;

import com.intellij.testFramework.ParsingTestCase;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;

public class KarateParserTest extends ParsingTestCase {

  public void setUp() throws Exception {
    super.setUp();
    registerExtensionPoint(getApplication().getExtensionArea(),
      KarateJavascriptParsingExtensionPoint.EP_NAME,
      KarateJavascriptParsingExtensionPoint.class);

  }

  public KarateParserTest() {
    super("", "feature", new GherkinParserDefinition());
  }

  public void testParser() {
    doTest(true, true);
  }

  public void testComplicated() {
    doTest(false, true);
  }

  @Override
  protected String getTestDataPath() {
    return "src/test/testData";
  }

  @Override
  protected boolean includeRanges() {
    return true;
  }
}