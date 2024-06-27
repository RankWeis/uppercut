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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.rankweis.uppercut.karate.psi.GherkinPystring;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kotlinx.serialization.SerializationException;
import kotlinx.serialization.json.Json;
import org.jetbrains.annotations.NotNull;

public final class GherkinLanguageInjector implements MultiHostInjector {

  private final Project project;

  public GherkinLanguageInjector(Project project) {
    this.project = project;
  }

  @NotNull
  @Override
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(GherkinPystring.class);
  }

  @Override
  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull final PsiElement context) {
    if (!(context instanceof GherkinPystring)) {
      return;
    }

    final PsiLanguageInjectionHost host = (PsiLanguageInjectionHost) context;

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
    if (StringUtil.startsWith(strippedText, "{")) {
      try {
        Json.Default.parseToJsonElement(strippedText);
        language = Json5Language.INSTANCE;
      } catch (SerializationException e) {
        language = JavascriptLanguage.INSTANCE;
      }
    } else if (StringUtil.startsWith(strippedText, "<")) {
      language = XMLLanguage.INSTANCE;
    } else {
      language = JavascriptLanguage.INSTANCE;
    }

    if (language != null) {
      int skipWhitespaceForward = StringUtil.skipWhitespaceOrNewLineForward(hostText, skippedOffset);
      int skipWhitespaceBackward =
        StringUtil.skipWhitespaceOrNewLineBackward(hostText, host.getTextLength() - skippedOffset);
      final TextRange range = TextRange.create(skipWhitespaceForward, skipWhitespaceBackward);
      List<TextRangeWithPrefixSuffix> rangesForSplit = new ArrayList<>();
      String text = range.substring(host.getText());
      String currentSplit = text;
      int firstTag = StringUtil.indexOf(currentSplit, "#(");
      String prefix = null;
      String suffix = null;
      if (language == JavascriptLanguage.INSTANCE) {
        prefix = "let a = ";
        suffix = ";";
      }
      int lastEnd = skipWhitespaceForward;
      if ( firstTag >= 0) {
        lastEnd += firstTag;
        rangesForSplit.add(new TextRangeWithPrefixSuffix(new TextRange(skipWhitespaceForward, lastEnd), prefix, null));
        firstTag = 0;
        currentSplit = currentSplit.substring(lastEnd);
        
        while (firstTag >= 0) {
          TextRange textRange = TextRange.create(lastEnd, lastEnd + firstTag);
          char c = text.charAt(textRange.getStartOffset() - 1);
          String prefixSuffix = (c == '\'' || c == '"') ? null : "'";
          rangesForSplit.add(new TextRangeWithPrefixSuffix(textRange, prefixSuffix, null));
          int endTag = StringUtil.indexOf(currentSplit, ")");
          if (endTag >= 0) {
            TextRange textRangeEndTag = TextRange.create(lastEnd, lastEnd + endTag + 1);
            String test = textRangeEndTag.substring(text);
            rangesForSplit.add(new TextRangeWithPrefixSuffix(textRangeEndTag, null, prefixSuffix));
            lastEnd += endTag + 1;
            currentSplit = currentSplit.substring(endTag + 1);
          }
          firstTag = StringUtil.indexOf(currentSplit, "#(");
        }
        rangesForSplit.add(new TextRangeWithPrefixSuffix(new TextRange(lastEnd, skipWhitespaceBackward), null, suffix));
      }

//      String test = rangesForSplit.stream()
//        .map(
//          r -> (r.prefix == null ? "" : r.prefix) + r.textRange.subSequence(text) + (r.suffix == null ? "" : r.suffix))
//        .collect(Collectors.joining());
      if (!rangesForSplit.isEmpty()) {
        registrar.startInjecting(language);
        for (int i = 0; i < rangesForSplit.size(); i++) {
          TextRangeWithPrefixSuffix textRangeWithPrefixSuffix = rangesForSplit.get(i);
          String addPrefix = textRangeWithPrefixSuffix.prefix;
          String addSuffix = textRangeWithPrefixSuffix.suffix;
          registrar.addPlace(addPrefix, addSuffix, host, textRangeWithPrefixSuffix.textRange);
        }
        registrar.doneInjecting();
      } else {
        if (!range.isEmpty()) {
          registrar.startInjecting(language)
            .addPlace(prefix, suffix, host, range)
            .doneInjecting();
        }
      }
    }
  }
  
  private class TextRangeWithPrefixSuffix {
    TextRange textRange;
    String prefix;
    String suffix;

    public TextRangeWithPrefixSuffix(TextRange textRange, String prefix, String suffix) {
      this.textRange = textRange;
      this.prefix = prefix;
      this.suffix = suffix;
    }
  }
}
