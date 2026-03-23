package com.rankweis.uppercut.karate.ui;

import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import io.karatelabs.js.KarateJsNoPluginExtension;
import java.util.List;

public class KarateRenameTest extends BasePlatformTestCase {

  private static final String TEST_CODE = """
    Feature: json schema validation
    
      @ignore
      Scenario: using a third-party lib and a schema file
        * def a = "hi"
        * a = "hello"
    """;

  @Override protected void setUp() throws Exception {
    super.setUp();
    ExtensionTestUtil.maskExtensions(KarateJavascriptParsingExtensionPoint.EP_NAME,
      List.of(new KarateJsNoPluginExtension()), getTestRootDisposable());
  }

  public void testRenameVariable() {
    // Step 1: Create a temporary file in the test project and open it
    myFixture.configureByText("test.feature", TEST_CODE);

    // Step 2: Get the editor for the file
    Editor editor = myFixture.getEditor();
    assertNotNull("Editor should not be null", editor);

    // Locate the first instance of 'a' in the PSI tree
    int offset = TEST_CODE.indexOf("a = \"hi\"");
    myFixture.getEditor().getCaretModel().moveToOffset(offset);

    myFixture.renameElementAtCaret("b");

    // Step 4: Assert the changes
    String expectedCode = """
      Feature: json schema validation
      
        @ignore
        Scenario: using a third-party lib and a schema file
          * def b = "hi"
          * b = "hello"
      """;

    String actualCode = myFixture.getFile().getText();
    assertEquals("Renaming variable 'a' to 'b' failed", expectedCode, actualCode);

  }

  @Override protected String getTestDataPath() {
    return "src/test";
  }

}
