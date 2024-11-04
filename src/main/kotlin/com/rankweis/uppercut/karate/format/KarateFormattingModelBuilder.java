package com.rankweis.uppercut.karate.format;

import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.formatting.SpacingBuilder;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import com.rankweis.uppercut.karate.psi.formatter.GherkinBlock;
import org.jetbrains.annotations.NotNull;

public class KarateFormattingModelBuilder implements FormattingModelBuilder {

  private static SpacingBuilder createSpaceBuilder(CodeStyleSettings settings) {
    return new SpacingBuilder(settings, KarateLanguage.INSTANCE)
      .around(KarateTokenTypes.DECLARATION)
      .spaceIf(settings.getCommonSettings(KarateLanguage.INSTANCE.getID()).SPACE_AROUND_ASSIGNMENT_OPERATORS);
  }

  @Override
  public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
    final CodeStyleSettings codeStyleSettings = formattingContext.getCodeStyleSettings();
    return FormattingModelProvider
      .createFormattingModelForPsiFile(formattingContext.getContainingFile(),
        new GherkinBlock(formattingContext.getNode()),
        codeStyleSettings);
  }

}
