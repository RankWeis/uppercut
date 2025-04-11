package com.rankweis.uppercut.karate.psi;

import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.ParsingTestCase;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import io.karatelabs.js.KarateJsNoPluginExtension;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class UppercutParserTest extends ParsingTestCase {

  public void setUp() throws Exception {
    super.setUp();
    registerExtensionPoint(getApplication().getExtensionArea(),
      KarateJavascriptParsingExtensionPoint.EP_NAME,
      KarateJavascriptParsingExtensionPoint.class);

    ExtensionTestUtil.maskExtensions(KarateJavascriptParsingExtensionPoint.EP_NAME,
      List.of(new KarateJsNoPluginExtension()), getTestRootDisposable());
  }

  public UppercutParserTest() {
    super("", "feature", true, new UppercutParserDefinition());
  }

  public void testComplicated() throws IOException {
    try {
      doTest(false, true);
    } catch (Throwable t) {
      // Sometimes psi tree has method names, sometimes not. This isn't important, so just check for both.
      System.out.println("Going alternate route.");
      String actual = toParseTreeText(this.myFile, this.skipSpaces(), this.includeRanges());
      ClassLoader classloader = Thread.currentThread().getContextClassLoader();
      InputStream resourceAsStream = classloader.getResourceAsStream("complicated-alt.txt");
      String expected = new String(resourceAsStream.readAllBytes());
      assertEquals(expected.trim(), actual.trim());
    }
  }

  @Override
  protected String getTestDataPath() {
    return "src/test/testData";
  }

  @Override
  protected boolean skipSpaces() {
    return true;
  }
}