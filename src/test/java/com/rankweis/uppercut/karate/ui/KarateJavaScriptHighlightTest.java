package com.rankweis.uppercut.karate.ui;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.lexer.impl.KarateJavascriptExtension;
import java.util.List;

public class KarateJavaScriptHighlightTest extends BasePlatformTestCase {

  @Override protected void setUp() throws Exception {
    super.setUp();
    if (!ApplicationManager.getApplication().getExtensionArea()
        .hasExtensionPoint(KarateJavascriptParsingExtensionPoint.EP_NAME.getName())) {
      ApplicationManager.getApplication().getExtensionArea().registerExtensionPoint(
        KarateJavascriptParsingExtensionPoint.EP_NAME.getName(),
        KarateJavascriptParsingExtensionPoint.class.getName(),
        com.intellij.openapi.extensions.ExtensionPoint.Kind.INTERFACE, false);
    }
    ExtensionTestUtil.maskExtensions(KarateJavascriptParsingExtensionPoint.EP_NAME,
      List.of(new KarateJavascriptExtension()), getTestRootDisposable());
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
