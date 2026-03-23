package com.rankweis.uppercut.karate.extension;

import com.intellij.json.json5.Json5PsiWalkerFactory;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalkerFactory;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.rankweis.uppercut.karate.psi.KarateLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KarateJson5PsiWalkerFactory implements JsonLikePsiWalkerFactory {

  public static final JsonLikePsiWalker JS_WALKER_INSTANCE = Json5PsiWalkerFactory.WALKER_INSTANCE;

  public boolean handles(@NotNull PsiElement element) {
    return element.getContainingFile().getLanguage().equals(KarateLanguage.INSTANCE) || element.getLanguage()
      .equals(KarateLanguage.INSTANCE);
  }

  public @NotNull JsonLikePsiWalker create(@Nullable JsonSchemaObject schemaObject) {
    return JS_WALKER_INSTANCE;
  }

}
