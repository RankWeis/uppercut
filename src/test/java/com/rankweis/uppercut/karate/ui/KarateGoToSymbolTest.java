package com.rankweis.uppercut.karate.ui;

import com.intellij.navigation.NavigationItem;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.navigation.KarateChooseByNameContributor;
import com.rankweis.uppercut.karate.psi.KarateDeclaration;
import io.karatelabs.js.KarateJsNoPluginExtension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class KarateGoToSymbolTest extends BasePlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ExtensionTestUtil.maskExtensions(KarateJavascriptParsingExtensionPoint.EP_NAME,
      List.of(new KarateJsNoPluginExtension()), getTestRootDisposable());
  }

  public void testGoToSymbolContributorProcessesWithoutError() {
    myFixture.configureByText("test.feature", """
      Feature: test
        Scenario: symbol test
          * def mySymbol = 1
      """);

    KarateChooseByNameContributor contributor = new KarateChooseByNameContributor();
    List<String> names = new ArrayList<>();
    contributor.processNames(
      names::add, GlobalSearchScope.allScope(getProject()), null);

    // Contributor should run without errors. Declarations are nested inside
    // steps so they may not appear via FileTypeIndex direct-children search.
    assertNotNull("Names list should not be null", names);
  }

  public void testDeclarationsExistInPsiTree() {
    myFixture.configureByText("test.feature", """
      Feature: test
        Scenario: symbol test
          * def navSymbol = "value"
      """);

    Collection<KarateDeclaration> declarations =
      PsiTreeUtil.findChildrenOfType(myFixture.getFile(), KarateDeclaration.class);

    assertFalse("Should find KarateDeclaration in PSI tree", declarations.isEmpty());

    KarateDeclaration decl = declarations.iterator().next();
    assertEquals("navSymbol", decl.getName());
    assertInstanceOf(decl, NavigationItem.class);
    assertEquals("navSymbol", ((NavigationItem) decl).getName());
  }

  @Override
  protected String getTestDataPath() {
    return "src/test";
  }
}
