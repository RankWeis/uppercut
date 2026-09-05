package com.rankweis.uppercut.karate.run;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

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
    assertEquals(PreferredTest.ALL_IN_FOLDER, configuration.getPreferredTest());
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

  private ConfigurationFromContext configurationFor(PsiDirectory directory) {
    DataContext dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, getProject())
      .add(PlatformCoreDataKeys.MODULE, getModule())
      .add(CommonDataKeys.PSI_ELEMENT, directory)
      .add(CommonDataKeys.VIRTUAL_FILE, directory.getVirtualFile())
      .build();
    ConfigurationContext context = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);
    return new KarateRunConfigurationProducer().createConfigurationFromContext(context);
  }
}
