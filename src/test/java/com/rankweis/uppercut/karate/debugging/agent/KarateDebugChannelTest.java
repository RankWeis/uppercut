package com.rankweis.uppercut.karate.debugging.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.rankweis.uppercut.testrunner.debug.DebugProtocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

/**
 * The IDE end of the debug channel, driven by a stand-in for the agent. No project or IDE fixture:
 * the channel is deliberately UI-free so this can be a plain socket test.
 */
public class KarateDebugChannelTest {

  private static final int TIMEOUT_SECONDS = 10;

  /**
   * Every test here reads from a socket, and a read that never returns hangs the whole suite rather
   * than failing - which is exactly what a change to when the channel speaks did once.
   */
  @Rule public Timeout timeout = Timeout.seconds(60);

  private KarateDebugChannel channel;
  private Socket agent;

  @After
  public void tearDown() throws IOException {
    if (agent != null) {
      agent.close();
    }
    if (channel != null) {
      channel.close();
    }
  }

  /** Records what the channel reports, so a test can wait for it. */
  private static final class RecordingListener implements KarateDebugChannel.Listener {

    private final CountDownLatch connected = new CountDownLatch(1);
    private final BlockingQueue<KarateDebugChannel.Paused> paused = new ArrayBlockingQueue<>(8);
    private final CountDownLatch disconnected = new CountDownLatch(1);

    @Override public void agentConnected() {
      connected.countDown();
    }

    @Override public void paused(KarateDebugChannel.Paused event) {
      paused.add(event);
    }

    @Override public void agentDisconnected() {
      disconnected.countDown();
    }
  }

  /**
   * Reads past the breakpoint set the channel sends the moment the agent connects. It sends one even
   * when there are no breakpoints: BREAKPOINTS_END is also the handshake, and without it the agent
   * waits out its full timeout before starting the suite.
   */
  private static String readPastBreakpointSet(BufferedReader from) throws IOException {
    String line;
    while ((line = from.readLine()) != null) {
      if (!DebugProtocol.CLEAR.equals(line) && !DebugProtocol.BREAKPOINTS_END.equals(line)
        && !line.startsWith(DebugProtocol.BREAKPOINT + " ")) {
        return line;
      }
    }
    return null;
  }

  private BufferedReader connectAgent() throws IOException {
    agent = new Socket(InetAddress.getLoopbackAddress(), channel.port());
    return new BufferedReader(new InputStreamReader(agent.getInputStream(), StandardCharsets.UTF_8));
  }

  @Test
  public void sendsTheBreakpointSetAsSoonAsTheAgentConnects() throws Exception {
    RecordingListener listener = new RecordingListener();
    channel = new KarateDebugChannel();
    channel.setListener(listener);
    channel.start();
    // Breakpoints exist long before the test JVM does; the channel has to hold them until it can send.
    channel.setBreakpoints(List.of(
      new KarateDebugChannel.Breakpoint("/repo/src/test/java/sample/users.feature", 9),
      new KarateDebugChannel.Breakpoint("/repo/src/test/java/sample/users.feature", 12)));

    BufferedReader fromChannel = connectAgent();
    assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    assertEquals(DebugProtocol.CLEAR, fromChannel.readLine());
    assertEquals(DebugProtocol.breakpointCommand("/repo/src/test/java/sample/users.feature", 9),
      fromChannel.readLine());
    assertEquals(DebugProtocol.breakpointCommand("/repo/src/test/java/sample/users.feature", 12),
      fromChannel.readLine());
    assertEquals(DebugProtocol.BREAKPOINTS_END, fromChannel.readLine());
  }

  @Test
  public void reportsPauseThenSendsResume() throws Exception {
    RecordingListener listener = new RecordingListener();
    channel = new KarateDebugChannel();
    channel.setListener(listener);
    channel.start();
    final BufferedReader fromChannel = connectAgent();
    assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

    PrintWriter toChannel = new PrintWriter(agent.getOutputStream(), true);
    toChannel.println("EVENT PAUSED {\"thread\":\"main\",\"path\":\"build/resources/test/sample/users"
      + ".feature\",\"line\":9,\"step\":\"* def id = 1\",\"scenario\":\"a scenario\"}");

    KarateDebugChannel.Paused paused = listener.paused.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertEquals("main", paused.thread());
    assertEquals("build/resources/test/sample/users.feature", paused.path());
    assertEquals(9, paused.line());
    assertEquals("* def id = 1", paused.step());
    assertEquals("a scenario", paused.scenario());

    channel.resume("main");
    assertEquals(DebugProtocol.RESUME + " main", readPastBreakpointSet(fromChannel));
  }

  @Test
  public void replaysWhatTheAgentSaidBeforeTheSessionExisted() throws Exception {
    // The session cannot be created until the process is, so the agent can connect - and in principle
    // pause - before anyone is listening. A dropped pause would be a test JVM parked with no UI.
    channel = new KarateDebugChannel();
    channel.start();
    connectAgent();
    PrintWriter toChannel = new PrintWriter(agent.getOutputStream(), true);
    toChannel.println("EVENT PAUSED {\"thread\":\"main\",\"path\":\"a.feature\",\"line\":3,"
      + "\"step\":\"* def id = 1\",\"scenario\":\"s\"}");
    Thread.sleep(200);

    RecordingListener listener = new RecordingListener();
    channel.setListener(listener);
    assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    KarateDebugChannel.Paused paused = listener.paused.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertEquals(3, paused.line());
  }

  @Test
  public void holdsTheHandshakeUntilTheJvmDebuggerAttaches() throws Exception {
    // A java breakpoint only binds once the debugger is attached, so a run that opted into the JVM
    // debugger must not let its first step go by before then.
    channel = new KarateDebugChannel();
    channel.start();
    channel.holdForJvmDebugger(60_000);
    BufferedReader fromChannel = connectAgent();
    channel.setBreakpoints(List.of(new KarateDebugChannel.Breakpoint("/repo/a.feature", 4)));

    agent.setSoTimeout(500);
    assertThrows(SocketTimeoutException.class, fromChannel::readLine);

    agent.setSoTimeout(TIMEOUT_SECONDS * 1000);
    channel.jvmDebuggerAttached();
    assertEquals(DebugProtocol.CLEAR, fromChannel.readLine());
    assertEquals(DebugProtocol.breakpointCommand("/repo/a.feature", 4), fromChannel.readLine());
    assertEquals(DebugProtocol.BREAKPOINTS_END, fromChannel.readLine());
  }

  @Test
  public void startsTheRunAnywayWhenTheJvmDebuggerNeverAttaches() throws Exception {
    // Failing open matters more here than anywhere: holding past the agent's own handshake timeout
    // would start the suite with no breakpoint set at all, losing the karate breakpoints as well.
    channel = new KarateDebugChannel();
    channel.start();
    channel.holdForJvmDebugger(200);
    BufferedReader fromChannel = connectAgent();
    channel.setBreakpoints(List.of(new KarateDebugChannel.Breakpoint("/repo/a.feature", 4)));

    assertEquals(DebugProtocol.CLEAR, fromChannel.readLine());
    assertEquals(DebugProtocol.breakpointCommand("/repo/a.feature", 4), fromChannel.readLine());
    assertEquals(DebugProtocol.BREAKPOINTS_END, fromChannel.readLine());
  }

  @Test
  public void completesTheHandshakeOnceTheSessionHasSaidThereAreNoBreakpoints() throws Exception {
    // BREAKPOINTS_END is also the agent's handshake, so an empty set still has to be sent - the run
    // would otherwise wait out the agent's whole timeout before starting.
    RecordingListener listener = new RecordingListener();
    channel = new KarateDebugChannel();
    channel.setListener(listener);
    channel.start();
    BufferedReader fromChannel = connectAgent();
    channel.setBreakpoints(List.of());
    assertEquals(DebugProtocol.CLEAR, fromChannel.readLine());
    assertEquals(DebugProtocol.BREAKPOINTS_END, fromChannel.readLine());
  }

  @Test
  public void saysNothingUntilTheSessionHasAttached() throws Exception {
    // The agent must not be told "no breakpoints" before the session has had its say: that message
    // releases it to run, and it would run straight past a breakpoint the user did set.
    RecordingListener listener = new RecordingListener();
    channel = new KarateDebugChannel();
    channel.setListener(listener);
    channel.start();
    agent = new Socket(InetAddress.getLoopbackAddress(), channel.port());
    assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    agent.setSoTimeout(500);
    BufferedReader fromChannel =
      new BufferedReader(new InputStreamReader(agent.getInputStream(), StandardCharsets.UTF_8));
    assertThrows(SocketTimeoutException.class, fromChannel::readLine);
  }

  @Test
  public void asksForVariablesAndMatchesTheAnswerToTheRequest() throws Exception {
    RecordingListener listener = new RecordingListener();
    channel = new KarateDebugChannel();
    channel.setListener(listener);
    channel.start();
    final BufferedReader fromChannel = connectAgent();
    assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    PrintWriter toChannel = new PrintWriter(agent.getOutputStream(), true);

    final CompletableFuture<List<KarateDebugChannel.Value>> top = channel.variables("main", List.of());
    String request = readPastBreakpointSet(fromChannel);
    assertTrue(request, request.startsWith(DebugProtocol.VARIABLES + " main "));
    String requestId = request.split(" ")[2];

    // Answer out of order, with an unrelated reply first: replies are matched by id, not arrival.
    toChannel.println("EVENT VARIABLES {\"id\":999,\"values\":[]}");
    toChannel.println("EVENT VARIABLES {\"id\":" + requestId + ",\"values\":["
      + "{\"name\":\"id\",\"type\":\"string\",\"value\":\"abc\",\"hasChildren\":false},"
      + "{\"name\":\"response\",\"type\":\"map\",\"value\":\"1 entry\",\"hasChildren\":true}]}");

    List<KarateDebugChannel.Value> values = top.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertEquals(2, values.size());
    assertEquals("id", values.get(0).name());
    assertEquals("abc", values.get(0).value());
    assertFalse(values.get(0).hasChildren());
    assertTrue(values.get(1).hasChildren());
  }

  @Test
  public void reportsAnEvaluationErrorAsAnError() throws Exception {
    RecordingListener listener = new RecordingListener();
    channel = new KarateDebugChannel();
    channel.setListener(listener);
    channel.start();
    final BufferedReader fromChannel = connectAgent();
    assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    PrintWriter toChannel = new PrintWriter(agent.getOutputStream(), true);

    CompletableFuture<KarateDebugChannel.Evaluated> answer = channel.evaluate("main", "id");
    String request = readPastBreakpointSet(fromChannel);
    assertTrue(request, request.startsWith(DebugProtocol.EVALUATE + " main "));
    toChannel.println("EVENT EVALUATED {\"id\":" + request.split(" ")[2]
      + ",\"error\":\"no such variable: id\"}");

    KarateDebugChannel.Evaluated evaluated = answer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertEquals("no such variable: id", evaluated.error());
    assertNull(evaluated.value());
  }

  @Test
  public void failsPendingRequestsWhenTheAgentGoesAway() throws Exception {
    // Otherwise a variables tree spins forever on a run that has already ended.
    RecordingListener listener = new RecordingListener();
    channel = new KarateDebugChannel();
    channel.setListener(listener);
    channel.start();
    connectAgent();
    assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

    CompletableFuture<List<KarateDebugChannel.Value>> pending = channel.variables("main", List.of());
    agent.close();
    assertThrows(ExecutionException.class, () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
  }

  @Test
  public void settingsAskedForBeforeTheAgentConnectedStillReachIt() throws Exception {
    // The session starts while the test JVM is still booting, so anything it asks for then is sent to
    // a socket that does not exist yet. It has to be held and replayed, or a failed step never stops.
    RecordingListener listener = new RecordingListener();
    channel = new KarateDebugChannel();
    channel.setListener(listener);
    channel.start();
    channel.setPauseOnFailure(true);
    channel.setBreakpoints(List.of());

    BufferedReader fromChannel = connectAgent();
    assertEquals(DebugProtocol.PAUSE_ON_FAILURE + " true", fromChannel.readLine());
    assertEquals(DebugProtocol.CLEAR, fromChannel.readLine());
    assertEquals(DebugProtocol.BREAKPOINTS_END, fromChannel.readLine());
  }

  @Test
  public void reportsTheAgentGoingAway() throws Exception {
    RecordingListener listener = new RecordingListener();
    channel = new KarateDebugChannel();
    channel.setListener(listener);
    channel.start();
    connectAgent();
    assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

    agent.close();
    assertTrue(listener.disconnected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
  }

  @Test
  public void ignoresNoiseOnTheWire() throws Exception {
    RecordingListener listener = new RecordingListener();
    channel = new KarateDebugChannel();
    channel.setListener(listener);
    channel.start();
    connectAgent();
    assertTrue(listener.connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

    PrintWriter toChannel = new PrintWriter(agent.getOutputStream(), true);
    toChannel.println("");
    toChannel.println("not an event at all");
    toChannel.println("EVENT PAUSED not-json");
    toChannel.println("EVENT HELLO {\"protocol\":1}");
    toChannel.println("EVENT PAUSED {\"thread\":\"main\",\"path\":\"a.feature\",\"line\":4,"
      + "\"step\":\"* def id = 1\",\"scenario\":\"s\"}");

    KarateDebugChannel.Paused paused = listener.paused.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertEquals("the only well-formed pause must still arrive", 4, paused.line());
  }
}
