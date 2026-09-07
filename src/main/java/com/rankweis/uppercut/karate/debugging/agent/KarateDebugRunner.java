package com.rankweis.uppercut.karate.debugging.agent;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.rankweis.uppercut.karate.run.KarateRunConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Runs a Karate configuration under Debug, with the plugin's own debugger and no JVM debugger at all.
 *
 * <p>Karate has no user-written step definitions - the DSL lives inside karate-core - so on a Karate
 * run the JVM debugger was only ever reachable for Java a feature calls through {@code Java.type},
 * and for stepping into Karate itself. Both majors now stop on the step instead, showing the
 * scenario's own variables, so JDWP buys a second debug tab and nothing else. Java called from a
 * feature is still debuggable by running the {@code @Karate.Test} JUnit class through the ordinary
 * Java or Gradle test configuration.</p>
 *
 * <p>Registered ahead of the platform's own debug runner, which would otherwise claim this
 * configuration and attach JDWP.</p>
 */
public class KarateDebugRunner extends GenericProgramRunner<RunnerSettings> {

  @Override
  public @NotNull String getRunnerId() {
    return "KarateDebugRunner";
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && profile instanceof KarateRunConfiguration;
  }

  /**
   * Uses {@code XDebuggerManager.newSessionBuilder}, which the verifier reports as experimental API on
   * 261 and no longer flags on 262 and up. The alternative is
   * {@code XDebugSession.getRunContentDescriptor()}, which is deprecated and logs a throwable in split
   * mode - so this is deliberately the forward-facing half of that choice rather than the quiet one.
   */
  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state,
    @NotNull ExecutionEnvironment environment) throws ExecutionException {
    return XDebuggerManager.getInstance(environment.getProject())
      .newSessionBuilder(new XDebugProcessStarter() {
        @Override
        public @NotNull XDebugProcess start(@NotNull XDebugSession session) throws ExecutionException {
          // Launching here, inside the starter, is what puts the test tree and the debugger in one
          // tab: the session adopts the run's own console and process rather than opening its own.
          ExecutionResult result = state.execute(environment.getExecutor(), KarateDebugRunner.this);
          KarateDebugChannel channel = state instanceof KarateRunConfiguration.DebugChannelHolder holder
            ? holder.debugChannel() : null;
          KarateDebugProcess process = new KarateDebugProcess(session, channel, result);
          if (channel != null) {
            channel.setListener(process);
          }
          return process;
        }
      })
      .environment(environment)
      .startSession()
      .getRunContentDescriptor();
  }
}
