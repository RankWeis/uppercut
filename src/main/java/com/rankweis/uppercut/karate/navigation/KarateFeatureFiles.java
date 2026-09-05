package com.rankweis.uppercut.karate.navigation;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.rankweis.uppercut.karate.psi.GherkinStep;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the feature paths Karate accepts - {@code classpath:dir/other.feature} against the
 * module's source roots, anything else relative to the referring file - to the files behind them.
 * Shared by go-to-declaration on a {@code read('...')} argument and by variable references that
 * follow a {@code def x = call read('...')} into the called feature.
 */
public final class KarateFeatureFiles {

  private static final String CLASSPATH = "classpath:";

  /** {@code call read('path')} on a step, with or without the trailing argument object. */
  private static final Pattern CALL_READ = Pattern.compile("\\bcall\\s+read\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");

  private KarateFeatureFiles() {
  }

  /**
   * The feature path a step calls through {@code call read('...')}, or null when the step is not
   * such a call. A trailing {@code @tag} or scenario selector is dropped: it narrows what runs, not
   * which file's variables come back.
   */
  public static @Nullable String calledFeaturePath(@NotNull GherkinStep step) {
    Matcher matcher = CALL_READ.matcher(step.getText());
    if (!matcher.find()) {
      return null;
    }
    String path = matcher.group(1);
    int selector = path.indexOf('@');
    return selector < 0 ? path : path.substring(0, selector);
  }

  /** The feature file {@code path} names when read from {@code from}; null when nothing matches. */
  public static @Nullable PsiFile resolve(@NotNull PsiFile from, @NotNull String path) {
    if (path.startsWith(CLASSPATH)) {
      List<PsiFile> matches = findInSourceRoots(from, path.substring(CLASSPATH.length()));
      return matches.isEmpty() ? null : matches.get(0);
    }
    VirtualFile file = from.getVirtualFile();
    VirtualFile directory = file == null ? null : file.getParent();
    return Optional.ofNullable(directory)
      .map(dir -> dir.findFileByRelativePath(path))
      .map(target -> PsiManager.getInstance(from.getProject()).findFile(target))
      .orElse(null);
  }

  /** Every file at {@code relativePath} under one of the source roots of {@code from}'s module. */
  public static @NotNull List<PsiFile> findInSourceRoots(@NotNull PsiFile from, @NotNull String relativePath) {
    Module module = ModuleUtilCore.findModuleForFile(from);
    if (module == null) {
      return List.of();
    }
    PsiManager psiManager = PsiManager.getInstance(from.getProject());
    return Arrays.stream(ModuleRootManager.getInstance(module).getSourceRoots())
      .map(root -> root.findFileByRelativePath(relativePath))
      .filter(Objects::nonNull)
      .map(psiManager::findFile)
      .filter(Objects::nonNull)
      .toList();
  }
}
