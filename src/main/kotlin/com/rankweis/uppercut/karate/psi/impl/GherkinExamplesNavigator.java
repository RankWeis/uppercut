// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public final class GherkinExamplesNavigator {
  @Nullable
  public static GherkinExamplesBlockImpl getExamplesByTable(final GherkinTableImpl table) {
    final PsiElement element = table.getParent();
    return element instanceof GherkinExamplesBlockImpl ? (GherkinExamplesBlockImpl)element : null;
  }
}
