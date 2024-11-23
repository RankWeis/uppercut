package com.rankweis.uppercut.karate.format;

import com.google.gson.Gson;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.formatting.Indent;
import com.intellij.formatting.SpacingBuilder;
import com.intellij.json.formatter.JsonFormattingBuilderModel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import com.rankweis.uppercut.karate.psi.formatter.GherkinBlock;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class KarateFormattingModelBuilder implements FormattingModelBuilder {

  private static SpacingBuilder createSpaceBuilder(CodeStyleSettings settings) {
    return new SpacingBuilder(settings, KarateLanguage.INSTANCE)
      .around(KarateTokenTypes.DECLARATION)
      .spaceIf(settings.getCommonSettings(KarateLanguage.INSTANCE.getID()).SPACE_AROUND_ASSIGNMENT_OPERATORS);
  }

  @Override
  public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
    final CodeStyleSettings codeStyleSettings = formattingContext.getCodeStyleSettings();
    Indent indent =
      formattingContext.getFormattingRange().getStartOffset() == 0 ? Indent.getNoneIndent() : Indent.getNormalIndent();
    GherkinBlock rootBlock =
      new GherkinBlock(formattingContext.getNode(), indent, formattingContext.getFormattingRange());
    return new DocumentBasedFormattingModel(rootBlock, formattingContext.getContainingFile().getProject(),
      formattingContext.getCodeStyleSettings(), formattingContext.getContainingFile().getFileType(),
      formattingContext.getContainingFile());
//    return FormattingModelProvider
//      .createFormattingModelForPsiFile(formattingContext.getContainingFile(),
//        rootBlock,
//        codeStyleSettings);
  }

  public boolean isJSONValid(String test) {
    try {
      if (StringUtil.trim(test).startsWith("{")) {
        new JSONObject(test);
      } else {
        new JSONArray(test);
      }
    } catch (JSONException ex) {
      return false;
    }
    return true;
  }

}
