// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.rankweis.uppercut.karate.psi;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public class KarateElementType extends IElementType {

  public KarateElementType(@NotNull @NonNls String debugName) {
    super(debugName, KarateLanguage.INSTANCE);
  }
}
