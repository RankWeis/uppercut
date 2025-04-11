// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be 
// found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import static com.rankweis.uppercut.karate.psi.i18n.JsonGherkinKeywordProvider.getKeywordProvider;

import com.intellij.json.highlighting.JsonSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;


public final class GherkinSyntaxHighlighterFactory extends JsonSyntaxHighlighterFactory {

  @Override
  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(Project project, VirtualFile virtualFile) {
    return new UppercutSyntaxHighlighter(project, virtualFile, getKeywordProvider(true));
  }

  @Override protected boolean isCanEscapeEol() {
    return true;
  }
}
