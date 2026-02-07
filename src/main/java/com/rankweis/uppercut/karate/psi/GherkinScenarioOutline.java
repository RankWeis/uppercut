// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface GherkinScenarioOutline extends GherkinStepsHolder {
  @NotNull
  List<GherkinExamplesBlock> getExamplesBlocks();

  @Nullable
  Map<String, String> getOutlineTableMap();
}
