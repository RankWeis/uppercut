package com.rankweis.uppercut.karate.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nls.Capitalization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateGoToSymbolProvider implements GotoDeclarationHandler {

  private static final Pattern FILE_PATTERN = Pattern.compile("[^ :]+\\.[^ :]+");

  @Override public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset,
    Editor editor) {
    if (sourceElement == null) {
      return new PsiElement[0];
    }
    String[] quotedSplit = sourceElement.getText().split("[\"']");
    List<String> filePaths = Arrays.stream(quotedSplit)
      .filter(s -> s.startsWith("classpath:"))
      .flatMap(s -> Arrays.stream(s.split("classpath:")))
      .flatMap(s -> Arrays.stream(s.split("@")))
      .filter(StringUtils::isNotEmpty)
      .toList();
    if (!filePaths.isEmpty()) {
      return goToClasspath(sourceElement, filePaths);
    } else {
      VirtualFile potentialFilePath = Arrays.stream(quotedSplit)
        .map(FILE_PATTERN::matcher)
        .filter(Matcher::matches)
        .map(m -> m.group(0))
        .findFirst()
        .flatMap(s -> Optional.ofNullable(sourceElement.getContainingFile().getVirtualFile().getParent())
          .map(f -> f.findFileByRelativePath(s)))
        .orElse(null);
      if (potentialFilePath != null) {
        return new PsiElement[]{PsiManager.getInstance(sourceElement.getProject()).findFile(potentialFilePath)};
      }
    }
    return new PsiElement[0];
  }

  @Override public @Nullable @Nls(capitalization = Capitalization.Title) String getActionText(
    @NotNull DataContext context) {
    return GotoDeclarationHandler.super.getActionText(context);
  }

  private PsiElement[] goToClasspath(@Nullable PsiElement sourceElement, List<String> filePaths) {
    PsiFile containingFile = Objects.requireNonNull(sourceElement).getContainingFile();
    if (containingFile == null) {
      return new PsiElement[0];
    }
    Module module = ModuleUtilCore.findModuleForFile(containingFile);
    if (module == null) {
      return new PsiElement[0];
    }
    @NotNull VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
    PsiManager instance = PsiManager.getInstance(sourceElement.getProject());
    List<PsiFile> list = Arrays.stream(sourceRoots)
      .map(r -> r.findFileByRelativePath(filePaths.get(0)))
      .filter(Objects::nonNull)
      .map(instance::findFile)
      .filter(Objects::nonNull)
      .toList();
    if (filePaths.size() == 1) {
      return list.toArray(PsiElement[]::new);
    } else if (filePaths.size() == 2) {
      return list.stream()
        .map(f -> {
          int textOffsetNewline = f.getText().indexOf("@" + filePaths.get(1) + "\n");
          int textOffset = f.getText().indexOf("@" + filePaths.get(1));
          int textOffsetRet = textOffsetNewline == -1 ? textOffset : textOffsetNewline;
          return f.findElementAt(textOffsetRet);
        })
        .toArray(PsiElement[]::new);
    }
    return new PsiElement[0];
  }
}
