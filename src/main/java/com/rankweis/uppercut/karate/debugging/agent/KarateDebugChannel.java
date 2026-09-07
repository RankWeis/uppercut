package com.rankweis.uppercut.karate.debugging.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import com.rankweis.uppercut.testrunner.debug.DebugProtocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The IDE end of the debug channel: listens on a loopback port, accepts the agent that
 * {@code KarateTestRunner} starts in the test JVM, and speaks {@link DebugProtocol} at it.
 *
 * <p>The IDE listens and the agent connects - the same way round as JDWP - so the port is known
 * before anything is launched and nothing has to be scraped out of the process output.</p>
 *
 * <p>Deliberately free of any debugger UI so it can be tested against a plain socket;
 * {@link KarateDebugProcess} is what turns these callbacks into a suspended session.</p>
 */
public final class KarateDebugChannel implements AutoCloseable {

  private static final Logger LOG = Logger.getInstance(KarateDebugChannel.class);

  /**
   * A breakpoint as the IDE knows it: an absolute file path, a 1-based line, and the Karate
   * expression it is conditional on, if any.
   */
  public record Breakpoint(@NotNull String path, int line, @Nullable String condition) {

    public Breakpoint(@NotNull String path, int line) {
      this(path, line, null);
    }
  }

  /** One row of the variables tree, as the agent rendered it. */
  public record Value(@NotNull String name, @NotNull String type, @NotNull String value,
                      boolean hasChildren) {
  }

  /** The answer to an Evaluate: a rendered value, or the message Karate refused it with. */
  public record Evaluated(@Nullable String value, @Nullable String type, @Nullable String error) {
  }

  /** Where the agent stopped, and why. Line is 1-based, as Karate reports it. */
  public record Paused(@NotNull String thread, @NotNull String path, int line,
                       @NotNull String step, @NotNull String scenario,
                       @NotNull String reason, @NotNull String error) {

    /** True when the run stopped because this step failed, rather than at a breakpoint or a step. */
    public boolean isFailure() {
      return "failure".equals(reason);
    }
  }

  public interface Listener {

    void agentConnected();

    void paused(@NotNull Paused paused);

    /** The agent is gone: the test JVM exited, or the channel broke. */
    void agentDisconnected();
  }

  private final ServerSocket server;
  private final Object writeLock = new Object();
  /** Events that arrived before the session existed, replayed when it does. */
  private final List<Runnable> pending = Collections.synchronizedList(new ArrayList<>());

  /** Replies the agent owes us, by request id. */
  private final Map<Integer, CompletableFuture<JsonObject>> requests = new ConcurrentHashMap<>();
  private final AtomicInteger nextRequestId = new AtomicInteger(1);

  private volatile Listener listener;
  private volatile PrintWriter out;
  private volatile Socket agent;
  private volatile boolean closed;
  private volatile List<Breakpoint> breakpoints = List.of();
  /**
   * Whether the session has told us its breakpoints yet. The set doubles as the agent's handshake, so
   * sending an empty one before the session has attached would let the run past a breakpoint the user
   * did set. The agent's own timeout is the backstop if the session never gets that far.
   */
  private volatile boolean breakpointsKnown;
  /**
   * Settings the session asked for before the agent existed. Anything sent while there is no socket
   * is dropped, and the session starts while the test JVM is still booting, so what the session wants
   * is held here and sent the moment it connects - the same reason the breakpoint set is.
   */
  private volatile Boolean pauseOnFailure;

  public KarateDebugChannel() throws IOException {
    this(0);
  }

  /**
   * @param port the port to listen on, or 0 to take a free one. A pinned port is for the case the
   *     run configuration's debug-port field exists for: a container or firewall that only allows
   *     certain ports through to the test JVM.
   */
  public KarateDebugChannel(int port) throws IOException {
    // Backlog of 1 and loopback only: exactly one agent, from this machine, ever connects.
    this.server = new ServerSocket(port, 1, InetAddress.getLoopbackAddress());
  }

  /**
   * Attaches the debug session. The channel is opened before the test JVM is launched, but the
   * session cannot exist until the process does, so anything the agent said in between is replayed
   * here in order. A pause that arrived with nobody listening would otherwise be a parked test JVM
   * with no way to resume it.
   */
  public void setListener(@NotNull Listener attached) {
    listener = attached;
    List<Runnable> replay;
    synchronized (pending) {
      replay = List.copyOf(pending);
      pending.clear();
    }
    replay.forEach(Runnable::run);
  }

  /** The port to hand the test JVM as {@code --debug-port}. */
  public int port() {
    return server.getLocalPort();
  }

  /** Starts accepting the agent. Returns immediately; everything after this is on the reader thread. */
  public void start() {
    Thread reader = new Thread(this::acceptAndRead, "uppercut-karate-debug");
    reader.setDaemon(true);
    reader.start();
  }

  /**
   * Replaces the breakpoint set. Safe before the agent connects - the set is held and sent as soon as
   * it does, which is the normal case: breakpoints exist long before the test JVM starts.
   */
  public void setBreakpoints(@NotNull Collection<Breakpoint> updated) {
    breakpoints = List.copyOf(updated);
    breakpointsKnown = true;
    sendBreakpoints();
  }

  /**
   * The scenario's variables, or the children of one of them.
   *
   * <p>One level at a time: a Karate scenario can hold a whole response body, and the tree is asked
   * for children only when the user opens a node.</p>
   */
  public CompletableFuture<List<Value>> variables(@NotNull String thread, @NotNull List<String> path) {
    return request(id -> DebugProtocol.variablesCommand(thread, id, path))
      .thenApply(KarateDebugChannel::readValues);
  }

  public CompletableFuture<Evaluated> evaluate(@NotNull String thread, @NotNull String expression) {
    return request(id -> DebugProtocol.evaluateCommand(thread, id, expression))
      .thenApply(json -> new Evaluated(
        json.has("value") ? string(json, "value") : null,
        json.has("type") ? string(json, "type") : null,
        json.has("error") ? string(json, "error") : null));
  }

  private CompletableFuture<JsonObject> request(@NotNull java.util.function.IntFunction<String> command) {
    int id = nextRequestId.getAndIncrement();
    CompletableFuture<JsonObject> answer = new CompletableFuture<>();
    requests.put(id, answer);
    if (out == null) {
      requests.remove(id);
      answer.completeExceptionally(new IOException("The test JVM is not connected"));
      return answer;
    }
    send(command.apply(id));
    return answer;
  }

  private static List<Value> readValues(JsonObject json) {
    List<Value> values = new ArrayList<>();
    if (json.has("values") && json.get("values").isJsonArray()) {
      for (JsonElement element : json.getAsJsonArray("values")) {
        JsonObject value = element.getAsJsonObject();
        values.add(new Value(string(value, "name"), string(value, "type"), string(value, "value"),
          value.has("hasChildren") && value.get("hasChildren").getAsBoolean()));
      }
    }
    return values;
  }

  public void resume(@NotNull String thread) {
    send(DebugProtocol.RESUME + " " + thread);
  }

  /** Resume, and stop again at this thread's next step. */
  public void step(@NotNull String thread) {
    send(DebugProtocol.STEP + " " + thread);
  }

  /** Whether a failed step should stop the run. Held until the agent connects if it has not yet. */
  public void setPauseOnFailure(boolean pause) {
    pauseOnFailure = pause;
    send(DebugProtocol.PAUSE_ON_FAILURE + " " + pause);
  }

  public void skipStep(@NotNull String thread) {
    send(DebugProtocol.SKIP + " " + thread);
  }

  /** Lets every parked thread go and stops the agent pausing, without killing the run. */
  public void detach() {
    send(DebugProtocol.DETACH);
  }

  private void acceptAndRead() {
    try (Socket socket = server.accept()) {
      agent = socket;
      socket.setTcpNoDelay(true);
      synchronized (writeLock) {
        out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(),
          StandardCharsets.UTF_8), true);
      }
      dispatch(Listener::agentConnected);
      Boolean pause = pauseOnFailure;
      if (pause != null) {
        // Before the breakpoint set: BREAKPOINTS_END releases the agent to run, and by then it must
        // already know whether a failed step should stop it.
        send(DebugProtocol.PAUSE_ON_FAILURE + " " + pause);
      }
      sendBreakpoints();
      try (BufferedReader in = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = in.readLine()) != null) {
          handle(line);
        }
      }
    } catch (IOException e) {
      if (!closed) {
        LOG.info("Karate debug channel ended", e);
      }
    } finally {
      // Nothing will answer these now; a variables tree waiting forever would just spin.
      requests.values().forEach(answer -> answer.completeExceptionally(new IOException("The test JVM is gone")));
      requests.clear();
      dispatch(Listener::agentDisconnected);
    }
  }

  private void handle(String line) {
    // EVENT <NAME> <json>
    if (!line.startsWith("EVENT ")) {
      return;
    }
    int space = line.indexOf(' ', "EVENT ".length());
    if (space < 0) {
      return;
    }
    String name = line.substring("EVENT ".length(), space);
    JsonObject json = parse(line.substring(space + 1));
    if (json == null) {
      return;
    }
    if ("VARIABLES".equals(name) || "EVALUATED".equals(name)) {
      CompletableFuture<JsonObject> answer =
        json.has("id") ? requests.remove(json.get("id").getAsInt()) : null;
      if (answer != null) {
        answer.complete(json);
      }
      return;
    }
    if ("PAUSED".equals(name)) {
      Paused paused = new Paused(
        string(json, "thread"), string(json, "path"),
        json.has("line") ? json.get("line").getAsInt() : 1,
        string(json, "step"), string(json, "scenario"),
        string(json, "reason"), string(json, "error"));
      dispatch(attached -> attached.paused(paused));
    }
    // HELLO and RESUMED need no action: the session already knows what it asked for, and a resume is
    // confirmed by the run continuing. They stay on the wire because they make a log of it readable.
  }

  private void dispatch(@NotNull java.util.function.Consumer<Listener> event) {
    Listener attached = listener;
    if (attached == null) {
      pending.add(() -> {
        Listener late = listener;
        if (late != null) {
          event.accept(late);
        }
      });
      return;
    }
    event.accept(attached);
  }

  private static @Nullable JsonObject parse(String json) {
    try {
      return JsonParser.parseString(json).getAsJsonObject();
    } catch (JsonSyntaxException | IllegalStateException e) {
      LOG.warn("Unparseable debug event payload: " + json, e);
      return null;
    }
  }

  private static String string(JsonObject json, String key) {
    return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
  }

  private void sendBreakpoints() {
    if (out == null || !breakpointsKnown) {
      return;
    }
    List<String> commands = new ArrayList<>();
    commands.add(DebugProtocol.CLEAR);
    for (Breakpoint breakpoint : breakpoints) {
      commands.add(DebugProtocol.breakpointCommand(
        breakpoint.path(), breakpoint.line(), breakpoint.condition()));
    }
    commands.add(DebugProtocol.BREAKPOINTS_END);
    // One lock for the whole set: BREAKPOINTS_END commits it in the agent, so a set must not be
    // interleaved with another thread's.
    synchronized (writeLock) {
      commands.forEach(this::write);
    }
  }

  private void send(String command) {
    synchronized (writeLock) {
      write(command);
    }
  }

  private void write(String command) {
    PrintWriter writer = out;
    if (writer != null) {
      writer.println(command);
    }
  }

  @Override
  public void close() {
    closed = true;
    try {
      server.close();
    } catch (IOException e) {
      LOG.debug("Closing the debug channel's server socket", e);
    }
    Socket socket = agent;
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException e) {
        LOG.debug("Closing the debug channel's agent socket", e);
      }
    }
  }
}
