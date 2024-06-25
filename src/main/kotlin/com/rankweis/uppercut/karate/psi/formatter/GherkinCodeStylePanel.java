package com.rankweis.uppercut.karate.psi.formatter;

import com.rankweis.uppercut.karate.psi.KarateLanguage;
import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.psi.codeStyle.CodeStyleSettings;

public class GherkinCodeStylePanel extends TabbedLanguageCodeStylePanel {
  protected GherkinCodeStylePanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
    super(KarateLanguage.INSTANCE, currentSettings, settings);
  }

  @Override
  protected void initTabs(CodeStyleSettings settings) {
    addIndentOptionsTab(settings);
  }
}
