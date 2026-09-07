package com.rankweis.uppercut.karate.debugging.agent;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.remote.RemoteConfigurationType;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The opt-in second debugger: an ordinary <b>Remote JVM Debug</b> session pointed at the JDWP port
 * the test JVM was launched with.
 *
 * <p>Karate has no user-written step definitions, so the JVM debugger is not how you stop on a step -
 * the Karate session does that on both majors. It is for the two things that session cannot reach:
 * Java a feature calls through {@code Java.type}, and karate-core itself.</p>
 *
 * <p><b>Why the platform's own configuration rather than attaching one here.</b>
 * {@link KarateDebugRunner} already claims the Debug executor ahead of the platform's debug runner,
 * so there is no second runner to hand this to; the alternative is
 * {@code DebuggerManagerEx.attachVirtualMachine} and {@code RemoteConnectionBuilder}, which live in
 * {@code com.intellij.debugger.impl}. Running the stock configuration keeps this to public API, and
 * gives the user a real second tab with the full Java debugger - stopping or detaching it leaves the
 * Karate run going.</p>
 *
 * <p><b>The two tabs share one JVM's threads.</b> A Java breakpoint suspends all of them by default,
 * and one of them serves the Karate tab: while the Java tab holds the VM, the Karate tab answers
 * nothing. Nothing is lost - commands sent meanwhile are acted on when the VM resumes - but this is
 * why the JVM debugger is opt-in rather than always on. See {@code docs/DEBUGGER.md}, phase 6.</p>
 */
public final class KarateJvmDebuggerAttach {

  private static final Logger LOG = Logger.getInstance(KarateJvmDebuggerAttach.class);

  /** What the JDWP agent prints once its port is bound - with {@code suspend=n} as well as {@code y}. */
  private static final String AGENT_LISTENING = "Listening for transport dt_socket at address:";
  /** How long to wait for that line before connecting regardless. Inside the channel's 8s hold. */
  private static final long AGENT_WAIT_MILLIS = 5_000L;

  private KarateJvmDebuggerAttach() {
  }

  /**
   * Starts a Remote JVM Debug session against {@code port} and tells {@code channel} when it is
   * attached, so the run's first step waits for it.
   *
   * @param module the run's module, which scopes the source lookup for a stopped location
   * @param process the test JVM, so the attach is abandoned if the run dies before it starts
   */
  public static void attach(@NotNull Project project, int port, @Nullable Module module,
    @Nullable KarateDebugChannel channel, @Nullable ProcessHandler process) {
    RunnerAndConfigurationSettings settings = RunManager.getInstance(project)
      .createConfiguration("Karate JVM debugger",
        RemoteConfigurationType.getInstance().getConfigurationFactories()[0]);
    RemoteConfiguration remote = (RemoteConfiguration) settings.getConfiguration();
    remote.USE_SOCKET_TRANSPORT = true;
    // The test JVM was launched server=y, so the IDE is the one attaching.
    remote.SERVER_MODE = false;
    remote.HOST = "localhost";
    remote.PORT = String.valueOf(port);
    settings.setTemporary(true);
    // The run's own module, so the debugger resolves a stopped location to a source file inside it.
    // The test JVM's classpath is already module-scoped and loads the right class - but mapping the
    // JDI location back to a file is a search over the debug session's scope, and a configuration
    // with no module gets the whole project. Two modules declaring the same class then leave the IDE
    // to pick, and a v2 run can stop showing v1's source.
    if (module != null) {
      remote.setModule(module);
    }

    whenAttached(project, remote.PORT, channel);

    AtomicBoolean launched = new AtomicBoolean();
    Runnable connect = () -> {
      if (!launched.compareAndSet(false, true)) {
        return;
      }
      // executeConfiguration is an EDT call, and this runs on whichever thread saw the agent.
      ApplicationManager.getApplication().invokeLater(() -> {
        try {
          ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance());
        } catch (RuntimeException e) {
          LOG.warn("Could not start the JVM debugger on port " + port, e);
          if (channel != null) {
            channel.jvmDebuggerAttached();
          }
        }
      });
    };

    if (process == null) {
      connect.run();
      return;
    }
    // Wait for the agent to say it is listening. A started process is not a bound JDWP port: the
    // agent binds during JVM startup, and connecting before that is refused - "Unable to open
    // debugger port ... Connection refused", intermittently, depending on how fast the JVM came up.
    // The agent announces itself on stdout even with suspend=n, so the line is the signal.
    process.addProcessListener(new ProcessListener() {
      @SuppressWarnings("rawtypes")
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (event.getText().contains(AGENT_LISTENING)) {
          connect.run();
        }
      }

      @Override public void processTerminated(@NotNull ProcessEvent event) {
        // A run that dies before the debugger attaches would otherwise hold the handshake for its
        // full timeout, for a JVM that is already gone.
        if (channel != null) {
          channel.jvmDebuggerAttached();
        }
      }
    });
    // Fail open, as everything on this path does: if the announcement never arrives - a JVM that
    // does not print it, output routed somewhere unexpected - connect anyway rather than silently
    // never opening the tab. Comfortably inside the handshake hold, so the run still waits for it.
    AppExecutorUtil.getAppScheduledExecutorService()
      .schedule(connect, AGENT_WAIT_MILLIS, TimeUnit.MILLISECONDS);
  }

  /**
   * Picks our remote session out of the ones the project starts, to release the Karate handshake and
   * to make the tab come forward when it stops. {@code processStarted} fires after the platform has
   * the connection, which is the closest thing to "the JVM debugger is watching" the platform offers
   * a listener.
   *
   * <p>Matched on the port rather than on the configuration instance: the platform is free to run a
   * copy of the settings it was handed, and identity that quietly stops matching would leave every
   * such run waiting out the hold's full timeout before its first step. The port is this launch's
   * alone - nothing else is listening on it.</p>
   */
  private static void whenAttached(@NotNull Project project, @NotNull String port,
    @Nullable KarateDebugChannel channel) {
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(XDebuggerManager.TOPIC, new XDebuggerManagerListener() {
      @Override public void processStarted(@NotNull XDebugProcess started) {
        if (!(started.getSession().getRunProfile() instanceof RemoteConfiguration attached)
          || !port.equals(attached.PORT)) {
          return;
        }
        if (channel != null) {
          channel.jvmDebuggerAttached();
        }
        selectThisTabWhenItStops(project, started);
        Disposer.dispose(connection);
      }
    });
  }

  /**
   * Brings the JVM debugger's own tab forward when it stops.
   *
   * <p>Without this a Java breakpoint does not look like it fired: the Debug tool window is already
   * open on the Karate tab, so the platform's "show the debugger on a breakpoint" finds the window
   * showing and leaves the selected tab alone. The run is suspended in a tab nobody is looking at,
   * and the tab that <i>is</i> showing has nothing suspended in it - which reads as a breakpoint
   * that was ignored.</p>
   *
   * <p>The Karate session does the same thing for itself when it pauses, one level in: it selects
   * frames and variables over its own console.</p>
   */
  private static void selectThisTabWhenItStops(@NotNull Project project, @NotNull XDebugProcess started) {
    started.getSession().addSessionListener(new XDebugSessionListener() {
      @Override public void sessionPaused() {
        // toFrontRunContent by process handler, not by descriptor: XDebugSession's descriptor getter
        // is deprecated and logs a throwable in split mode.
        ApplicationManager.getApplication().invokeLater(() ->
          RunContentManager.getInstance(project).toFrontRunContent(
            DefaultDebugExecutor.getDebugExecutorInstance(), started.getProcessHandler()));
      }
    });
  }
}
