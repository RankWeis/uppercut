package com.rankweis.uppercut.karate.ui;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import com.rankweis.uppercut.karate.psi.KarateDeclaration;
import com.rankweis.uppercut.karate.psi.impl.KarateReference;
import io.karatelabs.js.KarateJsNoPluginExtension;
import java.util.Arrays;
import java.util.List;

public class KarateGoToDefinitionTest extends BasePlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ExtensionTestUtil.maskExtensions(KarateJavascriptParsingExtensionPoint.EP_NAME,
      List.of(new KarateJsNoPluginExtension()), getTestRootDisposable());
  }

  public void testGoToDefinition() {
    String code = """
      Feature: test
        Scenario: goto test
          * def target = "hello"
          * print target
      """;
    myFixture.configureByText("test.feature", code);

    GherkinStep printStep = findStepContaining("print target");
    assertNotNull("Should find the print step", printStep);

    KarateReference ref = findKarateReference(printStep, "target");
    assertNotNull("Should find a KarateReference for target", ref);

    PsiElement resolved = ref.resolve();
    assertNotNull("Reference should resolve to definition", resolved);
    assertInstanceOf(resolved, KarateDeclaration.class);
    assertEquals("target", ((KarateDeclaration) resolved).getName());
  }

  public void testGoToDefinitionFromBackground() {
    String code = """
      Feature: test
        Background:
          * def bgTarget = "hello"
        Scenario: goto test
          * print bgTarget
      """;
    myFixture.configureByText("test.feature", code);

    GherkinStep printStep = findStepContaining("print bgTarget");
    assertNotNull("Should find the print step", printStep);

    KarateReference ref = findKarateReference(printStep, "bgTarget");
    assertNotNull("Should find a KarateReference for bgTarget", ref);

    PsiElement resolved = ref.resolve();
    assertNotNull("Background variable should resolve", resolved);
    assertInstanceOf(resolved, KarateDeclaration.class);
    assertEquals("bgTarget", ((KarateDeclaration) resolved).getName());
  }

  private GherkinStep findStepContaining(String text) {
    return PsiTreeUtil.findChildrenOfType(myFixture.getFile(), GherkinStep.class).stream()
      .filter(step -> step.getText().contains(text))
      .findFirst()
      .orElse(null);
  }

  private KarateReference findKarateReference(GherkinStep step, String key) {
    return Arrays.stream(step.getReferences())
      .filter(KarateReference.class::isInstance)
      .map(KarateReference.class::cast)
      .filter(ref -> ref.getKey().equals(key))
      .findFirst()
      .orElse(null);
  }

  @Override
  protected String getTestDataPath() {
    return "src/test";
  }
}
