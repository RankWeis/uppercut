package com.rankweis.uppercut.testrunner.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DebugValuesTest {

  @Test
  void rendersTheScalarsAKarateScenarioHolds() {
    assertEquals("null", DebugValues.render("x", null).type());
    assertEquals("string", DebugValues.render("x", "hello").type());
    assertEquals("hello", DebugValues.render("x", "hello").preview());
    assertEquals("number", DebugValues.render("x", 5).type());
    assertEquals("boolean", DebugValues.render("x", true).type());
    assertFalse(DebugValues.render("x", "hello").hasChildren());
  }

  @Test
  void describesContainersBySizeAndOpensThem() {
    DebugValues.Value map = DebugValues.render("response", Map.of("a", 1));
    assertEquals("map", map.type());
    assertEquals("1 entry", map.preview());
    assertTrue(map.hasChildren());

    DebugValues.Value list = DebugValues.render("items", List.of(1, 2));
    assertEquals("list", list.type());
    assertEquals("2 items", list.preview());
    assertTrue(list.hasChildren());
  }

  @Test
  void anEmptyContainerCannotBeOpened() {
    assertFalse(DebugValues.render("x", Map.of()).hasChildren());
    assertFalse(DebugValues.render("x", List.of()).hasChildren());
  }

  @Test
  void keepsPreviewsShortEnoughForATreeCell() {
    // A Karate variable is routinely a whole response body.
    String body = "x".repeat(5000);
    String preview = DebugValues.render("response", body).preview();
    assertEquals(DebugValues.PREVIEW_LIMIT + 3, preview.length());
    assertTrue(preview.endsWith("..."));
  }

  @Test
  void keepsPreviewsOnOneLine() {
    assertEquals("a\\nb", DebugValues.render("x", "a\nb").preview());
  }

  @Test
  void childrenOfAListAreIndexed() {
    Map<String, Object> children = DebugValues.children(List.of("a", "b"));
    assertEquals(List.of("0", "1"), List.copyOf(children.keySet()));
    assertEquals("b", children.get("1"));
  }

  @Test
  void childrenOfAnArrayAreIndexedToo() {
    assertEquals(2, DebugValues.children(new int[]{7, 8}).size());
    assertEquals(7, DebugValues.children(new int[]{7, 8}).get("0"));
  }

  @Test
  void scalarsHaveNoChildren() {
    assertTrue(DebugValues.children("hello").isEmpty());
    assertTrue(DebugValues.children(null).isEmpty());
  }

  @Test
  void resolvesAPathIntoNestedValues() {
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("response", Map.of("items", List.of(Map.of("name", "first"))));
    assertEquals("first", DebugValues.resolve(variables, List.of("response", "items", "0", "name")));
    assertEquals(List.of(), List.copyOf(DebugValues.children("first").keySet()));
  }

  @Test
  void aPathThatNoLongerResolvesIsNullRatherThanAnError() {
    // The IDE can ask about a tree the user left open across a resume.
    Map<String, Object> variables = Map.of("response", Map.of("items", List.of()));
    assertNull(DebugValues.resolve(variables, List.of("response", "items", "3")));
    assertNull(DebugValues.resolve(variables, List.of("gone")));
  }

  @Test
  void anEmptyPathIsTheVariablesThemselves() {
    Map<String, Object> variables = Map.of("id", "abc");
    assertEquals(variables, DebugValues.resolve(variables, List.of()));
  }
}
