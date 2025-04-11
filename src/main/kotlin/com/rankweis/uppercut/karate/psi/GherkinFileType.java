// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import com.rankweis.uppercut.karate.UppercutIcon;
import com.rankweis.uppercut.karate.MyBundle;
import com.intellij.openapi.fileTypes.LanguageFileType;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;


public final class GherkinFileType extends LanguageFileType {
  public static final GherkinFileType INSTANCE = new GherkinFileType();

  private GherkinFileType() {
    super(KarateLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "Karate";
  }

  @Override
  @NotNull
  public String getDescription() {
    return MyBundle.message("filetype.cucumber.scenario.description");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return "feature";
  }

  @Override
  public Icon getIcon() {
    return UppercutIcon.FILE;
  }
}
