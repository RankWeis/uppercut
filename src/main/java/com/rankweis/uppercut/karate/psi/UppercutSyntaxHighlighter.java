// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be 
// found in the LICENSE file.

package com.rankweis.uppercut.karate.psi;

import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.COMMENT;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.DECLARATION;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.DOUBLE_QUOTED_STRING;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.KEYWORDS;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.OPERATOR;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.PIPE;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.PYSTRING;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.PYSTRING_INCOMPLETE;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.PYSTRING_QUOTES;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.SINGLE_QUOTED_STRING;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.STEP_KEYWORD;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.TABLE_CELL;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.TAG;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.TEXT;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.VARIABLE;

import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.json5.highlighting.Json5SyntaxHighlightingFactory;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.lexer.UppercutLexer;
import com.rankweis.uppercut.karate.lexer.impl.KarateJavascriptExtension;
import com.rankweis.uppercut.settings.KarateSettingsState;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;


public class UppercutSyntaxHighlighter extends SyntaxHighlighterBase {

  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<>();
  private static final Json5SyntaxHighlightingFactory JSON_HIGHLIGHTER = new Json5SyntaxHighlightingFactory();
  private static final XmlFileHighlighter XML_HIGHLIGHTER = new XmlFileHighlighter();

  private Project project;
  private VirtualFile virtualFile;
  private final GherkinKeywordProvider myKeywordProvider;

  public UppercutSyntaxHighlighter(GherkinKeywordProvider keywordProvider) {
    myKeywordProvider = keywordProvider;
  }

  public UppercutSyntaxHighlighter(Project project, VirtualFile virtualFile, GherkinKeywordProvider keywordProvider) {
    this.project = project;
    this.virtualFile = virtualFile;
    myKeywordProvider = keywordProvider;
  }

  static {
    Arrays.stream(KEYWORDS.getTypes())
      .filter(p -> !p.equals(STEP_KEYWORD))
      .forEach(p -> ATTRIBUTES.put(p, GherkinHighlighter.KEYWORD));
    Arrays.stream(KEYWORDS.getTypes())
      .filter(p -> p.equals(STEP_KEYWORD))
      .forEach(p -> ATTRIBUTES.put(p, GherkinHighlighter.STEP_KEYWORD));

    ATTRIBUTES.put(COMMENT, GherkinHighlighter.COMMENT);
    ATTRIBUTES.put(TEXT, GherkinHighlighter.TEXT);
    ATTRIBUTES.put(OPERATOR, GherkinHighlighter.TEXT);
    ATTRIBUTES.put(DECLARATION, GherkinHighlighter.DECLARATION);
    ATTRIBUTES.put(VARIABLE, GherkinHighlighter.VARIABLE);
    ATTRIBUTES.put(TAG, GherkinHighlighter.TAG);
    ATTRIBUTES.put(PYSTRING, GherkinHighlighter.PYSTRING);
    ATTRIBUTES.put(PYSTRING_INCOMPLETE, GherkinHighlighter.PYSTRING);
    ATTRIBUTES.put(PYSTRING_QUOTES, GherkinHighlighter.PYSTRING);
    ATTRIBUTES.put(JsonElementTypes.OBJECT, GherkinHighlighter.PYSTRING);
    ATTRIBUTES.put(TABLE_CELL, GherkinHighlighter.TABLE_CELL);
    ATTRIBUTES.put(PIPE, GherkinHighlighter.PIPE);
    ATTRIBUTES.put(SINGLE_QUOTED_STRING, GherkinHighlighter.QUOTE);
    ATTRIBUTES.put(DOUBLE_QUOTED_STRING, GherkinHighlighter.QUOTE);
    ATTRIBUTES.put(KarateTokenTypes.JSON_INJECTABLE, GherkinHighlighter.KARATE_REFERENCE);
  }

  @Override
  @NotNull
  public Lexer getHighlightingLexer() {
    return new UppercutLexer(myKeywordProvider, true);
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    Optional<KarateJavascriptParsingExtensionPoint> jsExt;
    boolean useInternalEngine = KarateSettingsState.getInstance().isUseKarateJavaScriptEngine();
    if (useInternalEngine) {
      jsExt = Optional.ofNullable(KarateJavascriptExtension.EP_NAME.getExtensionList().stream().toList().getLast());
    } else {
      jsExt = KarateJavascriptExtension.EP_NAME.getExtensionList().stream().findFirst();
    }
    Optional<TextAttributesKey[]> jsTextAttributesKeys =
      jsExt
        .filter(ep -> ep.isJsLanguage(tokenType.getLanguage()))
        .map(KarateJavascriptParsingExtensionPoint::getJsSyntaxHighlighter)
        .map(h -> h.getTokenHighlights(tokenType))
        .filter(h -> h.length > 0);

    if (ATTRIBUTES.containsKey(tokenType)) {
      return SyntaxHighlighterBase.pack(ATTRIBUTES.get(tokenType));
    } else if (jsTextAttributesKeys.isPresent()) {
      return jsTextAttributesKeys.get();
    } else if (project != null
      && JSON_HIGHLIGHTER.getSyntaxHighlighter(project, virtualFile).getTokenHighlights(tokenType).length > 0) {
      return JSON_HIGHLIGHTER.getSyntaxHighlighter(project, virtualFile).getTokenHighlights(tokenType);
    } else if (XML_HIGHLIGHTER.getTokenHighlights(tokenType).length > 0) {
      return XML_HIGHLIGHTER.getTokenHighlights(tokenType);
    } else {
      return TextAttributesKey.EMPTY_ARRAY;
    }
  }
}
