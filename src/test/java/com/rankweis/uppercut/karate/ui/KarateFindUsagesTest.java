package com.rankweis.uppercut.karate.ui;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.usageView.UsageInfo;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import com.rankweis.uppercut.karate.psi.KarateDeclaration;
import io.karatelabs.js.KarateJsNoPluginExtension;
import java.util.Collection;
import java.util.List;

public class KarateFindUsagesTest extends BasePlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ExtensionTestUtil.maskExtensions(KarateJavascriptParsingExtensionPoint.EP_NAME,
      List.of(new KarateJsNoPluginExtension()), getTestRootDisposable());
  }

  public void testFindUsages() {
    String code = """
      Feature: test
        Scenario: find usages
          * def myVar = "hello"
          * print myVar
          * match myVar == "hello"
      """;
    myFixture.configureByText("test.feature", code);

    KarateDeclaration declaration = findDeclaration("myVar");
    assertNotNull("Should find the declaration for myVar", declaration);

    Collection<UsageInfo> usages = myFixture.findUsages(declaration);
    assertNotNull("Usages should not be null", usages);
    assertTrue("Should find at least 2 usages, found: " + usages.size(), usages.size() >= 2);
  }

  public void testFindUsagesFromUsageSite() {
    String code = """
      Feature: test
        Scenario: find usages
          * def usageVar = "hello"
          * print usageVar
      """;
    myFixture.configureByText("test.feature", code);

    KarateDeclaration declaration = findDeclaration("usageVar");
    assertNotNull("Should find the declaration for usageVar", declaration);

    Collection<UsageInfo> usages = myFixture.findUsages(declaration);
    assertNotNull("Usages should not be null", usages);
    assertFalse("Should find at least one usage", usages.isEmpty());
  }

  private KarateDeclaration findDeclaration(String name) {
    return PsiTreeUtil.findChildrenOfType(myFixture.getFile(), KarateDeclaration.class).stream()
      .filter(decl -> name.equals(decl.getName()))
      .findFirst()
      .orElse(null);
  }

  @Override
  protected String getTestDataPath() {
    return "src/test";
  }
}
