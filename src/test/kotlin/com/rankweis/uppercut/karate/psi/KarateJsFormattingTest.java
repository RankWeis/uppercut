package com.rankweis.uppercut.karate.psi;

import com.intellij.lang.Language;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormatterTestCase;
import com.intellij.testFramework.ExtensionTestUtil;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import io.karatelabs.js.KarateJsNoPluginExtension;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class KarateJsFormattingTest extends FormatterTestCase {

  public void setUp() throws Exception {
    super.setUp();
    registerKarateJsExtensionPoint();
  }

  public void testComplicated() {
    doTest();
  }

  public void testRandom() {
    doTest();
  }

  public void testJs() {
    doTest();
  }

  public void testUnformattedkaratejs() {
    doTest();
  }


  @Override protected void doTest() {
    CodeStyleSettingsManager.getInstance(getProject()).runWithLocalSettings(getSettings(), () -> {
      try {
        super.doTest();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  private void registerKarateJsExtensionPoint() {
    ExtensionTestUtil.maskExtensions(KarateJavascriptParsingExtensionPoint.EP_NAME,
      List.of(new KarateJsNoPluginExtension()), getTestRootDisposable());
  }

  @Override
  public String getBasePath() {
    return "testData";
  }

  @Override
  public String getTestDataPath() {
    return "src/test";
  }

  @Override
  public String getFileExtension() {
    return "feature";
  }

  @Override protected CodeStyleSettings getSettings() {
    CodeStyleSettings settings = super.getSettings();
    Objects.requireNonNull(settings.getCommonSettings(JavascriptLanguage.INSTANCE).getIndentOptions()).INDENT_SIZE = 2;
    Objects.requireNonNull(settings.getCommonSettings(KarateJsLanguage.INSTANCE).getIndentOptions()).INDENT_SIZE = 2;
    settings.getIndentOptions().INDENT_SIZE = 2;
    return settings;
  }

  @Override protected CommonCodeStyleSettings getSettings(Language language) {
    CommonCodeStyleSettings settings = super.getSettings(language);
    Objects.requireNonNull(settings.getIndentOptions()).INDENT_SIZE = 2;
    return settings;
  }

  @Override protected @NotNull CodeStyleSettings getCurrentCodeStyleSettings() {
    CodeStyleSettings currentCodeStyleSettings = super.getCurrentCodeStyleSettings();
    currentCodeStyleSettings.getIndentOptions().INDENT_SIZE = 2;
    return currentCodeStyleSettings;
  }
}