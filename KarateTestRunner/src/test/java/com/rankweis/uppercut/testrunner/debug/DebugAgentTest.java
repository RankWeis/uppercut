package com.rankweis.uppercut.testrunner.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Exercises the agent against a stand-in for the IDE: a loopback server socket speaking the same
 * protocol. No Karate anywhere - this is the half of the debugger that has to work before the
 * interceptor is worth wiring up.
 */
@Timeout(20)
class DebugAgentTest {

  /** The IDE end of the channel: listens, accepts one agent, and speaks the protocol at it. */
  private static final class FakeIde implements AutoCloseable {

    private final ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    private final CompletableFuture<Socket> accepted;
    private BufferedReader in;
    private PrintWriter out;

    FakeIde() throws IOException {
      accepted = CompletableFuture.supplyAsync(() -> {
        try {
          return server.accept();
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      });
    }

    int port() {
      return server.getLocalPort();
    }

    void attach() throws Exception {
      Socket socket = accepted.get(10, TimeUnit.SECONDS);
      in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      out = new PrintWriter(socket.getOutputStream(), true);
    }

    void send(String line) {
      out.println(line);
    }

    void setBreakpoint(String path, int line) {
      send(DebugProtocol.CLEAR);
      send(DebugProtocol.breakpointCommand(path, line));
      send(DebugProtocol.BREAKPOINTS_END);
    }

    String readLine() throws IOException {
      return in.readLine();
    }

    /** Reads until an EVENT of this name arrives, so a HELLO never has to be counted by hand. */
    String awaitEvent(String name) throws IOException {
      String line;
      while ((line = in.readLine()) != null) {
        if (line.startsWith("EVENT " + name + " ")) {
          return line;
        }
      }
      return null;
    }

    @Override
    public void close() throws IOException {
      server.close();
      accepted.thenAccept(socket -> {
        try {
          socket.close();
        } catch (IOException ignored) {
          // Test teardown.
        }
      });
    }
  }

  private static final String IDE_PATH = "/repo/src/test/java/sample/users.feature";
  private static final String REPORTED_PATH = "build/resources/test/sample/users.feature";

  /** Runs pause() on a named thread so the resume can address it by key. */
  private static Thread pausingThread(DebugAgent agent, String name, int line,
    AtomicReference<DebugAgent.Decision> result, CountDownLatch done) {
    Thread thread = new Thread(() -> {
      result.set(agent.pause(REPORTED_PATH, line, "* def id = 1", "a scenario"));
      done.countDown();
    }, name);
    thread.start();
    return thread;
  }

  @Test
  void pausesOnABreakpointAndResumesWhenTheIdeSaysSo() throws Exception {
    try (FakeIde ide = new FakeIde(); DebugAgent agent = new DebugAgent()) {
      CompletableFuture<Boolean> connected =
        CompletableFuture.supplyAsync(() -> agent.connect(ide.port(), 5000));
      ide.attach();
      String hello = ide.readLine();
      assertTrue(hello.startsWith("EVENT HELLO "), hello);
      assertTrue(hello.contains("\"protocol\":1"), hello);
      ide.setBreakpoint(IDE_PATH, 9);
      assertTrue(connected.get(10, TimeUnit.SECONDS));

      AtomicReference<DebugAgent.Decision> decision = new AtomicReference<>();
      CountDownLatch done = new CountDownLatch(1);
      pausingThread(agent, "scenario-1", 9, decision, done);

      String paused = ide.awaitEvent("PAUSED");
      assertNotNull(paused);
      assertTrue(paused.contains("\"thread\":\"scenario-1\""), paused);
      assertTrue(paused.contains("\"line\":9"), paused);
      assertTrue(paused.contains("\"path\":\"" + REPORTED_PATH + "\""), paused);
      assertTrue(paused.contains("\"step\":\"* def id = 1\""), paused);
      assertFalse(done.await(200, TimeUnit.MILLISECONDS), "the thread must stay parked until resumed");

      ide.send(DebugProtocol.RESUME + " scenario-1");
      assertTrue(done.await(10, TimeUnit.SECONDS));
      assertEquals(DebugAgent.Decision.PROCEED, decision.get());
      assertTrue(ide.awaitEvent("RESUMED").contains("\"action\":\"PROCEED\""));
    }
  }

  @Test
  void servesVariablesAndEvaluationWhileParked() throws Exception {
    try (FakeIde ide = new FakeIde(); DebugAgent agent = new DebugAgent()) {
      CompletableFuture<Boolean> connected =
        CompletableFuture.supplyAsync(() -> agent.connect(ide.port(), 5000));
      ide.attach();
      ide.setBreakpoint(IDE_PATH, 9);
      assertTrue(connected.get(10, TimeUnit.SECONDS));

      Map<String, Object> variables = new LinkedHashMap<>();
      variables.put("id", "abc");
      variables.put("response", Map.of("name", "first"));
      SuspendedFrame frame = new SuspendedFrame() {
        @Override public Map<String, Object> variables() {
          return variables;
        }

        @Override public Object evaluate(String expression) {
          if ("boom".equals(expression)) {
            throw new IllegalStateException("no such variable: boom");
          }
          return "evaluated " + expression;
        }
      };
      CountDownLatch done = new CountDownLatch(1);
      new Thread(() -> {
        agent.pause(REPORTED_PATH, 9, "* def id = 1", "a scenario", frame);
        done.countDown();
      }, "scenario-vars").start();
      assertNotNull(ide.awaitEvent("PAUSED"));

      ide.send(DebugProtocol.variablesCommand("scenario-vars", 1, List.of()));
      String top = ide.awaitEvent("VARIABLES");
      assertTrue(top.contains("\"name\":\"id\""), top);
      assertTrue(top.contains("\"value\":\"abc\""), top);
      assertTrue(top.contains("\"hasChildren\":true"), top);

      ide.send(DebugProtocol.variablesCommand("scenario-vars", 2, List.of("response")));
      String nested = ide.awaitEvent("VARIABLES");
      assertTrue(nested.contains("\"name\":\"name\""), nested);
      assertTrue(nested.contains("\"value\":\"first\""), nested);

      ide.send(DebugProtocol.evaluateCommand("scenario-vars", 3, "id"));
      String evaluated = ide.awaitEvent("EVALUATED");
      assertTrue(evaluated.contains("evaluated id"), evaluated);

      ide.send(DebugProtocol.evaluateCommand("scenario-vars", 4, "boom"));
      String failed = ide.awaitEvent("EVALUATED");
      assertTrue(failed.contains("\"error\""), failed);
      assertTrue(failed.contains("no such variable: boom"), failed);

      ide.send(DebugProtocol.RESUME + " scenario-vars");
      assertTrue(done.await(10, TimeUnit.SECONDS));
    }
  }

  @Test
  void answersRequestsAboutAThreadThatIsNoLongerPaused() throws Exception {
    // The IDE can ask about a tree the user left open when the run has already moved on.
    try (FakeIde ide = new FakeIde(); DebugAgent agent = new DebugAgent()) {
      CompletableFuture<Boolean> connected =
        CompletableFuture.supplyAsync(() -> agent.connect(ide.port(), 5000));
      ide.attach();
      ide.setBreakpoint(IDE_PATH, 9);
      assertTrue(connected.get(10, TimeUnit.SECONDS));

      ide.send(DebugProtocol.variablesCommand("nobody", 7, List.of()));
      String answer = ide.awaitEvent("VARIABLES");
      assertTrue(answer.contains("\"values\":[]"), answer);

      ide.send(DebugProtocol.evaluateCommand("nobody", 8, "id"));
      assertTrue(ide.awaitEvent("EVALUATED").contains("\"error\""));
    }
  }

  @Test
  void skipReturnsTheSkipDecision() throws Exception {
    try (FakeIde ide = new FakeIde(); DebugAgent agent = new DebugAgent()) {
      CompletableFuture<Boolean> connected =
        CompletableFuture.supplyAsync(() -> agent.connect(ide.port(), 5000));
      ide.attach();
      ide.setBreakpoint(IDE_PATH, 9);
      assertTrue(connected.get(10, TimeUnit.SECONDS));

      AtomicReference<DebugAgent.Decision> decision = new AtomicReference<>();
      CountDownLatch done = new CountDownLatch(1);
      pausingThread(agent, "scenario-2", 9, decision, done);
      assertNotNull(ide.awaitEvent("PAUSED"));

      ide.send(DebugProtocol.SKIP + " scenario-2");
      assertTrue(done.await(10, TimeUnit.SECONDS));
      assertEquals(DebugAgent.Decision.SKIP, decision.get());
    }
  }

  @Test
  void aLineWithNoBreakpointNeverPauses() throws Exception {
    try (FakeIde ide = new FakeIde(); DebugAgent agent = new DebugAgent()) {
      CompletableFuture<Boolean> connected =
        CompletableFuture.supplyAsync(() -> agent.connect(ide.port(), 5000));
      ide.attach();
      ide.setBreakpoint(IDE_PATH, 9);
      assertTrue(connected.get(10, TimeUnit.SECONDS));

      assertEquals(DebugAgent.Decision.PROCEED,
        agent.pause(REPORTED_PATH, 10, "* def id = 1", "a scenario"));
    }
  }

  @Test
  void aDroppedConnectionReleasesEveryParkedThread() throws Exception {
    // The failure that must never happen: a test JVM parked on a breakpoint with nobody to resume it.
    FakeIde ide = new FakeIde();
    try (DebugAgent agent = new DebugAgent()) {
      CompletableFuture<Boolean> connected =
        CompletableFuture.supplyAsync(() -> agent.connect(ide.port(), 5000));
      ide.attach();
      ide.setBreakpoint(IDE_PATH, 9);
      assertTrue(connected.get(10, TimeUnit.SECONDS));

      AtomicReference<DebugAgent.Decision> decision = new AtomicReference<>();
      CountDownLatch done = new CountDownLatch(1);
      pausingThread(agent, "scenario-3", 9, decision, done);
      assertNotNull(ide.awaitEvent("PAUSED"));

      ide.close();
      assertTrue(done.await(10, TimeUnit.SECONDS), "a dropped IDE must release the parked thread");
      assertEquals(DebugAgent.Decision.PROCEED, decision.get());
      assertTrue(agent.isDetached());
      assertEquals(DebugAgent.Decision.PROCEED,
        agent.pause(REPORTED_PATH, 9, "* def id = 1", "a scenario"), "and it must stop pausing");
    }
  }

  @Test
  void detachReleasesEveryParkedThread() throws Exception {
    try (FakeIde ide = new FakeIde(); DebugAgent agent = new DebugAgent()) {
      CompletableFuture<Boolean> connected =
        CompletableFuture.supplyAsync(() -> agent.connect(ide.port(), 5000));
      ide.attach();
      ide.setBreakpoint(IDE_PATH, 9);
      assertTrue(connected.get(10, TimeUnit.SECONDS));

      AtomicReference<DebugAgent.Decision> decision = new AtomicReference<>();
      CountDownLatch done = new CountDownLatch(1);
      pausingThread(agent, "scenario-4", 9, decision, done);
      assertNotNull(ide.awaitEvent("PAUSED"));

      ide.send(DebugProtocol.DETACH);
      assertTrue(done.await(10, TimeUnit.SECONDS));
      assertTrue(agent.isDetached());
    }
  }

  @Test
  void noIdeListeningMeansNoDebuggerAndNoFailure() throws Exception {
    int deadPort;
    try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      deadPort = probe.getLocalPort();
    }
    try (DebugAgent agent = new DebugAgent()) {
      assertFalse(agent.connect(deadPort, 1000));
      assertTrue(agent.isDetached());
      assertEquals(DebugAgent.Decision.PROCEED,
        agent.pause(REPORTED_PATH, 9, "* def id = 1", "a scenario"));
    }
  }

  @Test
  void anIdeThatNeverSendsBreakpointsDoesNotHoldTheRunUp() throws Exception {
    try (FakeIde ide = new FakeIde(); DebugAgent agent = new DebugAgent()) {
      long start = System.currentTimeMillis();
      CompletableFuture<Boolean> connected =
        CompletableFuture.supplyAsync(() -> agent.connect(ide.port(), 300));
      ide.attach();
      assertTrue(connected.get(10, TimeUnit.SECONDS));
      assertTrue(System.currentTimeMillis() - start >= 300, "it should wait for the handshake");
      assertEquals(DebugAgent.Decision.PROCEED,
        agent.pause(REPORTED_PATH, 9, "* def id = 1", "a scenario"));
    }
  }
}
