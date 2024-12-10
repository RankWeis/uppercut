package com.rankweis.uppercut.karate.ui;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import io.karatelabs.js.KarateJsNoPluginExtension;
import java.util.List;

public class KarateJsHighlightTest extends BasePlatformTestCase {

  @Override protected void setUp() throws Exception {
    super.setUp();
    ExtensionTestUtil.maskExtensions(KarateJavascriptParsingExtensionPoint.EP_NAME,
      List.of(new KarateJsNoPluginExtension()), getTestRootDisposable());
  }

  public void testHighlight() {
    myFixture.configureByFile("testData/random.feature");
    myFixture.testHighlightingAllFiles(true, false, false, "testData/random.feature", "testData/js.feature",
      "testData/complicated.feature");
    myFixture.configureByFile("testData/badjs.feature");
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting(HighlightSeverity.ERROR);
    assertEquals(1, highlightInfos.size());
  }

  @Override protected String getTestDataPath() {
    return "src/test";
  }

}
