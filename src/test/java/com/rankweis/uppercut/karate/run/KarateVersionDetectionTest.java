package com.rankweis.uppercut.karate.run;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.intellij.execution.ExecutionException;
import com.rankweis.uppercut.settings.KarateSettingsState.KarateVersionPreference;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

public class KarateVersionDetectionTest {

  @Test
  public void autoDetectsV2FromJunit6Jar() {
    assertTrue(KarateRunConfiguration.isKarateV2(KarateVersionPreference.AUTO,
      Stream.of("junit-jupiter-5.11.4.jar", "karate-junit6-2.1.1.jar")));
    assertTrue(KarateRunConfiguration.isKarateV2(KarateVersionPreference.AUTO,
      Stream.of("karate-core-2.0.10.jar")));
  }

  @Test
  public void autoDetectsV1FromJunit5Jar() {
    assertFalse(KarateRunConfiguration.isKarateV2(KarateVersionPreference.AUTO,
      Stream.of("karate-junit5-1.5.1.jar", "karate-core-1.5.1.jar")));
    assertFalse(KarateRunConfiguration.isKarateV2(KarateVersionPreference.AUTO, Stream.of()));
  }

  @Test
  public void explicitOverridesBeatClasspath() {
    assertTrue(KarateRunConfiguration.isKarateV2(KarateVersionPreference.V2,
      Stream.of("karate-junit5-1.5.1.jar")));
    assertFalse(KarateRunConfiguration.isKarateV2(KarateVersionPreference.V1,
      Stream.of("karate-junit6-2.1.1.jar")));
  }

  @Test
  public void overrideContradictingTheClasspathIsRefusedUpFront() {
    // Forcing V1 onto a Karate 2 module used to fail deep in the v1 runner with a bare
    // NoSuchMethodException that never mentioned the setting responsible.
    assertThrows(ExecutionException.class, () -> KarateRunConfiguration.checkVersionOverrideMatchesClasspath(
      KarateVersionPreference.V1, List.of("karate-junit6-2.1.1.jar", "karate-core-2.1.1.jar")));
    assertThrows(ExecutionException.class, () -> KarateRunConfiguration.checkVersionOverrideMatchesClasspath(
      KarateVersionPreference.V2, List.of("karate-junit5-1.5.1.jar", "karate-core-1.5.1.jar")));
  }

  @Test
  public void pinWinsWhenBothMajorsOrNeitherAreVisible() throws Exception {
    // A stale transitive karate-core 2.x on a v1 module: AUTO would pick v2 and die; the V1 pin is the
    // documented escape hatch, so it must be honoured rather than refused.
    KarateRunConfiguration.checkVersionOverrideMatchesClasspath(
      KarateVersionPreference.V1, List.of("karate-junit5-1.5.1.jar", "karate-core-1.5.1.jar", "karate-core-2.0.3.jar"));
    // A module mid-migration with both runners present: either pin is a legitimate choice.
    KarateRunConfiguration.checkVersionOverrideMatchesClasspath(
      KarateVersionPreference.V2, List.of("karate-junit5-1.5.1.jar", "karate-junit6-2.1.1.jar"));
    // Unversioned local jars say nothing about the major; the pin is all there is to go on.
    KarateRunConfiguration.checkVersionOverrideMatchesClasspath(
      KarateVersionPreference.V2, List.of("karate-core.jar", "some-lib.jar"));
    KarateRunConfiguration.checkVersionOverrideMatchesClasspath(
      KarateVersionPreference.V1, List.of("karate-core.jar", "some-lib.jar"));
  }

  @Test
  public void unversionedJunit6JarStillMeansKarate2() {
    assertTrue(KarateRunConfiguration.isKarateV2(KarateVersionPreference.AUTO,
      Stream.of("karate-junit6.jar", "karate-core.jar")));
    assertFalse(KarateRunConfiguration.isKarateV2(KarateVersionPreference.AUTO,
      Stream.of("karate-junit5.jar", "karate-core.jar")));
  }

  @Test
  public void overrideMatchingTheClasspathIsAllowed() throws Exception {
    KarateRunConfiguration.checkVersionOverrideMatchesClasspath(
      KarateVersionPreference.V2, List.of("karate-junit6-2.1.1.jar"));
    KarateRunConfiguration.checkVersionOverrideMatchesClasspath(
      KarateVersionPreference.V1, List.of("karate-junit5-1.5.1.jar"));
    // AUTO leaves the decision to detection, whatever is on the classpath
    KarateRunConfiguration.checkVersionOverrideMatchesClasspath(
      KarateVersionPreference.AUTO, List.of("karate-junit6-2.1.1.jar"));
    KarateRunConfiguration.checkVersionOverrideMatchesClasspath(KarateVersionPreference.AUTO, List.of());
  }

  @Test
  public void moduleScanOnlyDecidesWhenTheModuleHasKarate() {
    // Karate jars on a sibling module worked under the old project-wide scan; a module with no
    // karate at all must widen back to the project instead of triggering the bundled fallback.
    assertTrue(KarateRunConfiguration.moduleScanIsAuthoritative(
      Stream.of("junit-jupiter-5.11.4.jar", "karate-junit5-1.5.1.jar")));
    assertTrue(KarateRunConfiguration.moduleScanIsAuthoritative(Stream.of("karate-core-2.1.1.jar")));
    assertFalse(KarateRunConfiguration.moduleScanIsAuthoritative(
      Stream.of("junit-jupiter-5.11.4.jar", "logback-classic-1.5.28.jar")));
    assertFalse(KarateRunConfiguration.moduleScanIsAuthoritative(Stream.of()));
  }

  @Test
  public void classpathWithoutKarateIsRefusedWithTheCause() {
    // The library scan may see Karate through the project-wide library table while the module's own
    // classpath has none of it - a feature outside any module, or a project opened from its pom.xml.
    assertNull(KarateRunConfiguration.classpathProblem(
      List.of("karate-junit5-1.5.1.jar", "karate-core-1.5.1.jar"), "app.test"));
    assertNull(KarateRunConfiguration.classpathProblem(List.of("karate-core-1.5.1.jar"), "app.test"));
    String noModule = KarateRunConfiguration.classpathProblem(List.of("KarateTestRunner.jar"), null);
    assertTrue(noModule, noModule.contains("not inside any module"));
    String noKarate = KarateRunConfiguration.classpathProblem(List.of("junit-jupiter-5.11.4.jar"), "app.test");
    assertTrue(noKarate, noKarate.contains("'app.test'") && noKarate.contains("karate-junit6"));
  }

  @Test
  public void unrelatedJarsWithKaratePrefixDoNotTrigger() {
    assertFalse(KarateRunConfiguration.isKarateV2(KarateVersionPreference.AUTO,
      Stream.of("karate-core-1.5.1.jar", "karate-gatling-2-utils.jar", "my-karate-junit6-2-helper.jar")));
  }
}
