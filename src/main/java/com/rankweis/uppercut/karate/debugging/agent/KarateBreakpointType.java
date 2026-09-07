package com.rankweis.uppercut.karate.debugging.agent;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.rankweis.uppercut.karate.psi.GherkinFileType;
import com.rankweis.uppercut.karate.run.KarateLibraries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Breakpoints on lines of a {@code .feature} file, for the Karate 2 debugger.
 *
 * <p>Offered only on a Karate 2 <i>module</i> - a project-wide library scan would claim every feature
 * file in a monorepo whose other module is on v2. Karate 1 feature files take <b>Java</b> line breakpoints
 * instead - {@code KarateDebugAware} makes the platform accept them there and
 * {@code KaratePositionManager} binds them to step-definition bytecode - and offering two breakpoint
 * types on one line would put a choice in front of users who have no way to make it. Phase 4 of
 * {@code docs/DEBUGGER.md} moves v1 onto this debugger and deletes the JDI path; this restriction
 * goes with it.</p>
 */
public class KarateBreakpointType extends XLineBreakpointType<XBreakpointProperties<?>> {

  public static final String ID = "karate-feature-line";

  public KarateBreakpointType() {
    super(ID, "Karate feature line");
  }

  @Override
  public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
    return GherkinFileType.INSTANCE.equals(file.getFileType())
      && KarateLibraries.isKarateV2(project, file);
  }

  @Override
  public @Nullable XBreakpointProperties<?> createBreakpointProperties(@NotNull VirtualFile file, int line) {
    // A Karate breakpoint is fully described by its file and line; there is nothing to persist.
    return null;
  }

  @Override
  public String getDisplayText(XLineBreakpoint<XBreakpointProperties<?>> breakpoint) {
    return getDisplayTextDefaultWithPathAndLine(breakpoint);
  }
}
