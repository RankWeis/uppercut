package com.rankweis.uppercut.karate.format.settings;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public final class KarateCodeStyleSettings extends CustomCodeStyleSettings {

  protected KarateCodeStyleSettings(@NotNull CodeStyleSettings settings) {
    super("KarateCodeStyleSettings", settings);
  }

}
