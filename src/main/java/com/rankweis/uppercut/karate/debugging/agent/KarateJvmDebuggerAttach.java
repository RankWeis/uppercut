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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
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

  private KarateJvmDebuggerAttach() {
  }

  /**
   * Starts a Remote JVM Debug session against {@code port} and tells {@code channel} when it is
   * attached, so the run's first step waits for it.
   *
   * @param process the test JVM, so the attach is abandoned if the run dies before it starts
   */
  public static void attach(@NotNull Project project, int port, @Nullable KarateDebugChannel channel,
    @Nullable ProcessHandler process) {
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

    if (channel != null) {
      releaseChannelWhenAttached(project, remote.PORT, channel);
      if (process != null) {
        // A run that dies before the debugger attaches would otherwise hold the handshake for its
        // full timeout, for a JVM that is already gone.
        process.addProcessListener(new ProcessListener() {
          @Override public void processTerminated(@NotNull ProcessEvent event) {
            channel.jvmDebuggerAttached();
          }
        });
      }
    }

    // executeConfiguration is an EDT call, and this runs on whichever thread started the process.
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
  }

  /**
   * Releases the Karate handshake once the remote session is up. {@code processStarted} fires after
   * the platform has the connection, which is the closest thing to "the JVM debugger is watching"
   * the platform offers a listener.
   *
   * <p>Matched on the port rather than on the configuration instance: the platform is free to run a
   * copy of the settings it was handed, and identity that quietly stops matching would leave every
   * such run waiting out the hold's full timeout before its first step. The port is this launch's
   * alone - nothing else is listening on it.</p>
   */
  private static void releaseChannelWhenAttached(@NotNull Project project, @NotNull String port,
    @NotNull KarateDebugChannel channel) {
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(XDebuggerManager.TOPIC, new XDebuggerManagerListener() {
      @Override public void processStarted(@NotNull XDebugProcess started) {
        if (started.getSession().getRunProfile() instanceof RemoteConfiguration attached
          && port.equals(attached.PORT)) {
          channel.jvmDebuggerAttached();
          Disposer.dispose(connection);
        }
      }
    });
  }
}
