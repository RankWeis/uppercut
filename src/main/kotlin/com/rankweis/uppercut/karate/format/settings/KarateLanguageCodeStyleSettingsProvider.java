package com.rankweis.uppercut.karate.format.settings;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import org.jetbrains.annotations.NotNull;

public final class KarateLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  private static final String DEMO_TEXT =
    """
      # language: en
      Feature: Karate Colors Settings Page
        In order to customize Karate language (*.feature files) highlighting
        Our users can use this settings preview pane
 
        @wip
        Scenario Outline: Different Gherkin language structures
          Given Some feature file with content
          ""\"
          Feature: Some feature
            Scenario: Some scenario
          ""\"
          And I want to add new cucumber step
          And Also a step with "<regexp_param>regexp</regexp_param>" parameter
          When I open <<outline_param>ruby_ide</outline_param>>
          Then Steps autocompletion feature will help me with all these tasks
          And inlineFunc = function() { return 'Hello, World!'; }
          And embeddedFunc =
          \"""
          function() {
            let x="hi";
            return 'Hello, World!';
          }
          \"""
 
        Examples:
          | <th>ruby_ide</th> |
          | RubyMine |""";

  @NotNull
  @Override
  public Language getLanguage() {
    return KarateLanguage.INSTANCE;
  }

//  @Override
//  protected void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings,
//    @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
//    indentOptions.INDENT_SIZE = 2;
//  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
    super.customizeSettings(consumer, settingsType);
//    if (settingsType == SettingsType.SPACING_SETTINGS) {
//      consumer.showStandardOptions("SPACE_AROUND_ASSIGNMENT_OPERATORS");
//    } else if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
//      consumer.showStandardOptions("KEEP_BLANK_LINES_IN_CODE");
//    }
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    return DEMO_TEXT;
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new SmartIndentOptionsEditor();
  }


}
