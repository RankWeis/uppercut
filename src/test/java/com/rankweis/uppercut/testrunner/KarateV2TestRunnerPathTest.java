package com.rankweis.uppercut.testrunner;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Karate 2's {@code Runner.Builder.path()} does not understand the {@code file:} prefix that v1's
 * {@code Karate.path()} accepts - it silently resolves nothing, and the run ends with
 * "features: 0 | passed: 0 | all passed" and exit code 0. The IDE sends {@code file:<abs-path>} for
 * every file-based run, so the v2 runner strips the scheme; v1 keeps
 * {@link KarateTestRunner#withDefaultScheme} unchanged.
 */
public class KarateV2TestRunnerPathTest {

  @Test
  public void fileSchemeIsStrippedFromThePath() {
    assertEquals("/Users/foo/src/test/java/demo/orders.feature",
      KarateV2TestRunner.toV2Path("file:/Users/foo/src/test/java/demo/orders.feature"));
  }

  @Test
  public void fileSchemeKeepsTheLineFilterSuffix() {
    // v2 reads the trailing :line as a line filter off the bare path, the same way v1 did off the URL.
    assertEquals("/Users/foo/demo/orders.feature:12",
      KarateV2TestRunner.toV2Path("file:/Users/foo/demo/orders.feature:12"));
  }

  @Test
  public void extraSlashesInTheFileUrlCollapse() {
    assertEquals("/Users/foo/demo/orders.feature",
      KarateV2TestRunner.toV2Path("file:///Users/foo/demo/orders.feature"));
  }

  @Test
  public void classpathUrlIsPassedThroughUntouched() {
    assertEquals("classpath:demo/orders.feature", KarateV2TestRunner.toV2Path("classpath:demo/orders.feature"));
  }

  @Test
  public void bareRelativeNameFallsBackToClasspathScheme() {
    assertEquals("classpath:demo/orders.feature:12", KarateV2TestRunner.toV2Path("demo/orders.feature:12"));
  }

  @Test
  public void bareAbsolutePathIsLeftAlone() {
    // An ALL_IN_FOLDER run sends the folder with no scheme; prefixing classpath: would break it.
    assertEquals("/Users/foo/demo", KarateV2TestRunner.toV2Path("/Users/foo/demo"));
  }
}
