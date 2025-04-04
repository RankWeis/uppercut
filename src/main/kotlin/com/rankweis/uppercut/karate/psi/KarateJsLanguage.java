// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.rankweis.uppercut.karate.psi;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;


public class KarateJsLanguage extends Language {

  public static KarateJsLanguage INSTANCE = new KarateJsLanguage();

  protected KarateJsLanguage() {
    super("KarateJs");
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "KarateJs";
  }
}
