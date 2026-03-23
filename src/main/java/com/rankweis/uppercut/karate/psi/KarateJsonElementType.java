// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.rankweis.uppercut.karate.psi;

import com.intellij.json.json5.Json5Language;
import com.intellij.json.psi.impl.JsonObjectImpl;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;


public class KarateJsonElementType extends JsonObjectImpl {

  public KarateJsonElementType(@NotNull ASTNode node) {
    super(node);
  }

  @Override public @NotNull Language getLanguage() {
    return Json5Language.INSTANCE;
  }
}
