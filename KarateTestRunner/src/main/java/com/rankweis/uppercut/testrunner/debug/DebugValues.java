package com.rankweis.uppercut.testrunner.debug;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the objects Karate keeps in a scenario into something a variables tree can show: a type, a
 * short preview, and whether it can be opened.
 *
 * <p>Values are rendered one level at a time. A Karate scenario routinely holds a whole HTTP response
 * body, so sending the tree eagerly would put megabytes on the wire for a panel the user may never
 * open; the IDE asks for children by path instead.</p>
 */
public final class DebugValues {

  /** Longer previews are pointless in a one-line tree cell, and expensive when the value is a body. */
  static final int PREVIEW_LIMIT = 200;

  private DebugValues() {
  }

  /** One row in the variables tree. */
  public record Value(String name, String type, String preview, boolean hasChildren) {
  }

  public static Value render(String name, Object value) {
    if (value == null) {
      return new Value(name, "null", "null", false);
    }
    if (value instanceof CharSequence text) {
      return new Value(name, "string", truncate(text.toString()), false);
    }
    if (value instanceof Number || value instanceof Boolean) {
      return new Value(name, value instanceof Boolean ? "boolean" : "number", value.toString(), false);
    }
    if (value instanceof Map<?, ?> map) {
      return new Value(name, "map", preview(map), !map.isEmpty());
    }
    if (value instanceof Collection<?> collection) {
      return new Value(name, "list", preview(collection), !collection.isEmpty());
    }
    if (value.getClass().isArray()) {
      return new Value(name, "list", preview(children(value).values()), Array.getLength(value) > 0);
    }
    // A Java object a feature put in a variable, or one of Karate's own function types.
    return new Value(name, value.getClass().getSimpleName(), truncate(String.valueOf(value)), false);
  }

  /**
   * A one-line look inside a container: {@code {greeting: "hello spike", id: 3}} rather than
   * "2 entries". The count is what the tree cell already implies once it can be opened; what the
   * reader wants at a glance is which keys are in there and what they hold.
   *
   * <p>Built with a budget rather than built-then-truncated: a Karate variable is routinely a whole
   * response body, and rendering megabytes to show 200 characters is the kind of thing that makes a
   * debugger feel slow at exactly the wrong moment.</p>
   */
  private static String preview(Map<?, ?> map) {
    if (map.isEmpty()) {
      return "{}";
    }
    StringBuilder preview = new StringBuilder("{");
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (preview.length() > 1) {
        preview.append(", ");
      }
      if (preview.length() > PREVIEW_LIMIT) {
        return preview.append("...}").toString();
      }
      preview.append(entry.getKey()).append(": ").append(shallow(entry.getValue()));
    }
    return truncateContainer(preview.append('}').toString(), '}');
  }

  private static String preview(Collection<?> items) {
    if (items.isEmpty()) {
      return "[]";
    }
    StringBuilder preview = new StringBuilder("[");
    for (Object item : items) {
      if (preview.length() > 1) {
        preview.append(", ");
      }
      if (preview.length() > PREVIEW_LIMIT) {
        return preview.append("...]").toString();
      }
      preview.append(shallow(item));
    }
    return truncateContainer(preview.append(']').toString(), ']');
  }

  /** One level only: a nested container is a shape, not its contents. */
  private static String shallow(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof CharSequence text) {
      return '"' + truncate(text.toString()) + '"';
    }
    if (value instanceof Number || value instanceof Boolean) {
      return value.toString();
    }
    if (value instanceof Map<?, ?> map) {
      return map.isEmpty() ? "{}" : "{...}";
    }
    if (value instanceof Collection<?> collection) {
      return collection.isEmpty() ? "[]" : "[...]";
    }
    if (value.getClass().isArray()) {
      return Array.getLength(value) == 0 ? "[]" : "[...]";
    }
    return value.getClass().getSimpleName();
  }

  private static String truncateContainer(String preview, char closer) {
    return preview.length() <= PREVIEW_LIMIT ? preview
      : preview.substring(0, PREVIEW_LIMIT) + "..." + closer;
  }

  /** The children of a container, keyed the way {@link #resolve} expects to find them again. */
  public static Map<String, Object> children(Object value) {
    Map<String, Object> children = new LinkedHashMap<>();
    if (value instanceof Map<?, ?> map) {
      map.forEach((key, child) -> children.put(String.valueOf(key), child));
    } else if (value instanceof Collection<?> collection) {
      int index = 0;
      for (Object child : collection) {
        children.put(String.valueOf(index++), child);
      }
    } else if (value != null && value.getClass().isArray()) {
      for (int index = 0; index < Array.getLength(value); index++) {
        children.put(String.valueOf(index), Array.get(value, index));
      }
    }
    return children;
  }

  /**
   * Walks a path of child names from the scenario's variables. Returns null for anything that does
   * not resolve - a variable that has since changed shape, a stale request from a tree the user left
   * open - which the agent reports as an empty child list rather than an error.
   */
  public static Object resolve(Map<String, Object> variables, List<String> path) {
    Object current = variables;
    for (String segment : path) {
      Map<String, Object> children = children(current);
      if (!children.containsKey(segment)) {
        return null;
      }
      current = children.get(segment);
    }
    return current;
  }

  private static String truncate(String text) {
    String oneLine = text.replace("\n", "\\n").replace("\r", "");
    return oneLine.length() <= PREVIEW_LIMIT ? oneLine : oneLine.substring(0, PREVIEW_LIMIT) + "...";
  }
}
