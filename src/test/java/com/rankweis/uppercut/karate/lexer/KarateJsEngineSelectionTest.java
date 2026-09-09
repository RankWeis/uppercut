package com.rankweis.uppercut.karate.lexer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.rankweis.uppercut.karate.lexer.impl.KarateJavascriptExtension;
import com.rankweis.uppercut.karate.psi.PlainKarateKeywordProvider;
import com.rankweis.uppercut.settings.KarateSettingsState;
import io.karatelabs.js.KarateJsNoPluginExtension;
import java.util.List;

/**
 * Which JavaScript engine lexes an embedded block is decided by position in the extension list:
 * plugin.xml registers the bundled karate-js engine {@code order="last"} and plugin-withJs.xml
 * registers the IntelliJ JavaScript plugin {@code order="first"}, so callers take the last element
 * for the bundled engine and the first for the JS plugin. Nothing in the code says which end is
 * which, and the two halves live in different files, so a reordering would silently swap engines.
 *
 * <p>This pins the selection given that order. It cannot pin the {@code order=} attributes
 * themselves - the extensions are masked here rather than loaded from the descriptors.
 */
public class KarateJsEngineSelectionTest extends LightPlatformTestCase {

  private static final String STEP = "* def now = function() { return 1 }\n";

  private boolean originalSetting;

  @Override public void setUp() throws Exception {
    super.setUp();
    if (!ApplicationManager.getApplication().getExtensionArea()
        .hasExtensionPoint(KarateJavascriptParsingExtensionPoint.EP_NAME.getName())) {
      ApplicationManager.getApplication().getExtensionArea().registerExtensionPoint(
        KarateJavascriptParsingExtensionPoint.EP_NAME.getName(),
        KarateJavascriptParsingExtensionPoint.class.getName(),
        com.intellij.openapi.extensions.ExtensionPoint.Kind.INTERFACE, false);
    }
    // the order the two descriptors produce when the JavaScript plugin is present
    ExtensionTestUtil.maskExtensions(KarateJavascriptParsingExtensionPoint.EP_NAME,
      List.of(new KarateJavascriptExtension(), new KarateJsNoPluginExtension()), getTestRootDisposable());
    originalSetting = KarateSettingsState.getInstance().isUseKarateJavaScriptEngine();
  }

  @Override public void tearDown() throws Exception {
    try {
      KarateSettingsState.getInstance().setUseKarateJavaScriptEngine(originalSetting);
    } finally {
      super.tearDown();
    }
  }

  /** Token name for the {@code function} keyword, which differs between the two engines. */
  private String functionTokenName() {
    UppercutLexer lexer = new UppercutLexer(new PlainKarateKeywordProvider());
    lexer.start(STEP, 0, STEP.length(), 0);
    while (lexer.getTokenType() != null) {
      final IElementType type = lexer.getTokenType();
      if ("function".contentEquals(STEP.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()))) {
        return String.valueOf(type);
      }
      lexer.advance();
    }
    throw new AssertionError("no token covered the 'function' keyword");
  }

  public void testDefaultPrefersJavaScriptPlugin() {
    KarateSettingsState.getInstance().setUseKarateJavaScriptEngine(false);
    assertEquals("with the setting off the IntelliJ JavaScript plugin should lex the block",
      "JS:FUNCTION_KEYWORD", functionTokenName());
  }

  public void testSettingSelectsBundledEngine() {
    KarateSettingsState.getInstance().setUseKarateJavaScriptEngine(true);
    assertEquals("with the setting on the bundled karate-js engine should lex the block",
      "FUNCTION", functionTokenName());
  }
}
