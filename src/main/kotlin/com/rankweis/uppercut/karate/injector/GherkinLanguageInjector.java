// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be 
// found in the LICENSE file.
package com.rankweis.uppercut.karate.injector;

import static com.rankweis.uppercut.karate.psi.GherkinLexer.PYSTRING_MARKER;

import com.intellij.json.json5.Json5Language;
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.rankweis.uppercut.karate.psi.GherkinPystring;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import kotlinx.serialization.SerializationException;
import kotlinx.serialization.json.Json;
import org.jetbrains.annotations.NotNull;

public final class GherkinLanguageInjector implements MultiHostInjector {

  private static final Pattern JSON_LOOSE_FORMAT = Pattern.compile("\\{\\s*\"\\w+\" *:.*}\\s*", Pattern.DOTALL);

  @NotNull
  @Override
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(GherkinPystring.class);
  }

  @Override
  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull final PsiElement context) {
    if (!(context instanceof GherkinPystring host)) {
      return;
    }

    final String hostText = host.getText();
    if (PYSTRING_MARKER.equals(hostText)) {
      return;
    }
    int newLineCharacterOffset = 0;
    while (newLineCharacterOffset < hostText.length() && hostText.charAt(newLineCharacterOffset) != '\n') {
      newLineCharacterOffset++;
    }
    String strippedText;
    int skippedOffset = PYSTRING_MARKER.length();
    if (!hostText.startsWith(PYSTRING_MARKER)) {
      strippedText = StringUtil.trimLeading(StringUtil.trimTrailing(hostText));
      skippedOffset = 0;
    } else {
      strippedText = StringUtil.trimLeading(StringUtil.trimTrailing(hostText)
        .substring(PYSTRING_MARKER.length(), hostText.length() - PYSTRING_MARKER.length()));
    }
    Language language;
    if (JSON_LOOSE_FORMAT.matcher(strippedText).matches()) {
      // Favor towards json so there isn't a sudden switch to javascript when invalid json is typed.
      language = Json5Language.INSTANCE;
    } else if (StringUtil.startsWith(strippedText, "{")) {
      try {
        Json.Default.parseToJsonElement(strippedText);
        language = Json5Language.INSTANCE;
      } catch (SerializationException e) {
        language = Json5Language.INSTANCE;
      }
    } else if (StringUtil.startsWith(strippedText, "<")) {
      language = XMLLanguage.INSTANCE;
    } else {
      language = JavascriptLanguage.INSTANCE;
    }

    int skipWhitespaceForward = StringUtil.skipWhitespaceOrNewLineForward(hostText, skippedOffset);
    int skipWhitespaceBackward =
      StringUtil.skipWhitespaceOrNewLineBackward(hostText, host.getTextLength() - skippedOffset);
    final TextRange range = TextRange.create(skipWhitespaceForward, skipWhitespaceBackward);

    if (!range.isEmpty()) {
      String prefix = null;
      String suffix = null;
      if (language == JavascriptLanguage.INSTANCE) {
        prefix = "let x = ";
        suffix = ";";
      }

      registrar.startInjecting(language);
      registrar.addPlace(prefix, suffix, host, range);
      registrar.doneInjecting();
    }
  }
}
