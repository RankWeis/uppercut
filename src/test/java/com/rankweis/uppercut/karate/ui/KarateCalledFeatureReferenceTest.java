package com.rankweis.uppercut.karate.ui;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import com.rankweis.uppercut.karate.psi.KarateDeclaration;
import com.rankweis.uppercut.karate.psi.impl.KarateReference;
import java.util.Arrays;
import java.util.List;

/**
 * {@code auth.token} where {@code auth} is {@code call read('auth.feature')}: the segment after the
 * dot resolves to the {@code def} inside the called feature, not to {@code auth}.
 */
public class KarateCalledFeatureReferenceTest extends BasePlatformTestCase {

  private static final String AUTH = """
    @ignore
    Feature: Bearer token for the calling suite

      Scenario: Sign a short-lived token
        * def clientId = karate.get('clientId', 'checkout')
        * def token = 'eyJ.' + clientId
        * def expiresIn = 3600
    """;

  public void testSegmentAfterCallReadResolvesIntoTheCalledFeature() {
    myFixture.addFileToProject("demo/auth.feature", AUTH);
    PsiFile orders = myFixture.addFileToProject("demo/orders.feature", """
      Feature: Order API

        Background:
          * def auth = call read('auth.feature') { clientId: 'checkout-service' }
          * def bearer = 'Bearer ' + auth.token
      """);

    GherkinStep bearerStep = stepContaining(orders, "auth.token");
    KarateReference token = referenceWithKey(bearerStep, "auth.token");
    KarateReference auth = referenceWithKey(bearerStep, "auth");

    PsiElement tokenTarget = token.resolve();
    assertNotNull("auth.token should resolve into auth.feature", tokenTarget);
    assertEquals("token", ((KarateDeclaration) tokenTarget).getName());
    assertEquals("auth.feature", tokenTarget.getContainingFile().getName());
    // the reference sits on the `token` segment only, so navigation lands there and `auth` keeps its own
    assertEquals("token", token.getRangeInElement().substring(bearerStep.getText()));
    // and, resolving, it is not soft - otherwise the step-definition reference over the whole line
    // outranks it and Cmd-hover underlines the entire step instead of `token`
    assertFalse(token.isSoft());
    assertEquals("auth", ((KarateDeclaration) auth.resolve()).getName());
    assertEquals("orders.feature", auth.resolve().getContainingFile().getName());
  }

  public void testClasspathPathResolvesThroughTheSourceRoot() {
    myFixture.addFileToProject("demo/auth.feature", AUTH);
    PsiFile orders = myFixture.addFileToProject("demo/orders.feature", """
      Feature: Order API

        Scenario: with classpath
          * def auth = call read('classpath:demo/auth.feature@signing')
          * print auth.expiresIn
      """);

    PsiElement target = referenceWithKey(stepContaining(orders, "auth.expiresIn"), "auth.expiresIn").resolve();
    assertNotNull("classpath: path (with a selector) should resolve like read() go-to does", target);
    assertEquals("expiresIn", ((KarateDeclaration) target).getName());
  }

  public void testSegmentOfAnythingElseStaysUnresolvedAndSoft() {
    PsiFile orders = myFixture.addFileToProject("demo/orders.feature", """
      Feature: Order API

        Scenario: json
          * def order = { id: 7 }
          * match order.id == 7
          * match response.items[0].sku == 'KB-118'
      """);

    KarateReference id = referenceWithKey(stepContaining(orders, "order.id"), "order.id");
    assertNull("a JSON literal has no def to land on", id.resolve());
    assertTrue(id.isSoft());
    assertEquals("order", ((KarateDeclaration) referenceWithKey(stepContaining(orders, "order.id"), "order")
      .resolve()).getName());
    assertNull(referenceWithKey(stepContaining(orders, "response.items"), "response.items").resolve());
  }

  private static GherkinStep stepContaining(PsiFile file, String text) {
    return PsiTreeUtil.findChildrenOfType(file, GherkinStep.class).stream()
      .filter(step -> step.getText().contains(text))
      .findFirst()
      .orElseThrow(() -> new AssertionError("no step containing " + text));
  }

  private static KarateReference referenceWithKey(GherkinStep step, String key) {
    List<KarateReference> refs = Arrays.stream(step.getReferences())
      .filter(KarateReference.class::isInstance)
      .map(KarateReference.class::cast)
      .filter(ref -> ref.getKey().equals(key))
      .toList();
    assertEquals("exactly one reference keyed " + key + " on: " + step.getText(), 1, refs.size());
    return refs.get(0);
  }
}
