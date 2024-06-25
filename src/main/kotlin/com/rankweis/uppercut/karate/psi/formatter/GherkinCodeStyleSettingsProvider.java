package com.rankweis.uppercut.karate.psi.formatter;

import com.rankweis.uppercut.karate.MyBundle;
import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;

public final class GherkinCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  @NotNull
  @Override
  public Configurable createSettingsPage(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, MyBundle.message("configurable.name.gherkin")) {
      @Override
      protected @NotNull CodeStyleAbstractPanel createPanel(@NotNull CodeStyleSettings settings) {
        return new GherkinCodeStylePanel(getCurrentSettings(), settings);
      }

      @Override
      public String getHelpTopic() {
        return "reference.settingsdialog.codestyle.gherkin";
      }
    };
  }

  @Override
  public String getConfigurableDisplayName() {
    return MyBundle.message("configurable.name.gherkin");
  }
}
