// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.rankweis.uppercut.karate.psi.i18n;

import static com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement;

import com.rankweis.uppercut.karate.psi.GherkinKeywordList;
import com.rankweis.uppercut.karate.psi.GherkinKeywordProvider;
import com.rankweis.uppercut.karate.psi.GherkinKeywordTable;
import com.rankweis.uppercut.karate.psi.PlainKarateKeywordProvider;
import com.rankweis.uppercut.karate.steps.CucumberStepHelper;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.MalformedJsonException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class JsonGherkinKeywordProvider implements GherkinKeywordProvider {

  private static final class Lazy {
    // leads to init of gher
    static final GherkinKeywordList myEmptyKeywordList = new GherkinKeywordList();
  }

  private final Map<String, GherkinKeywordList> myLanguageKeywords = new HashMap<>();
  private final Set<String> myAllStepKeywords = new HashSet<>();
  private final Set<String> myAllActionKeywords = new HashSet<>();

  private static GherkinKeywordProvider myKeywordProvider;
  private static GherkinKeywordProvider myGherkin6KeywordProvider;

  public static GherkinKeywordProvider getKeywordProvider() {
    if (myKeywordProvider == null) {
      myKeywordProvider = createKeywordProviderFromJson("i18n_old.json");
    }
    return myKeywordProvider;
  }

  public static GherkinKeywordProvider getKeywordProvider(boolean gherkin6) {
    if (!gherkin6) {
      return getKeywordProvider();
    }
    if (myGherkin6KeywordProvider == null) {
      myGherkin6KeywordProvider = createKeywordProviderFromJson("i18n.json");
    }
    return myGherkin6KeywordProvider;
  }

  public static GherkinKeywordProvider getKeywordProvider(@NotNull PsiElement context) {
    Module module = findModuleForPsiElement(context);
    boolean gherkin6Enabled = module != null && CucumberStepHelper.isGherkin6Supported(module);
    return getKeywordProvider(gherkin6Enabled);
  }

  private static GherkinKeywordProvider createKeywordProviderFromJson(@NotNull String jsonFileName) {
    GherkinKeywordProvider result = null;
    ClassLoader classLoader = JsonGherkinKeywordProvider.class.getClassLoader();
    if (classLoader != null) {
      InputStream gherkinKeywordStream = Objects.requireNonNull(classLoader.getResourceAsStream(jsonFileName));
      result = new JsonGherkinKeywordProvider(gherkinKeywordStream);
    }

    return result != null ? result : new PlainKarateKeywordProvider();
  }

  public JsonGherkinKeywordProvider(@NotNull InputStream inputStream) {
    Map<String, Map<String, Object>> fromJson;
    try (Reader in = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
      fromJson = new Gson().fromJson(in, new TypeToken<Map<String, HashMap<String, Object>>>() {}.getType());

      for (Map.Entry<String, Map<String, Object>> entry : fromJson.entrySet()) {
        Map<String, Object> translation = entry.getValue();
        final GherkinKeywordList keywordList = new GherkinKeywordList(translation);
        myLanguageKeywords.put(entry.getKey(), keywordList);
        for (String keyword : keywordList.getAllKeywords()) {
          if (keywordList.getTokenType(keyword) == KarateTokenTypes.STEP_KEYWORD) {
            myAllStepKeywords.add(keyword);
          }
          if (keywordList.getTokenType(keyword) == KarateTokenTypes.ACTION_KEYWORD) {
            myAllActionKeywords.add(keyword);
          }
        }
      }
    }
    catch (MalformedJsonException e) {
      // ignore
    }
    catch (IOException e) {
      Logger.getInstance(JsonGherkinKeywordProvider.class.getName()).error(e);
    }
  }

  @Override
  public Collection<String> getAllKeywords(String language) {
    return getKeywordList(language).getAllKeywords();
  }

  @Override
  public IElementType getTokenType(String language, String keyword) {
    return getKeywordList(language).getTokenType(keyword);
  }

  @Override
  public String getBaseKeyword(String language, String keyword) {
    return getKeywordList(language).getBaseKeyword(keyword);
  }

  @Override
  public boolean isSpaceRequiredAfterKeyword(String language, String keyword) {
    return getKeywordList(language).isSpaceAfterKeyword(keyword);
  }

  @Override
  public boolean isStepKeyword(String keyword) {
    return myAllStepKeywords.contains(keyword);
  }

  @Override public boolean isActionKeyword(String keyword) {
    return myAllActionKeywords.contains(keyword);
  }

  @NotNull
  @Override
  public GherkinKeywordTable getKeywordsTable(@Nullable String language) {
    return getKeywordList(language).getKeywordsTable();
  }

  @NotNull
  private GherkinKeywordList getKeywordList(@Nullable final String language) {
    GherkinKeywordList keywordList = myLanguageKeywords.get(language);
    if (keywordList == null) {
      keywordList = Lazy.myEmptyKeywordList;
    }
    return keywordList;
  }
}
