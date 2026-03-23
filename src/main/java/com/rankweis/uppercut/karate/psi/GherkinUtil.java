// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import com.rankweis.uppercut.karate.psi.impl.GherkinFileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GherkinUtil {
  @NotNull
  public static String getFeatureLanguage(@Nullable GherkinFile gherkinFile) {
    return gherkinFile != null ? gherkinFile.getLocaleLanguage() : GherkinFileImpl.getDefaultLocale();
  }
}
