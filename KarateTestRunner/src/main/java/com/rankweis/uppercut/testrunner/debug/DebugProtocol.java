package com.rankweis.uppercut.testrunner.debug;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * The wire format between the IDE and the debug agent in the test JVM. One message per line, both
 * directions, over a plain socket the IDE listens on (see {@link DebugAgent}).
 *
 * <p><b>IDE → agent</b> is whitespace-separated tokens, because the agent must parse it and this jar
 * carries no JSON parser. Anything that can contain a space, a colon or a backslash - which is to say
 * every file path - travels base64-encoded, so a Windows path or a directory with a space in it needs
 * no escaping rules that both ends have to agree on:</p>
 *
 * <pre>
 *   CLEAR                        begin a new breakpoint set
 *   BREAKPOINT &lt;base64 path&gt; &lt;line&gt;
 *   BREAKPOINTS_END              commit the set; also the "you may start" signal at startup
 *   RESUME &lt;threadKey&gt;           continue the step that thread is paused on
 *   SKIP &lt;threadKey&gt;             skip that step and continue (Karate's SKIP action)
 *   RESUME_ALL                   release every paused thread
 *   DETACH                       release everything and stop pausing for the rest of the run
 *   VARIABLES &lt;thread&gt; &lt;id&gt; [&lt;base64 path&gt;]   children of a value, or the scenario's variables
 *   EVALUATE &lt;thread&gt; &lt;id&gt; &lt;base64 expression&gt;
 * </pre>
 *
 * <p>{@code id} correlates a request with its reply; a value path is its child names joined by
 * newlines, base64-encoded, because a JSON key can contain anything a separator might use.</p>
 *
 * <p><b>Agent → IDE</b> is {@code EVENT <NAME> <json>}, matching the {@code <<UPPERCUT-V2>>} event
 * stream the IDE already parses, so the IDE side reuses its JSON reader:</p>
 *
 * <pre>
 *   HELLO  {"protocol":1,"karateMajor":2}
 *   PAUSED {"thread":"...","path":"...","line":9,"step":"...","scenario":"..."}
 *   RESUMED{"thread":"...","action":"PROCEED|SKIP"}
 *   VARIABLES {"id":1,"values":[{"name":"id","type":"string","value":"...","hasChildren":false}]}
 *   EVALUATED {"id":2,"value":"...","type":"string"}          (or {"id":2,"error":"..."})
 * </pre>
 *
 * <p>The thread key is the same one {@code KarateV2TestRunner} stamps on every forwarded run event,
 * so the IDE can line a PAUSED up with the scenario node it already built.</p>
 */
public final class DebugProtocol {

  /** Bumped only if a message changes shape incompatibly; the IDE refuses a version it doesn't know. */
  public static final int VERSION = 1;

  public static final String CLEAR = "CLEAR";
  public static final String BREAKPOINT = "BREAKPOINT";
  public static final String BREAKPOINTS_END = "BREAKPOINTS_END";
  public static final String RESUME = "RESUME";
  public static final String SKIP = "SKIP";
  public static final String RESUME_ALL = "RESUME_ALL";
  public static final String DETACH = "DETACH";
  public static final String VARIABLES = "VARIABLES";
  public static final String EVALUATE = "EVALUATE";

  private DebugProtocol() {
  }

  /**
   * One parsed IDE → agent line. {@code path} and {@code line} carry the breakpoint for BREAKPOINT;
   * {@code argument} is the thread for the rest, with {@code requestId} and {@code payload} set for
   * the two that expect a reply.
   */
  public record Command(String name, String argument, String path, int line, int requestId, String payload) {

    static Command of(String name) {
      return new Command(name, null, null, -1, -1, null);
    }

    static Command of(String name, String argument) {
      return new Command(name, argument, null, -1, -1, null);
    }

    static Command breakpoint(String path, int line) {
      return new Command(BREAKPOINT, null, path, line, -1, null);
    }

    static Command request(String name, String thread, int requestId, String payload) {
      return new Command(name, thread, null, -1, requestId, payload);
    }
  }

  /**
   * Parses one IDE → agent line, or returns null for anything unrecognised - a blank line, an unknown
   * verb, a BREAKPOINT whose line number is not a number. A debugger that dies on a malformed command
   * leaves the test JVM parked forever, so every parse failure is simply ignored.
   */
  public static Command parse(String line) {
    if (line == null) {
      return null;
    }
    String trimmed = line.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    List<String> tokens = List.of(trimmed.split("\\s+"));
    String verb = tokens.get(0);
    switch (verb) {
      case CLEAR, BREAKPOINTS_END, RESUME_ALL, DETACH:
        return Command.of(verb);
      case RESUME, SKIP:
        return tokens.size() < 2 ? null : Command.of(verb, tokens.get(1));
      case BREAKPOINT:
        if (tokens.size() < 3) {
          return null;
        }
        try {
          return Command.breakpoint(decode(tokens.get(1)), Integer.parseInt(tokens.get(2)));
        } catch (IllegalArgumentException e) {
          return null;
        }
      case VARIABLES:
      case EVALUATE:
        // VARIABLES may omit the payload: no path means the scenario's own variables.
        if (tokens.size() < 3 || (EVALUATE.equals(verb) && tokens.size() < 4)) {
          return null;
        }
        try {
          String payload = tokens.size() > 3 ? decode(tokens.get(3)) : "";
          return Command.request(verb, tokens.get(1), Integer.parseInt(tokens.get(2)), payload);
        } catch (IllegalArgumentException e) {
          return null;
        }
      default:
        return null;
    }
  }

  /** Renders a BREAKPOINT line. Used by the IDE side and by the tests that stand in for it. */
  public static String breakpointCommand(String path, int line) {
    return BREAKPOINT + " " + encode(path) + " " + line;
  }

  /** Renders a VARIABLES request. An empty path asks for the scenario's own variables. */
  public static String variablesCommand(String thread, int requestId, List<String> path) {
    String command = VARIABLES + " " + thread + " " + requestId;
    return path.isEmpty() ? command : command + " " + encode(joinPath(path));
  }

  public static String evaluateCommand(String thread, int requestId, String expression) {
    return EVALUATE + " " + thread + " " + requestId + " " + encode(expression);
  }

  /** A value path is its child names joined by newlines: a JSON key can contain any other separator. */
  public static String joinPath(List<String> path) {
    return String.join("\n", path);
  }

  public static List<String> splitPath(String path) {
    return path.isEmpty() ? List.of() : List.of(path.split("\n", -1));
  }

  public static String encode(String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  public static String decode(String value) {
    return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
  }

  /**
   * The key both channels use to name a thread. Karate 2 runs scenarios on virtual threads, which are
   * unnamed - {@code getName()} is "" for all of them, so every thread would collapse onto one key.
   */
  public static String threadKey(Thread thread) {
    String name = thread.getName();
    return name == null || name.isBlank() ? "vt-" + thread.getId() : name;
  }
}
