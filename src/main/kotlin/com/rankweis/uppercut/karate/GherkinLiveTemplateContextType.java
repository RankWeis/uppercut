package com.rankweis.uppercut.karate;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.rankweis.uppercut.karate.psi.UppercutSyntaxHighlighter;
import com.rankweis.uppercut.karate.psi.PlainKarateKeywordProvider;
import com.rankweis.uppercut.karate.psi.impl.GherkinFileImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public final class GherkinLiveTemplateContextType extends TemplateContextType {
  public GherkinLiveTemplateContextType() {
    super(MyBundle.message("live.templates.context.cucumber.name"));
  }

  @Override public boolean isInContext(@NotNull TemplateActionContext templateActionContext) {
    return templateActionContext.getFile() instanceof GherkinFileImpl;
  }

  @Override
  public SyntaxHighlighter createHighlighter() {
    return new UppercutSyntaxHighlighter(new PlainKarateKeywordProvider());
  }
}
