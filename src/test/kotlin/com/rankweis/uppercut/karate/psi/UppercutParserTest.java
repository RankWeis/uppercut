package com.rankweis.uppercut.karate.psi;

import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.ParsingTestCase;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import io.karatelabs.js.KarateJsNoPluginExtension;
import java.io.IOException;
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

  public void testComplicated() {
    doTest(true, true);
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