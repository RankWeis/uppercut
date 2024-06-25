// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import com.intellij.json.json5.highlighting.Json5SyntaxHighlightingFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import com.rankweis.uppercut.karate.psi.i18n.JsonGherkinKeywordProvider;


public final class GherkinSyntaxHighlighterFactory extends Json5SyntaxHighlightingFactory {
  @Override
  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(Project project, VirtualFile virtualFile) {
  return new GherkinSyntaxHighlighter(JsonGherkinKeywordProvider.getKeywordProvider(true));
  }
  
  
}
