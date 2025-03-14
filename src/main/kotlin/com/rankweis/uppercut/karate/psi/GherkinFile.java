// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import com.intellij.psi.PsiFile;
import java.util.List;


public interface GherkinFile extends PsiFile {
  List<String> getStepKeywords();

  List<String> getActionKeywords();

  String getLocaleLanguage();

  GherkinFeature[] getFeatures();
}
