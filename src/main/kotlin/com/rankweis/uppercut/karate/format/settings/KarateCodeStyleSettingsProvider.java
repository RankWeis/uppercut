package com.rankweis.uppercut.karate.format.settings;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.psi.codeStyle.CodeStyleConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.rankweis.uppercut.karate.MyBundle;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import org.jetbrains.annotations.NotNull;

public final class KarateCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  @Override
  public CustomCodeStyleSettings createCustomSettings(@NotNull CodeStyleSettings settings) {
    return new KarateCodeStyleSettings(settings);
  }
  
  @NotNull
  @Override
  public CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, MyBundle.message("configurable.name.gherkin")) {
      @Override
      protected @NotNull CodeStyleAbstractPanel createPanel(@NotNull CodeStyleSettings settings) {
        return new KarateCodeStylePanel(getCurrentSettings(), settings);
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

  private static class KarateCodeStylePanel extends TabbedLanguageCodeStylePanel {

    public KarateCodeStylePanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
      super(KarateLanguage.INSTANCE, currentSettings, settings);
    }

  }

}
