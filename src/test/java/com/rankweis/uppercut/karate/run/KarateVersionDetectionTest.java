package com.rankweis.uppercut.karate.run;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.rankweis.uppercut.settings.KarateSettingsState.KarateVersionPreference;
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
  public void unrelatedJarsWithKaratePrefixDoNotTrigger() {
    assertFalse(KarateRunConfiguration.isKarateV2(KarateVersionPreference.AUTO,
      Stream.of("karate-core-1.5.1.jar", "karate-gatling-2-utils.jar", "my-karate-junit6-2-helper.jar")));
  }
}
