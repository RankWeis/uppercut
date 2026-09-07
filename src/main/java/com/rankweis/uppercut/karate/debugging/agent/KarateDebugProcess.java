package com.rankweis.uppercut.karate.debugging.agent;

import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.Content;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.rankweis.uppercut.karate.run.FeaturePathResolver;
import com.rankweis.uppercut.settings.KarateSettingsState;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The debugger for a Karate run, on both majors: breakpoints on lines of a {@code .feature} file,
 * the scenario's variables, and evaluation against the paused scenario.
 *
 * <p>One session, one tab: it adopts the run's own console - the test tree - so the run and the
 * debugger are the same thing, which is what {@link KarateDebugRunner} launches it for. Stepping is
 * not built; the step actions continue to the next breakpoint and say so.</p>
 */
public class KarateDebugProcess extends XDebugProcess implements KarateDebugChannel.Listener {

  private final Project project;
  private final @Nullable KarateDebugChannel channel;
  private final ProcessHandler processHandler;
  private final ExecutionConsole console;
  private final XDebuggerEditorsProvider editorsProvider = new KarateDebugEditorsProvider();
  private final Set<XLineBreakpoint<XBreakpointProperties<?>>> breakpoints =
    Collections.synchronizedSet(new LinkedHashSet<>());
  /** One instance: a second handler would take its own half of the registerBreakpoint calls. */
  private final XBreakpointHandler<?>[] breakpointHandlers = {new Handler()};

  private volatile String stateMessage = "Waiting for the test JVM to connect";

  public KarateDebugProcess(@NotNull XDebugSession session, @Nullable KarateDebugChannel channel,
    @NotNull ExecutionResult executionResult) {
    super(session);
    this.project = session.getProject();
    this.channel = channel;
    this.processHandler = executionResult.getProcessHandler();
    this.console = executionResult.getExecutionConsole();
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

  /** The run's own console - the Karate test tree - so a debug run is one tab, not two. */
  @Override
  public @NotNull ExecutionConsole createConsole() {
    return console;
  }

  /**
   * Sends the breakpoint set exactly once the handlers have registered what exists, whether or not
   * there is anything to send: that message doubles as the agent's handshake, and until it arrives
   * the test JVM waits rather than running past a breakpoint.
   */
  @Override
  public void sessionInitialized() {
    if (channel == null) {
      return;
    }
    channel.setPauseOnFailure(KarateSettingsState.getInstance().isPauseOnFailedStep());
    pushBreakpoints();
  }

  @Override
  public String getCurrentStateMessage() {
    return stateMessage;
  }

  // ---- channel callbacks, all on the channel's reader thread ----

  @Override
  public void agentConnected() {
    stateMessage = "Connected to the test JVM";
  }

  @Override
  public void paused(KarateDebugChannel.@NotNull Paused paused) {
    stateMessage = paused.isFailure()
      ? "Stopped on a failed step at " + paused.path() + ":" + paused.line()
      : "Paused at " + paused.path() + ":" + paused.line();
    if (paused.isFailure()) {
      // Say why the run stopped somewhere the user did not ask it to.
      report("Step failed: " + (paused.error().isEmpty() ? paused.step() : paused.error()));
    } else if (!paused.error().isEmpty()) {
      // A breakpoint whose condition would not evaluate: it stopped anyway, and this says why.
      report(paused.error());
    }
    getSession().positionReached(new KarateSuspendContext(paused, sourcePosition(paused), channel));
    showVariables();
  }

  @Override
  public void agentDisconnected() {
    stateMessage = "Test JVM disconnected";
  }

  // ---- session commands ----

  @Override
  public void resume(@Nullable XSuspendContext context) {
    String thread = threadOf(context);
    if (thread != null && channel != null) {
      channel.resume(thread);
    }
  }

  /**
   * All three step actions do the same thing: run to the next step.
   *
   * <p>Karate's steps are the only place the run can stop, so "next step" is what stepping means
   * here. It does not yet distinguish over from into: a step that calls another feature stops on the
   * called feature's first step rather than after the call. Telling them apart needs the call depth,
   * which both majors expose - see {@code docs/DEBUGGER.md}.</p>
   */
  @Override
  public void startStepOver(@Nullable XSuspendContext context) {
    step(context);
  }

  @Override
  public void startStepInto(@Nullable XSuspendContext context) {
    step(context);
  }

  @Override
  public void startStepOut(@Nullable XSuspendContext context) {
    step(context);
  }

  @Override
  public void stop() {
    // Detach first so any thread parked on a breakpoint is released, then drop the channel. Killing
    // the process while a thread is parked would leave the run's output truncated mid-step.
    if (channel != null) {
      channel.detach();
      channel.close();
    }
  }

  private void step(@Nullable XSuspendContext context) {
    String thread = threadOf(context);
    if (thread != null && channel != null) {
      channel.step(thread);
    }
  }

  /**
   * Adds "Skip Step" beside the stepping actions: continue the run without executing the step it is
   * stopped on. Karate's own {@code SKIP} makes this exact, and it is the one thing a Karate debugger
   * can offer that a JVM one cannot - a step that is failing for an uninteresting reason (an
   * environment call, a fixture that is down) can be stepped past to reach the part being debugged.
   */
  @Override
  public void registerAdditionalActions(@NotNull DefaultActionGroup leftToolbar,
    @NotNull DefaultActionGroup topToolbar, @NotNull DefaultActionGroup settings) {
    leftToolbar.add(new PauseOnFailedStepToggle());
    topToolbar.add(new AnAction("Skip Step", "Continue without running the step the run is stopped on",
      AllIcons.Actions.Play_forward) {

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(
          channel != null && threadOf(getSession().getSuspendContext()) != null);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        String thread = threadOf(getSession().getSuspendContext());
        if (thread != null && channel != null) {
          channel.skipStep(thread);
        }
      }
    });
  }

  /**
   * Brings this tab forward and shows frames and variables when the run stops.
   *
   * <p>Two levels of selecting. The tab itself, because a run that opted into the JVM debugger has a
   * second tab in the same tool window - opened after this one, so possibly the selected one - and
   * the platform's "show the debugger on a breakpoint" leaves the selection alone once the window is
   * showing. Without this, a Karate breakpoint stops the run behind a tab the user is not looking at
   * and the editor never moves to the step. Then the frames pane within the tab, because the other
   * pane is the test tree, which is where the user was looking a moment ago and tells them nothing
   * about where they now are.</p>
   */
  private void showVariables() {
    // By process handler, not descriptor: XDebugSession's descriptor getter is deprecated and logs a
    // throwable in split mode.
    ApplicationManager.getApplication().invokeLater(() ->
      RunContentManager.getInstance(getSession().getProject()).toFrontRunContent(
        DefaultDebugExecutor.getDebugExecutorInstance(), getProcessHandler()), ModalityState.any());
    getSession().runWhenUiReady(ui -> ApplicationManager.getApplication().invokeLater(() -> {
      Content frames = ui.findContent(DebuggerContentInfo.FRAME_CONTENT);
      if (frames != null) {
        ui.selectAndFocus(frames, true, false);
      }
    }, ModalityState.any()));
  }

  private @Nullable String threadOf(@Nullable XSuspendContext context) {
    return context instanceof KarateSuspendContext karate ? karate.thread() : null;
  }

  /**
   * Turns stopping on failed steps on and off without leaving the debugger.
   *
   * <p>It lives on the toolbar rather than only in Settings because the moment you want it off is the
   * moment it is stopping you every few seconds - halfway through a suite that fails in twenty
   * places. Toggling it also writes the setting, so the next run starts the way you left it.</p>
   */
  private final class PauseOnFailedStepToggle extends ToggleAction {

    private PauseOnFailedStepToggle() {
      super("Pause on Failed Step", "Stop the run on a step that fails, with the scenario's variables "
        + "as the failure left them", AllIcons.Debugger.Db_exception_breakpoint);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return KarateSettingsState.getInstance().isPauseOnFailedStep();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean pause) {
      KarateSettingsState.getInstance().setPauseOnFailedStep(pause);
      if (channel != null) {
        // Takes effect on the next step, not the next run.
        channel.setPauseOnFailure(pause);
      }
    }
  }

  /**
   * Says something the user needs to know about why the run stopped.
   *
   * <p>A balloon only. The console this session adopts is the test tree's, and printing into that
   * attaches the text to the root node's output rather than to any visible pane - a log line nobody
   * would ever see is worse than none, because it reads like one that exists.</p>
   */
  private void report(String message) {
    getSession().reportMessage(message, MessageType.WARNING);
  }

  private @Nullable XSourcePosition sourcePosition(KarateDebugChannel.Paused paused) {
    VirtualFile file = ApplicationManager.getApplication().runReadAction(
      (Computable<VirtualFile>) () -> FeaturePathResolver.findFeatureFile(project, paused.path()));
    if (file == null) {
      report("Could not find " + paused.path() + " in this project, so the paused line cannot be shown.");
      return null;
    }
    // Karate reports 1-based lines; XSourcePosition counts from 0.
    return XDebuggerUtil.getInstance().createPosition(file, paused.line() - 1);
  }

  private void pushBreakpoints() {
    if (channel == null) {
      return;
    }
    List<KarateDebugChannel.Breakpoint> wire;
    synchronized (breakpoints) {
      wire = breakpoints.stream()
        .map(breakpoint -> new KarateDebugChannel.Breakpoint(
          VfsUtilCore.urlToPath(breakpoint.getFileUrl()),
          // XLineBreakpoint counts lines from 0, Karate from 1.
          breakpoint.getLine() + 1,
          breakpoint.getConditionExpression() == null ? null
            : breakpoint.getConditionExpression().getExpression()))
        .toList();
    }
    channel.setBreakpoints(wire);
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
      pushBreakpoints();
    }
  }

  /** One paused Karate thread, with the one frame this phase knows how to build. */
  private static final class KarateSuspendContext extends XSuspendContext {

    private final KarateDebugChannel.Paused paused;
    private final XExecutionStack stack;

    private KarateSuspendContext(KarateDebugChannel.Paused paused, @Nullable XSourcePosition position,
      KarateDebugChannel channel) {
      this.paused = paused;
      this.stack = new KarateExecutionStack(paused, position, channel);
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

  /**
   * Fills a tree node from the agent, one level at a time. Every failure - a resumed thread, a dead
   * channel, a variable that throws on inspection - ends as a message in the node rather than an
   * exception: the run is paused and the user is looking at it.
   */
  private static void requestChildren(KarateDebugChannel channel, String thread, List<String> path,
    XCompositeNode node) {
    channel.variables(thread, path).whenComplete((values, failure) -> {
      if (failure != null) {
        node.setErrorMessage("Could not read variables: " + failure.getMessage());
        return;
      }
      XValueChildrenList children = new XValueChildrenList();
      for (KarateDebugChannel.Value value : values) {
        children.add(value.name(), new KarateValue(channel, thread, path, value));
      }
      // Karate's own order is meaningful (declaration order in the scenario); do not re-sort it.
      node.setAlreadySorted(true);
      node.addChildren(children, true);
    });
  }

  /** One variable, or one node inside one. */
  private static final class KarateValue extends XNamedValue {

    private final KarateDebugChannel channel;
    private final String thread;
    private final List<String> path;
    private final KarateDebugChannel.Value value;

    private KarateValue(KarateDebugChannel channel, String thread, List<String> parentPath,
      KarateDebugChannel.Value value) {
      super(value.name());
      this.channel = channel;
      this.thread = thread;
      this.path = Stream.concat(parentPath.stream(), Stream.of(value.name())).toList();
      this.value = value;
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
      node.setPresentation(null, value.type(), value.value(), value.hasChildren());
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
      requestChildren(channel, thread, path, node);
    }
  }

  /** Evaluate, and the expression fields, run through Karate's own {@code eval}. */
  private static final class KarateEvaluator extends XDebuggerEvaluator {

    private final KarateDebugChannel channel;
    private final String thread;

    private KarateEvaluator(KarateDebugChannel channel, String thread) {
      this.channel = channel;
      this.thread = thread;
    }

    @Override
    public void evaluate(@NotNull String expression, @NotNull XEvaluationCallback callback,
      @Nullable XSourcePosition expressionPosition) {
      channel.evaluate(thread, expression).whenComplete((evaluated, failure) -> {
        if (failure != null) {
          callback.errorOccurred(failure.getMessage() == null ? "Evaluation failed" : failure.getMessage());
          return;
        }
        if (evaluated.error() != null) {
          callback.errorOccurred(evaluated.error());
          return;
        }
        callback.evaluated(new KarateValue(channel, thread, List.of(),
          new KarateDebugChannel.Value("", evaluated.type() == null ? "" : evaluated.type(),
            evaluated.value() == null ? "null" : evaluated.value(), false)));
      });
    }
  }

  private static final class KarateExecutionStack extends XExecutionStack {

    private final XStackFrame topFrame;

    private KarateExecutionStack(KarateDebugChannel.Paused paused, @Nullable XSourcePosition position,
      KarateDebugChannel channel) {
      super(paused.scenario().isEmpty() ? paused.thread() : paused.scenario());
      this.topFrame = new KarateStackFrame(paused, position, channel);
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
    private final KarateDebugChannel channel;

    private KarateStackFrame(KarateDebugChannel.Paused paused, @Nullable XSourcePosition position,
      KarateDebugChannel channel) {
      this.paused = paused;
      this.position = position;
      this.channel = channel;
    }

    /** The scenario's variables, as Karate sees them - what {@code karate.get()} would return. */
    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
      requestChildren(channel, paused.thread(), List.of(), node);
    }

    @Override
    public @NotNull XDebuggerEvaluator getEvaluator() {
      return new KarateEvaluator(channel, paused.thread());
    }

    @Override
    public @Nullable XSourcePosition getSourcePosition() {
      return position;
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
      String step = paused.step().isEmpty() ? "step" : paused.step();
      component.append(step, paused.isFailure()
        ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
      component.append("  " + shortName() + ":" + paused.line(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    private String shortName() {
      String path = paused.path().replace('\\', '/');
      int slash = path.lastIndexOf('/');
      return slash < 0 ? path : path.substring(slash + 1);
    }
  }
}
