package com.rankweis.uppercut.karate.psi;

import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;

public class KarateHighlightingTest extends CodeInsightFixtureTestCase {

  public void testComplicated() {
    myFixture.setTestDataPath("./src/test/testData");
    myFixture.configureByFile("complicated.feature");
    myFixture.checkHighlighting(true, true, false, true);
  }



}
