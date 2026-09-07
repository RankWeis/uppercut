package com.rankweis.uppercut.karate.debugging.agent;

import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.rankweis.uppercut.karate.run.FeaturePathResolver;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Karate side of a Debug run: a second debug session, alongside the Java one that JDWP drives.
 *
 * <p><b>Why two tabs.</b> A Karate 2 Debug run is two debuggers at once: the JVM's, which stops on
 * Java breakpoints in step-definition code, and this one, which stops on lines of a {@code .feature}
 * file. They are separate sessions because the platform has one {@code XDebugProcess} per session,
 * and dropping the JDWP half to get a single tab would take Java breakpoints away from people who
 * have them today. Both attach to the same process, so stopping either stops the run.</p>
 *
 * <p>Phase 2 of {@code docs/DEBUGGER.md}: pause, highlight, resume. Variables, evaluation and real
 * stepping come next; until then the step actions continue to the next breakpoint and say so.</p>
 */
public class KarateDebugProcess extends XDebugProcess implements KarateDebugChannel.Listener {

  private final Project project;
  private final KarateDebugChannel channel;
  private final ProcessHandler processHandler;
  private final ConsoleView console;
  private final XDebuggerEditorsProvider editorsProvider = new KarateDebugEditorsProvider();
  private final Set<XLineBreakpoint<XBreakpointProperties<?>>> breakpoints =
    Collections.synchronizedSet(new LinkedHashSet<>());
  /** One instance: a second handler would take its own half of the registerBreakpoint calls. */
  private final XBreakpointHandler<?>[] breakpointHandlers = {new Handler()};

  private volatile String stateMessage = "Waiting for the test JVM to connect";

  public KarateDebugProcess(@NotNull XDebugSession session, @NotNull KarateDebugChannel channel,
    @NotNull ProcessHandler processHandler) {
    super(session);
    this.project = session.getProject();
    this.channel = channel;
    this.processHandler = processHandler;
    this.console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
  }

  @Override
  public @NotNull XDebuggerEditorsProvider getEditorsProvider() {
    return editorsProvider;
  }

  @Override
  public XBreakpointHandler<?> @NotNull [] getBreakpointHandlers() {
    return breakpointHandlers;
  }

  @Override
  protected ProcessHandler doGetProcessHandler() {
    return processHandler;
  }

  /**
   * A console of this session's own, deliberately not attached to the process: the test output
   * already has a home in the run tab, and attaching a second console to the same handler would print
   * every line twice. What lands here is the debug channel's own story - connected, paused, resumed -
   * which is otherwise invisible.
   */
  @Override
  public @NotNull ExecutionConsole createConsole() {
    return console;
  }

  @Override
  public void sessionInitialized() {
    log("Karate debugger: listening on port " + channel.port() + " for the test JVM.");
    log("Test output and Java breakpoints are in the other Debug tab.");
  }

  @Override
  public String getCurrentStateMessage() {
    return stateMessage;
  }

  // ---- channel callbacks, all on the channel's reader thread ----

  @Override
  public void agentConnected() {
    stateMessage = "Connected to the test JVM";
    log("Test JVM connected; " + breakpoints.size() + " feature-file breakpoint(s) set.");
  }

  @Override
  public void paused(KarateDebugChannel.@NotNull Paused paused) {
    stateMessage = "Paused at " + paused.path() + ":" + paused.line();
    log("Paused at " + paused.path() + ":" + paused.line() + "  " + paused.step());
    getSession().positionReached(new KarateSuspendContext(paused, sourcePosition(paused)));
    showThisTab();
  }

  @Override
  public void agentDisconnected() {
    stateMessage = "Test JVM disconnected";
    log("Test JVM disconnected.");
  }

  // ---- session commands ----

  @Override
  public void resume(@Nullable XSuspendContext context) {
    String thread = threadOf(context);
    if (thread != null) {
      channel.resume(thread);
    }
  }

  @Override
  public void startStepOver(@Nullable XSuspendContext context) {
    continueInsteadOfStepping(context);
  }

  @Override
  public void startStepInto(@Nullable XSuspendContext context) {
    continueInsteadOfStepping(context);
  }

  @Override
  public void startStepOut(@Nullable XSuspendContext context) {
    continueInsteadOfStepping(context);
  }

  @Override
  public void stop() {
    // Detach first so any thread parked on a breakpoint is released, then drop the channel. Killing
    // the process while a thread is parked would leave the run's output truncated mid-step.
    channel.detach();
    channel.close();
  }

  private void continueInsteadOfStepping(@Nullable XSuspendContext context) {
    log("Stepping is not supported yet - continuing to the next breakpoint.");
    resume(context);
  }

  /**
   * Brings the Karate tab forward on a pause.
   *
   * <p>Two sessions share the Debug tool window, and only the selected one draws its execution line.
   * Without this, hitting a feature-file breakpoint stops the run under a Java tab that has nothing to
   * show and no highlight anywhere - the user has to know to switch tabs to see where they are.</p>
   */
  private void showThisTab() {
    ApplicationManager.getApplication().invokeLater(() -> {
      RunContentDescriptor descriptor = getSession().getRunContentDescriptor();
      if (descriptor != null) {
        RunContentManager.getInstance(project)
          .toFrontRunContent(DefaultDebugExecutor.getDebugExecutorInstance(), descriptor);
      }
    }, ModalityState.any());
  }

  private @Nullable String threadOf(@Nullable XSuspendContext context) {
    return context instanceof KarateSuspendContext karate ? karate.thread() : null;
  }

  private @Nullable XSourcePosition sourcePosition(KarateDebugChannel.Paused paused) {
    VirtualFile file = ReadAction.compute(() -> FeaturePathResolver.findFeatureFile(project, paused.path()));
    if (file == null) {
      log("Could not find " + paused.path() + " in this project, so the paused line cannot be shown.");
      return null;
    }
    // Karate reports 1-based lines; XSourcePosition counts from 0.
    return XDebuggerUtil.getInstance().createPosition(file, paused.line() - 1);
  }

  private void log(String message) {
    console.print(message + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
  }

  /** Sends the whole breakpoint set on every change; the agent commits it atomically. */
  private final class Handler extends XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<?>>> {

    private Handler() {
      super(KarateBreakpointType.class);
    }

    @Override
    public void registerBreakpoint(@NotNull XLineBreakpoint<XBreakpointProperties<?>> breakpoint) {
      breakpoints.add(breakpoint);
      push();
    }

    @Override
    public void unregisterBreakpoint(@NotNull XLineBreakpoint<XBreakpointProperties<?>> breakpoint,
      boolean temporary) {
      breakpoints.remove(breakpoint);
      push();
    }

    private void push() {
      List<KarateDebugChannel.Breakpoint> wire;
      synchronized (breakpoints) {
        wire = breakpoints.stream()
          .map(breakpoint -> new KarateDebugChannel.Breakpoint(
            VfsUtilCore.urlToPath(breakpoint.getFileUrl()),
            // XLineBreakpoint counts lines from 0, Karate from 1.
            breakpoint.getLine() + 1))
          .toList();
      }
      channel.setBreakpoints(wire);
    }
  }

  /** One paused Karate thread, with the one frame this phase knows how to build. */
  private static final class KarateSuspendContext extends XSuspendContext {

    private final KarateDebugChannel.Paused paused;
    private final XExecutionStack stack;

    private KarateSuspendContext(KarateDebugChannel.Paused paused, @Nullable XSourcePosition position) {
      this.paused = paused;
      this.stack = new KarateExecutionStack(paused, position);
    }

    String thread() {
      return paused.thread();
    }

    @Override
    public @Nullable XExecutionStack getActiveExecutionStack() {
      return stack;
    }

    @Override
    public XExecutionStack @NotNull [] getExecutionStacks() {
      return new XExecutionStack[]{stack};
    }
  }

  private static final class KarateExecutionStack extends XExecutionStack {

    private final XStackFrame topFrame;

    private KarateExecutionStack(KarateDebugChannel.Paused paused, @Nullable XSourcePosition position) {
      super(paused.scenario().isEmpty() ? paused.thread() : paused.scenario());
      this.topFrame = new KarateStackFrame(paused, position);
    }

    @Override
    public @Nullable XStackFrame getTopFrame() {
      return topFrame;
    }

    @Override
    public void computeStackFrames(int firstFrameIndex, @NotNull XStackFrameContainer container) {
      // One frame for now. Called features nest through FeatureRuntime.callDepth, which is what turns
      // this into a real stack in a later phase.
      container.addStackFrames(firstFrameIndex == 0 ? List.of(topFrame) : List.of(), true);
    }
  }

  private static final class KarateStackFrame extends XStackFrame {

    private final KarateDebugChannel.Paused paused;
    private final XSourcePosition position;

    private KarateStackFrame(KarateDebugChannel.Paused paused, @Nullable XSourcePosition position) {
      this.paused = paused;
      this.position = position;
    }

    @Override
    public @Nullable XSourcePosition getSourcePosition() {
      return position;
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
      String step = paused.step().isEmpty() ? "step" : paused.step();
      component.append(step, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      component.append("  " + shortName() + ":" + paused.line(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    private String shortName() {
      String path = paused.path().replace('\\', '/');
      int slash = path.lastIndexOf('/');
      return slash < 0 ? path : path.substring(slash + 1);
    }
  }
}
