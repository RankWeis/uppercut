package com.rankweis.uppercut.karate.debugging;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import org.junit.Test;

public class UppercutClassLoaderTest {

  @Test
  public void pathsWithSpacesBecomeValidUrls() {
    // The JDK's default location on Windows. Building the URL by string concatenation threw
    // "Illegal character in opaque part at index 19" here, which aborted the whole library scan and
    // left the debugger with no position manager.
    URL url = UppercutClassLoader.toClasspathUrl("C:/Program Files/Zulu/zulu-25/lib/src.zip");
    assertNotNull(url);
    assertTrue(url.toString(), url.toString().contains("Program%20Files"));
    assertTrue(url.toString(), url.toString().endsWith("src.zip"));
  }

  @Test
  public void archiveEntrySuffixIsDroppedSoOnlyTheArchiveIsOnTheClasspath() {
    URL url = UppercutClassLoader.toClasspathUrl(
      "C:/Program Files/Zulu/zulu-25/lib/src.zip!/java.security.sasl");
    assertNotNull(url);
    assertTrue(url.toString(), url.toString().endsWith("src.zip"));
  }

  @Test
  public void plainJarPathsStillResolve() {
    URL url = UppercutClassLoader.toClasspathUrl("/home/user/.m2/karate-core-1.5.1.jar");
    assertNotNull(url);
    assertTrue(url.toString(), url.toString().endsWith("karate-core-1.5.1.jar"));
  }
}
