package test;

import com.rankweis.uppercut.karate.psi.GherkinParserDefinition;
import com.intellij.testFramework.ParsingTestCase;

public class SimpleParsingTest extends ParsingTestCase {

  public SimpleParsingTest() {
    super("", "karate", new GherkinParserDefinition());
  }

  public void testParsingTestData() {
    doTest(true);
  }

  /**
   * @return path to test data file directory relative to root of this module.
   */
  @Override
  protected String getTestDataPath() {
    return "testData/";
  }

  @Override
  protected boolean includeRanges() {
    return true;
  }

}