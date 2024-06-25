package com.rankweis.uppercut.karate;

import com.rankweis.uppercut.karate.psi.GherkinSyntaxHighlighter;
import com.rankweis.uppercut.karate.psi.PlainKarateKeywordProvider;
import com.rankweis.uppercut.karate.psi.impl.GherkinFileImpl;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public final class GherkinLiveTemplateContextType extends TemplateContextType {
  public GherkinLiveTemplateContextType() {
    super(MyBundle.message("live.templates.context.cucumber.name"));
  }

  @Override
  public boolean isInContext(@NotNull final PsiFile file, final int offset) {
    return file instanceof GherkinFileImpl;
  }

  @Override
  public SyntaxHighlighter createHighlighter() {
    return new GherkinSyntaxHighlighter(new PlainKarateKeywordProvider());
  }
}
