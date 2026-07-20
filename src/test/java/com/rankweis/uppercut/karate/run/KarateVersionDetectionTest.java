package com.rankweis.uppercut.karate.run;

import static org.junit.Assert.assertFalse;
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
  public void unrelatedJarsWithKaratePrefixDoNotTrigger() {
    assertFalse(KarateRunConfiguration.isKarateV2(KarateVersionPreference.AUTO,
      Stream.of("karate-core-1.5.1.jar", "karate-gatling-2-utils.jar", "my-karate-junit6-2-helper.jar")));
  }
}
