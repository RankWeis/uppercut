// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be 
// found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import com.intellij.psi.tree.TokenSet;

public interface KarateTokenTypes {
  KarateElementType COMMENT = new KarateElementType("COMMENT");
  KarateElementType TEXT = new KarateElementType("TEXT");
  KarateElementType QUOTE = new KarateElementType("QUOTE");
  KarateElementType EXAMPLES_KEYWORD = new KarateElementType("EXAMPLES_KEYWORD");
  KarateElementType FEATURE_KEYWORD = new KarateElementType("FEATURE_KEYWORD");
  KarateElementType RULE_KEYWORD = new KarateElementType("RULE_KEYWORD");
  KarateElementType BACKGROUND_KEYWORD = new KarateElementType("BACKGROUND_KEYWORD");
  KarateElementType SCENARIO_KEYWORD = new KarateElementType("SCENARIO_KEYWORD");
  KarateElementType EXAMPLE_KEYWORD = new KarateElementType("EXAMPLE_KEYWORD");
  KarateElementType SCENARIO_OUTLINE_KEYWORD = new KarateElementType("SCENARIO_OUTLINE_KEYWORD");
  KarateElementType STEP_KEYWORD = new KarateElementType("STEP_KEYWORD");
  KarateElementType ACTION_KEYWORD = new KarateElementType("ACTION_KEYWORD");
  KarateElementType STEP_PARAMETER_BRACE = new KarateElementType("STEP_PARAMETER_BRACE");
  KarateElementType STEP_PARAMETER_TEXT = new KarateElementType("STEP_PARAMETER_TEXT");
  KarateElementType COLON = new KarateElementType("COLON");
  KarateElementType TAG = new KarateElementType("TAG");
  KarateElementType DECLARATION = new KarateElementType("DECLARATION");
  KarateElementType VARIABLE = new KarateElementType("VARIABLE");
  KarateElementType PYSTRING_QUOTES = new KarateElementType("PYSTRING_QUOTES");
  KarateElementType PYSTRING = new KarateElementType("PYSTRING_ELEMENT");
  KarateElementType PYSTRING_INCOMPLETE = new KarateElementType("PYSTRING_INCOMPLETE");
  
  KarateElementType PIPE = new KarateElementType("PIPE");
  KarateElementType TABLE_CELL = new KarateElementType("TABLE_CELL");
  
  TokenSet IDENTIFIERS = TokenSet.create(VARIABLE, DECLARATION);

  TokenSet KEYWORDS = TokenSet.create(FEATURE_KEYWORD, RULE_KEYWORD, EXAMPLE_KEYWORD,
    BACKGROUND_KEYWORD, SCENARIO_KEYWORD, SCENARIO_OUTLINE_KEYWORD,
    EXAMPLES_KEYWORD, EXAMPLES_KEYWORD, ACTION_KEYWORD,
    STEP_KEYWORD);

  TokenSet SCENARIOS_KEYWORDS = TokenSet.create(SCENARIO_KEYWORD, SCENARIO_OUTLINE_KEYWORD, EXAMPLE_KEYWORD);

  TokenSet COMMENTS = TokenSet.create(KarateTokenTypes.COMMENT);
}
