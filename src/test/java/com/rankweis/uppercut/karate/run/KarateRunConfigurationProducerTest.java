package com.rankweis.uppercut.karate.run;

import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.List;

/**
 * The run configuration offered on a folder, and whether it displaces the build tool's own
 * "Tests in ..." configuration - the Gradle one replaces every competitor unconditionally, so on a
 * folder with feature files ours has to replace back or it is never offered.
 */
public class KarateRunConfigurationProducerTest extends BasePlatformTestCase {

  public void testFolderWithFeaturesNextToJavaTestsReplacesTheBuildToolRun() {
    // Karate's documented layout: features beside their JUnit runner class under src/test/java
    myFixture.addFileToProject("sample/SampleTest.java", "package sample; public class SampleTest {}");
    PsiFile feature = myFixture.addFileToProject("sample/users.feature", "Feature: users\n");

    ConfigurationFromContext fromContext = configurationFor(feature.getContainingDirectory());

    assertNotNull("no Karate run offered on a folder holding a feature", fromContext);
    KarateRunConfiguration configuration = (KarateRunConfiguration) fromContext.getConfiguration();
    assertEquals("Karate tests in 'sample'", configuration.getName());
    assertEquals(KarateRunConfiguration.PreferredTest.ALL_IN_FOLDER, configuration.getPreferredTest());
    assertTrue("must displace the build tool's folder run, or it is dropped",
      new KarateRunConfigurationProducer().shouldReplace(fromContext, fromContext));
  }

  public void testFolderWithoutFeaturesIsLeftToTheBuildTool() {
    PsiFile test = myFixture.addFileToProject("plain/PlainTest.java", "package plain; public class PlainTest {}");

    ConfigurationFromContext fromContext = configurationFor(test.getContainingDirectory());

    // the folder still gets a (recursive) Karate run, but it must not push the JUnit/Gradle one out
    assertNotNull(fromContext);
    assertFalse(new KarateRunConfigurationProducer().shouldReplace(fromContext, fromContext));
  }

  public void testTagRunsScanTheModuleSourceRootsNotTheModuleDirectory() {
    // A tag run hands Karate directories to walk. Walking the module directory also walks the build
    // output, where Maven/Gradle keep a copy of every feature - and each tagged scenario ran twice.
    List<String> roots = KarateRunConfiguration.tagScanRoots(getModule());
    assertFalse("the light fixture module has a source root", roots.isEmpty());
    for (String root : roots) {
      assertTrue(root, root.startsWith("/") || root.matches("^[A-Za-z]:/.*") || root.startsWith("temp:"));
    }
    assertTrue(KarateRunConfiguration.tagScanRoots(null).isEmpty());
  }

  public void testSingleScenarioRunPointsAtTheScenarioKeywordLineNotTheTagLine() {
    // Karate 1.x matches scenario runs by the `Scenario:` keyword line; passing the tag line skips
    // the scenario and reports zero test events, which the IDE then surfaces as "Test framework quit
    // unexpectedly". The producer used to hand Karate the holder's own text offset, which points at
    // the first tag when a scenario has any.
    PsiFile feature = myFixture.addFileToProject("tagged.feature",
      "Feature: tagged\n"
        + "\n"
        + "  @smoke\n"
        + "  Scenario: Place an order\n"
        + "    * def x = 1\n");
    PsiElement onStep = feature.findElementAt(feature.getText().indexOf("def"));
    KarateRunConfiguration fromStep = (KarateRunConfiguration) configurationFor(onStep).getConfiguration();
    assertEquals(KarateRunConfiguration.PreferredTest.SINGLE_SCENARIO, fromStep.getPreferredTest());
    assertEquals("Scenario keyword sits on line 4; the tag on line 3 is not what Karate matches on",
      4, fromStep.getLineNumber());

    // Right-clicking the tag itself is the ALL_TAGS path (@smoke run); this branch is only here to
    // pin the SINGLE_SCENARIO path used by the gutter/step/keyword clicks.
    PsiElement onTag = feature.findElementAt(feature.getText().indexOf("@smoke"));
    KarateRunConfiguration fromTag = (KarateRunConfiguration) configurationFor(onTag).getConfiguration();
    assertEquals(KarateRunConfiguration.PreferredTest.ALL_TAGS, fromTag.getPreferredTest());
  }

  private ConfigurationFromContext configurationFor(PsiDirectory directory) {
    ConfigurationContext context = ConfigurationContext.createEmptyContextForLocation(
      new PsiLocation<>(getProject(), getModule(), directory));
    return new KarateRunConfigurationProducer().createConfigurationFromContext(context);
  }

  private ConfigurationFromContext configurationFor(PsiElement element) {
    ConfigurationContext context = ConfigurationContext.createEmptyContextForLocation(
      new PsiLocation<>(getProject(), getModule(), element));
    return new KarateRunConfigurationProducer().createConfigurationFromContext(context);
  }
}
