// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.rankweis.uppercut.karate.psi;

import com.intellij.psi.tree.IElementType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlainKarateKeywordProvider implements GherkinKeywordProvider {

  public static GherkinKeywordTable DEFAULT_KEYWORD_TABLE = new GherkinKeywordTable();
  public static Map<String, IElementType> DEFAULT_KEYWORDS = new HashMap<>();
  private static final Set<String> ourKeywordsWithNoSpaceAfter = new HashSet<>();

  static {
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.FEATURE_KEYWORD, "Feature");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.BACKGROUND_KEYWORD, "Background");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.SCENARIO_KEYWORD, "Scenario");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.RULE_KEYWORD, "Rule");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.SCENARIO_KEYWORD, "Example");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.SCENARIO_OUTLINE_KEYWORD, "Scenario Outline");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.EXAMPLES_KEYWORD, "Examples");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.EXAMPLES_KEYWORD, "Scenarios");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.STEP_KEYWORD, "Given");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.STEP_KEYWORD, "When");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.STEP_KEYWORD, "Then");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.STEP_KEYWORD, "And");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.STEP_KEYWORD, "But");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.STEP_KEYWORD, "*");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.STEP_KEYWORD, "Lorsqu'");

    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "url");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "call");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "callonce");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "remove");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "callsingle");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "def");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "match");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "path");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "param");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "header");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "method");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "set");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "configure");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "print");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "table");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "if");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "status");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "request");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "assert");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "read");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "headers");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "cookie");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "cookies");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "params");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "response");
    DEFAULT_KEYWORD_TABLE.put(KarateTokenTypes.ACTION_KEYWORD, "eval");

    ourKeywordsWithNoSpaceAfter.add("Lorsqu'");

    DEFAULT_KEYWORD_TABLE.putAllKeywordsInto(DEFAULT_KEYWORDS);
  }

  @Override
  public Collection<String> getAllKeywords(String language) {
    return DEFAULT_KEYWORDS.keySet();
  }

  @Override
  public IElementType getTokenType(String language, String keyword) {
    return DEFAULT_KEYWORDS.get(keyword);
  }

  @Override
  public String getBaseKeyword(String language, String keyword) {
    return keyword;
  }

  @Override
  public boolean isSpaceRequiredAfterKeyword(String language, String keyword) {
    return !ourKeywordsWithNoSpaceAfter.contains(keyword);
  }

  @Override
  public boolean isStepKeyword(String keyword) {
    return DEFAULT_KEYWORDS.get(keyword) == KarateTokenTypes.STEP_KEYWORD;
  }

  @Override public boolean isActionKeyword(String keyword) {
    return DEFAULT_KEYWORDS.get(keyword) == KarateTokenTypes.ACTION_KEYWORD;
  }

  @Override
  @NotNull
  public GherkinKeywordTable getKeywordsTable(@Nullable final String language) {
    return DEFAULT_KEYWORD_TABLE;
  }

  public Collection<String> getActionKeywords() {
    return DEFAULT_KEYWORD_TABLE.getActionKeywords();
  }
}
