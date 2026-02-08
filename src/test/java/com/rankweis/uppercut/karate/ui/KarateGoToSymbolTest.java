package com.rankweis.uppercut.karate.ui;

import com.intellij.navigation.NavigationItem;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.indexing.FindSymbolParameters;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.navigation.KarateChooseByNameContributor;
import com.rankweis.uppercut.karate.psi.KarateDeclaration;
import io.karatelabs.js.KarateJsNoPluginExtension;
import java.util.ArrayList;
import java.util.List;

public class KarateGoToSymbolTest extends BasePlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ExtensionTestUtil.maskExtensions(KarateJavascriptParsingExtensionPoint.EP_NAME,
      List.of(new KarateJsNoPluginExtension()), getTestRootDisposable());
  }

  public void testGoToSymbolFindsDeclaration() {
    myFixture.configureByText("test.feature", """
      Feature: test
        Scenario: symbol test
          * def mySymbol = 1
      """);

    KarateChooseByNameContributor contributor = new KarateChooseByNameContributor();
    List<String> names = new ArrayList<>();
    contributor.processNames(
      names::add, GlobalSearchScope.allScope(getProject()), null);

    assertTrue("mySymbol should be in the symbol list, found: " + names,
      names.contains("mySymbol"));
  }

  public void testGoToSymbolNavigatesToElement() {
    myFixture.configureByText("test.feature", """
      Feature: test
        Scenario: symbol test
          * def navSymbol = "value"
      """);

    KarateChooseByNameContributor contributor = new KarateChooseByNameContributor();
    List<NavigationItem> items = new ArrayList<>();
    FindSymbolParameters params = FindSymbolParameters.simple(getProject(), false);
    contributor.processElementsWithName("navSymbol", items::add, params);

    assertFalse("Should find at least one NavigationItem for navSymbol", items.isEmpty());
    NavigationItem item = items.get(0);
    assertInstanceOf(item, KarateDeclaration.class);
    assertEquals("navSymbol", item.getName());
  }

  @Override
  protected String getTestDataPath() {
    return "src/test";
  }
}
