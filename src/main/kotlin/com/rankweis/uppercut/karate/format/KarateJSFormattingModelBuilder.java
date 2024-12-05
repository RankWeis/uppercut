package com.rankweis.uppercut.karate.format;

import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.rankweis.uppercut.karate.psi.formatter.GherkinBlock;
import org.jetbrains.annotations.NotNull;

public class KarateJSFormattingModelBuilder implements FormattingModelBuilder {

  @Override
  public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
    final CodeStyleSettings codeStyleSettings = formattingContext.getCodeStyleSettings();
    return FormattingModelProvider
      .createFormattingModelForPsiFile(formattingContext.getContainingFile(),
        new GherkinBlock(formattingContext.getNode()),
        codeStyleSettings);
  }



}
