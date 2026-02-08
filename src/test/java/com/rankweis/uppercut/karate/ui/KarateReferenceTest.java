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

public class KarateReferenceTest extends BasePlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ExtensionTestUtil.maskExtensions(KarateJavascriptParsingExtensionPoint.EP_NAME,
      List.of(new KarateJsNoPluginExtension()), getTestRootDisposable());
  }

  public void testReferenceResolves() {
    String code = """
      Feature: test
        Scenario: ref test
          * def myVar = "hello"
          * print myVar
      """;
    myFixture.configureByText("test.feature", code);

    GherkinStep printStep = findStepContaining("print myVar");
    assertNotNull("Should find the print step", printStep);

    KarateReference ref = findKarateReference(printStep, "myVar");
    assertNotNull("Should find a KarateReference for myVar", ref);

    PsiElement resolved = ref.resolve();
    assertNotNull("Reference should resolve", resolved);
    assertInstanceOf(resolved, KarateDeclaration.class);
    assertEquals("myVar", ((KarateDeclaration) resolved).getName());
  }

  public void testReferenceResolvesFromBackground() {
    String code = """
      Feature: test
        Background:
          * def bgVar = "hello"
        Scenario: ref test
          * print bgVar
      """;
    myFixture.configureByText("test.feature", code);

    GherkinStep printStep = findStepContaining("print bgVar");
    assertNotNull("Should find the print step", printStep);

    KarateReference ref = findKarateReference(printStep, "bgVar");
    assertNotNull("Should find a KarateReference for bgVar", ref);

    PsiElement resolved = ref.resolve();
    assertNotNull("Background variable reference should resolve", resolved);
    assertInstanceOf(resolved, KarateDeclaration.class);
    assertEquals("bgVar", ((KarateDeclaration) resolved).getName());
  }

  public void testReferenceResolvesInScenarioOutline() {
    String code = """
      Feature: test
        Scenario Outline: outline test
          * def calculate = function(x) { return x * 2; }
          * def result = calculate(1)
          * match result == 2
      """;
    myFixture.configureByText("test.feature", code);

    GherkinStep matchStep = findStepContaining("match result");
    assertNotNull("Should find the match step", matchStep);

    KarateReference ref = findKarateReference(matchStep, "result");
    assertNotNull("Should find a KarateReference for result", ref);

    PsiElement resolved = ref.resolve();
    assertNotNull("Reference in Scenario Outline should resolve", resolved);
    assertInstanceOf(resolved, KarateDeclaration.class);
    assertEquals("result", ((KarateDeclaration) resolved).getName());

    GherkinStep defResultStep = findStepContaining("def result");
    assertNotNull("Should find the def result step", defResultStep);

    KarateReference calcRef = findKarateReference(defResultStep, "calculate");
    assertNotNull("Should find a KarateReference for calculate", calcRef);

    PsiElement calcResolved = calcRef.resolve();
    assertNotNull("calculate reference in Scenario Outline should resolve", calcResolved);
    assertInstanceOf(calcResolved, KarateDeclaration.class);
    assertEquals("calculate", ((KarateDeclaration) calcResolved).getName());
  }

  public void testUnresolvedReference() {
    String code = """
      Feature: test
        Scenario: ref test
          * print undefinedVar
      """;
    myFixture.configureByText("test.feature", code);

    GherkinStep printStep = findStepContaining("print undefinedVar");
    assertNotNull("Should find the print step", printStep);

    KarateReference ref = findKarateReference(printStep, "undefinedVar");
    assertNotNull("Should find a KarateReference for undefinedVar", ref);

    PsiElement resolved = ref.resolve();
    assertNull("Reference to undefined variable should not resolve", resolved);
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
