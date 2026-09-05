package com.rankweis.uppercut.testrunner;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The runner used to unconditionally prefix every {@code --testname} with {@code classpath:}, so
 * an edit under {@code src/test/java} did not reflect until {@code target/test-classes} was
 * refreshed by Maven/Gradle. The plugin now sends {@code file:<abs-path>} for file-based runs; the
 * runner has to pass those through and only prefix bare names for backwards compatibility with
 * older serialized run configurations.
 */
public class KarateTestRunnerSchemeTest {

  @Test
  public void bareNameFallsBackToClasspathScheme() {
    assertEquals("classpath:demo/orders.feature:12", KarateTestRunner.withDefaultScheme("demo/orders.feature:12"));
  }

  @Test
  public void fileUrlIsPassedThroughUntouched() {
    // Karate reads directly from the source path - the point of the change - so no double-prefix.
    String url = "file:/Users/foo/src/test/java/demo/orders.feature:12";
    assertEquals(url, KarateTestRunner.withDefaultScheme(url));
  }

  @Test
  public void classpathUrlIsPassedThroughUntouched() {
    String url = "classpath:demo/orders.feature";
    assertEquals(url, KarateTestRunner.withDefaultScheme(url));
  }
}
