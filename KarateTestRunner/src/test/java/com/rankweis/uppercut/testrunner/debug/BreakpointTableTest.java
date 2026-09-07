package com.rankweis.uppercut.testrunner.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BreakpointTableTest {

  private final BreakpointTable table = new BreakpointTable();

  private void set(String path, int line) {
    table.clear();
    table.add(path, line);
    table.commit();
  }

  @Test
  void matchesTheClasspathCopyOfTheFileTheUserSetTheBreakpointOn() {
    // The whole reason paths are matched rather than compared: neither is a prefix of the other.
    set("/repo/src/test/java/sample/users.feature", 9);
    assertTrue(table.matches("build/resources/test/sample/users.feature", 9));
    assertTrue(table.matches("target/test-classes/sample/users.feature", 9));
  }

  @Test
  void requiresTheLineToMatchToo() {
    set("/repo/src/test/java/sample/users.feature", 9);
    assertFalse(table.matches("build/resources/test/sample/users.feature", 10));
  }

  @Test
  void doesNotMatchASameNamedFeatureInAnotherDirectory() {
    set("/repo/src/test/java/sample/users.feature", 9);
    assertFalse(table.matches("build/resources/test/other/users.feature", 9));
  }

  @Test
  void matchesAcrossSeparatorStyles() {
    set("C:\\repo\\src\\test\\java\\sample\\users.feature", 9);
    assertTrue(table.matches("build/resources/test/sample/users.feature", 9));
  }

  @Test
  void ignoresLeadingDotSegments() {
    set("./src/test/java/sample/users.feature", 3);
    assertTrue(table.matches("src/test/java/sample/users.feature", 3));
  }

  @Test
  void pendingBreakpointsDoNotApplyUntilCommitted() {
    table.clear();
    table.add("/repo/sample/users.feature", 9);
    assertFalse(table.matches("build/resources/test/sample/users.feature", 9));
    table.commit();
    assertTrue(table.matches("build/resources/test/sample/users.feature", 9));
  }

  @Test
  void commitReplacesTheWholeSetRatherThanAddingToIt() {
    set("/repo/sample/users.feature", 9);
    set("/repo/sample/users.feature", 12);
    assertFalse(table.matches("build/resources/test/sample/users.feature", 9));
    assertTrue(table.matches("build/resources/test/sample/users.feature", 12));
  }

  @Test
  void carriesTheConditionOfTheBreakpointItMatched() {
    table.clear();
    table.add("/repo/sample/users.feature", 9, "id == 'x'");
    table.add("/repo/sample/users.feature", 12, null);
    table.commit();
    assertEquals("id == 'x'", table.conditionAt("build/resources/test/sample/users.feature", 9));
    assertNull(table.conditionAt("build/resources/test/sample/users.feature", 12));
    assertNull(table.conditionAt("build/resources/test/sample/users.feature", 99));
  }

  @Test
  void anEmptyTableMatchesNothing() {
    assertTrue(table.isEmpty());
    assertFalse(table.matches("build/resources/test/sample/users.feature", 9));
    assertFalse(table.matches(null, 9));
  }
}
