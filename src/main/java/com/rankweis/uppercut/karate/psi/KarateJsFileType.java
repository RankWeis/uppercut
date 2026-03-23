// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.rankweis.uppercut.karate.psi;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.rankweis.uppercut.karate.UppercutIcon;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;


public final class KarateJsFileType extends LanguageFileType {

  public static final KarateJsFileType INSTANCE = new KarateJsFileType();

  private KarateJsFileType() {
    super(KarateJsLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "KarateJs";
  }

  @Override
  @NotNull
  public String getDescription() {
    return "Helper for injected karate code";
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return "featurejs";
  }

  @Override
  public Icon getIcon() {
    return UppercutIcon.FILE;
  }
}
