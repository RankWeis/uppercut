// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.rankweis.uppercut.karate.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public interface UppercutElementTypes {

  IElementType FEATURE = new KarateElementType("feature");
  IElementType FEATURE_HEADER = new KarateElementType("feature header");
  IElementType SCENARIO = new KarateElementType("scenario");
  IElementType STEP = new KarateElementType("step");
  IElementType ACTION = new KarateElementType("action");
  IElementType STEP_PARAMETER = new KarateElementType("step parameter");
  IElementType SCENARIO_OUTLINE = new KarateElementType("scenario outline");
  IElementType RULE = new KarateElementType("rule");
  IElementType EXAMPLES_BLOCK = new KarateElementType("examples block");
  IElementType TABLE = new KarateElementType("table");
  IElementType TABLE_HEADER_ROW = new KarateElementType("table header row");
  IElementType TABLE_ROW = new KarateElementType("table row");
  IElementType TABLE_CELL = new KarateElementType("table cell");
  IElementType TAG = new KarateElementType("tag");
  IElementType PYSTRING = new KarateElementType("pystring");
  IElementType JAVASCRIPT = new KarateElementType("javascript_element");
  IElementType JSON = new KarateElementType("json_element");
  IElementType TEXT_BLOCK = new KarateElementType("json_element");
  IElementType XML = new KarateElementType("xml_element");
  IElementType DECLARATION = new KarateElementType("declaration");
  IElementType VARIABLE = new KarateElementType("variable");
  IElementType PAREN_ELEMENT = new KarateElementType("paren element");

  TokenSet SCENARIOS = TokenSet.create(SCENARIO, SCENARIO_OUTLINE);
}
