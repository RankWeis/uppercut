package com.rankweis.uppercut.karate.run;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UppercutConsoleFilter implements Filter {

  private final Project project;

  public UppercutConsoleFilter(Project project) {
    this.project = project;
  }

  private static final Pattern CLASSPATH_PATTERN = Pattern.compile("classpath:(\\S+)");

  @Override public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
    Matcher matcher = CLASSPATH_PATTERN.matcher(line);
    int start = entireLength - line.length();
    if (matcher.find()) {
      VirtualFile virtualFile = getVirtualFile(matcher.group(1));
      if (virtualFile != null) {
        HyperlinkInfo info = new OpenFileHyperlinkInfo(project, virtualFile, 0);
        return new Result(start + line.indexOf("classpath:"),
          start + line.indexOf("classpath:") + matcher.group().length(), info);
      }
    }
    return null;
  }

  private VirtualFile getVirtualFile(String path) {

    List<String> filePaths = Arrays.stream(path.split("@")).toList();
    Module[] modules = ModuleManager.getInstance(project).getModules();
    List<VirtualFile> virtualFiles = Arrays.stream(modules)
      .map(module -> {
        @NotNull VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
        List<VirtualFile> vfs = Arrays.stream(sourceRoots)
          .map(r -> r.findFileByRelativePath(filePaths.getFirst()))
          .filter(Objects::nonNull)
          .toList();
        if (vfs.isEmpty()) {
          return null;
        }
        if (filePaths.size() == 1) {
          return vfs.getFirst();
        } else {
          return vfs.getFirst();
        }
      }).filter(Objects::nonNull).toList();
    return virtualFiles.isEmpty() ? null : virtualFiles.getFirst();
  }
}
