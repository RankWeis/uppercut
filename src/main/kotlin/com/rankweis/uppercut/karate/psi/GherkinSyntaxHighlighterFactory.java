// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be 
// found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import com.intellij.json.highlighting.JsonSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.rankweis.uppercut.karate.psi.i18n.JsonGherkinKeywordProvider;
import org.jetbrains.annotations.NotNull;


public final class GherkinSyntaxHighlighterFactory extends JsonSyntaxHighlighterFactory {

  @Override
  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(Project project, VirtualFile virtualFile) {
    return new GherkinSyntaxHighlighter(JsonGherkinKeywordProvider.getKeywordProvider(true));
  }


}
