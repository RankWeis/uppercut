package com.rankweis.uppercut.karate.run;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Maps a feature path as Karate reports it back to the file the user is editing.
 *
 * <p>Karate's paths are relative to the working directory and usually point at the compiled test
 * classpath ({@code build/resources/test/sample/x.feature} for Gradle,
 * {@code target/test-classes/...} for Maven); a run launched against a file reports the
 * source-relative path instead. Strip leading segments until a suffix resolves against a source root;
 * only when the whole stripping pass finds nothing, fall back to the project directory.
 *
 * <p>The two passes must not be interleaved: the runtime path resolves against the project dir as-is
 * (the compiled copy under {@code build/} really exists), so a combined root list would short-circuit
 * on the generated file before stripping ever produced the source-relative suffix. Navigation would
 * then open the build copy, where edits and breakpoints are silently discarded on the next build.
 *
 * <p>Both consumers depend on that ordering: the test tree's {@code locationHint} navigation and the
 * debugger, which has to highlight the paused line in the file the breakpoint was set in.
 */
public final class FeaturePathResolver {

  private static final Logger LOG = Logger.getInstance(FeaturePathResolver.class);

  private FeaturePathResolver() {
  }

  public static @Nullable VirtualFile findFeatureFile(@NotNull Project project, @NotNull String featurePath) {
    List<VirtualFile> sourceRoots =
      Arrays.stream(ModuleManager.getInstance(project).getModules())
        .flatMap(m -> Arrays.stream(ModuleRootManager.getInstance(m).getSourceRoots()))
        .toList();
    VirtualFile inSourceRoot = resolveByStripping(featurePath, sourceRoots);
    if (inSourceRoot != null) {
      return inSourceRoot;
    }
    LOG.info("Karate feature path did not resolve against any source root, falling back to project dir. "
      + "path=" + featurePath + " sourceRoots=" + sourceRoots);
    VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
    return projectDir == null ? null : resolveByStripping(featurePath, List.of(projectDir));
  }

  private static @Nullable VirtualFile resolveByStripping(String featurePath, List<VirtualFile> roots) {
    // findFileByRelativePath, not VfsUtil.findRelativeFile: the latter treats an absolute path
    // (e.g. C:/... - what Karate 2 emits when the working dir is not the module root) as absolute and
    // ignores the base root entirely, resolving the build-output copy on the first iteration no
    // matter which roots are tried first.
    String candidate = featurePath.replace('\\', '/');
    while (!candidate.isEmpty()) {
      String finalCandidate = candidate;
      VirtualFile found = roots.stream().filter(Objects::nonNull)
        .map(root -> root.findFileByRelativePath(finalCandidate))
        .filter(Objects::nonNull)
        .findFirst().orElse(null);
      if (found != null) {
        return found;
      }
      int slash = candidate.indexOf('/');
      if (slash < 0) {
        return null;
      }
      candidate = candidate.substring(slash + 1);
    }
    return null;
  }
}
