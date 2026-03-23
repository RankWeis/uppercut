package com.rankweis.uppercut.karate.psi;

import com.intellij.psi.tree.IElementType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public class GherkinKeywordTable {
  private final Map<IElementType, Collection<String>> myType2KeywordsTable = new HashMap<>();

  public GherkinKeywordTable() {
    for (IElementType type : KarateTokenTypes.KEYWORDS.getTypes()) {
      myType2KeywordsTable.put(type, new ArrayList<>());
    }
  }

  public void putAllKeywordsInto(Map<String, IElementType> target) {
    for (IElementType type : this.getTypes()) {
      final Collection<String> keywords = this.getKeywords(type);
      if (keywords != null) {
        for (String keyword : keywords) {
          target.put(keyword, type);
        }
      }
    }
  }

  public void put(IElementType type, String keyword) {
    if (KarateTokenTypes.KEYWORDS.contains(type)) {
      Collection<String> keywords = getKeywords(type);
      if (keywords == null) {
        keywords = new ArrayList<>(1);
        myType2KeywordsTable.put(type, keywords);
      }
      keywords.add(keyword);
    }
  }

  public Collection<String> getStepKeywords() {
    final Collection<String> keywords = getKeywords(KarateTokenTypes.STEP_KEYWORD);
    assert keywords != null;
    return keywords;
  }

  public Collection<String> getActionKeywords() {
    final Collection<String> keywords = getKeywords(KarateTokenTypes.ACTION_KEYWORD);
    assert keywords != null;
    return keywords;
  }

  public Collection<String> getScenarioKeywords() {
    return getKeywords(KarateTokenTypes.SCENARIO_KEYWORD);
  }

  public Collection<String> getScenarioLikeKeywords() {

    final Collection<String> scenarios = getKeywords(KarateTokenTypes.SCENARIO_KEYWORD);
    assert scenarios != null;
    final Set<String> keywords = new HashSet<>(scenarios);

    final Collection<String> scenarioOutline = getKeywords(KarateTokenTypes.SCENARIO_OUTLINE_KEYWORD);
    assert scenarioOutline != null;
    keywords.addAll(scenarioOutline);

    return keywords;
  }
  
  @NotNull
  public Collection<String> getRuleKeywords() {
    Collection<String> result = getKeywords(KarateTokenTypes.RULE_KEYWORD);
    return result == null ? Collections.emptyList() : result;
  }

  public String getScenarioOutlineKeyword() {
    return getScenarioOutlineKeywords().iterator().next();
  }

  public Collection<String> getScenarioOutlineKeywords() {

    final Collection<String> scenarioOutline = getKeywords(KarateTokenTypes.SCENARIO_OUTLINE_KEYWORD);
    assert scenarioOutline != null;

    return scenarioOutline;
  }

  public Collection<String> getBackgroundKeywords() {
    final Collection<String> bg = getKeywords(KarateTokenTypes.BACKGROUND_KEYWORD);
    assert bg != null;

    return bg;
  }

  public String getExampleSectionKeyword() {
    return getExampleSectionKeywords().iterator().next();
  }

  public Collection<String> getExampleSectionKeywords() {
    final Collection<String> keywords = getKeywords(KarateTokenTypes.EXAMPLES_KEYWORD);
    assert keywords != null;
    return keywords;
  }

  public String getFeatureSectionKeyword() {
    return getFeaturesSectionKeywords().iterator().next();
  }

  public Collection<String> getFeaturesSectionKeywords() {
    final Collection<String> keywords = getKeywords(KarateTokenTypes.FEATURE_KEYWORD);
    assert keywords != null;
    return keywords;
  }

  public Collection<IElementType> getTypes() {
    return myType2KeywordsTable.keySet();
  }

  @Nullable
  public Collection<String> getKeywords(final IElementType type) {
    return myType2KeywordsTable.get(type);
  }

  public boolean tableContainsKeyword(KarateElementType type, String keyword) {
    Collection<String> alreadyKnownKeywords = getKeywords(type);
    return null != alreadyKnownKeywords && alreadyKnownKeywords.contains(keyword);
  }
}