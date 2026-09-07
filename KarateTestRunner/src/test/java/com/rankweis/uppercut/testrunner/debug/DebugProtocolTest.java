package com.rankweis.uppercut.testrunner.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.rankweis.uppercut.testrunner.debug.DebugProtocol.Command;
import org.junit.jupiter.api.Test;

class DebugProtocolTest {

  @Test
  void breakpointRoundTripsAPathWithSpacesAndColons() {
    String path = "C:\\Program Files\\my repo\\src\\test\\a b.feature";
    Command command = DebugProtocol.parse(DebugProtocol.breakpointCommand(path, 42));
    assertEquals(DebugProtocol.BREAKPOINT, command.name());
    assertEquals(path, command.path());
    assertEquals(42, command.line());
  }

  @Test
  void breakpointRoundTripsNonAsciiPaths() {
    String path = "/repo/src/test/日本語/ünïcode.feature";
    Command command = DebugProtocol.parse(DebugProtocol.breakpointCommand(path, 1));
    assertEquals(path, command.path());
  }

  @Test
  void parsesVerbsWithAndWithoutArguments() {
    assertEquals(DebugProtocol.CLEAR, DebugProtocol.parse("CLEAR").name());
    assertEquals(DebugProtocol.BREAKPOINTS_END, DebugProtocol.parse("BREAKPOINTS_END").name());
    assertEquals(DebugProtocol.RESUME_ALL, DebugProtocol.parse("RESUME_ALL").name());
    assertEquals(DebugProtocol.DETACH, DebugProtocol.parse("DETACH").name());
    assertEquals("vt-21", DebugProtocol.parse("RESUME vt-21").argument());
    assertEquals("vt-21", DebugProtocol.parse("SKIP vt-21").argument());
  }

  @Test
  void toleratesEveryShapeOfMalformedLine() {
    // A debugger that throws on a bad command leaves the test JVM parked forever.
    assertNull(DebugProtocol.parse(null));
    assertNull(DebugProtocol.parse(""));
    assertNull(DebugProtocol.parse("   "));
    assertNull(DebugProtocol.parse("NONSENSE"));
    assertNull(DebugProtocol.parse("RESUME"));
    assertNull(DebugProtocol.parse("BREAKPOINT only-one-token"));
    assertNull(DebugProtocol.parse("BREAKPOINT " + DebugProtocol.encode("/a.feature") + " not-a-number"));
    assertNull(DebugProtocol.parse("BREAKPOINT !!!not-base64!!! 3"));
  }

  @Test
  void toleratesExtraWhitespace() {
    Command command = DebugProtocol.parse("  RESUME   main  ");
    assertEquals("main", command.argument());
  }

  @Test
  void namesVirtualThreadsByIdBecauseTheyHaveNoName() {
    Thread named = new Thread(() -> { }, "pool-1-thread-3");
    assertEquals("pool-1-thread-3", DebugProtocol.threadKey(named));
    // This jar is compiled for Java 17, so a virtual thread cannot be constructed here; what makes
    // one special to us is only that getName() is blank, which any thread can be.
    Thread unnamed = new Thread(() -> { }, "");
    assertEquals("vt-" + unnamed.getId(), DebugProtocol.threadKey(unnamed));
  }
}
