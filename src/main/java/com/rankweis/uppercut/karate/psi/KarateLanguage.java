// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.rankweis.uppercut.karate.psi;

import com.intellij.json.JsonLanguage;
import org.jetbrains.annotations.NotNull;


public class KarateLanguage extends JsonLanguage {

  public static KarateLanguage INSTANCE = new KarateLanguage();

  protected KarateLanguage() {
    super("Karate");
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Karate";
  }

  @Override public boolean hasPermissiveStrings() {
    return true;
  }
}
