// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be 
// found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import static com.intellij.json.JsonElementTypes.DOUBLE_QUOTED_STRING;
import static com.intellij.json.JsonElementTypes.L_CURLY;
import static com.intellij.json.JsonElementTypes.R_CURLY;
import static com.intellij.json.JsonElementTypes.SINGLE_QUOTED_STRING;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.COMMENT;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.DECLARATION;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.KEYWORDS;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.PIPE;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.PYSTRING;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.QUOTE;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.STEP_KEYWORD;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.TABLE_CELL;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.TAG;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.TEXT;

import com.intellij.ide.highlighter.EmbeddedTokenHighlighter;
import com.intellij.json.JsonElementTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.MultiMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;


public class GherkinSyntaxHighlighter extends SyntaxHighlighterBase implements EmbeddedTokenHighlighter {

  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<>();

  private final GherkinKeywordProvider myKeywordProvider;

  public GherkinSyntaxHighlighter(GherkinKeywordProvider keywordProvider) {
    myKeywordProvider = keywordProvider;
  }

  static {
    Arrays.stream(KEYWORDS.getTypes())
      .filter(p -> !p.equals(STEP_KEYWORD))
      .forEach(p -> ATTRIBUTES.put(p, GherkinHighlighter.KEYWORD));
    Arrays.stream(KEYWORDS.getTypes())
      .filter(p -> p.equals(STEP_KEYWORD))
      .forEach(p -> ATTRIBUTES.put(p, GherkinHighlighter.COMMENT));

    ATTRIBUTES.put(COMMENT, GherkinHighlighter.COMMENT);
    ATTRIBUTES.put(TEXT, GherkinHighlighter.TEXT);
    ATTRIBUTES.put(DECLARATION, GherkinHighlighter.DECLARATION);
    ATTRIBUTES.put(TAG, GherkinHighlighter.TAG);
    ATTRIBUTES.put(PYSTRING, GherkinHighlighter.PYSTRING);
    ATTRIBUTES.put(JsonElementTypes.OBJECT, GherkinHighlighter.PYSTRING);
    ATTRIBUTES.put(TABLE_CELL, GherkinHighlighter.TABLE_CELL);
    ATTRIBUTES.put(PIPE, GherkinHighlighter.PIPE);
    ATTRIBUTES.put(QUOTE, GherkinHighlighter.QUOTE);
    ATTRIBUTES.put(L_CURLY, GherkinHighlighter.QUOTE);
    ATTRIBUTES.put(R_CURLY, GherkinHighlighter.QUOTE);
    ATTRIBUTES.put(SINGLE_QUOTED_STRING, GherkinHighlighter.QUOTE);
    ATTRIBUTES.put(DOUBLE_QUOTED_STRING, GherkinHighlighter.QUOTE);
  }

  @Override
  @NotNull
  public Lexer getHighlightingLexer() {
    return new GherkinLexer(myKeywordProvider);
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    return SyntaxHighlighterBase.pack(ATTRIBUTES.get(tokenType));
  }


  @Override public @NotNull MultiMap<IElementType, TextAttributesKey> getEmbeddedTokenAttributes() {
    MultiMap<IElementType, TextAttributesKey> map = MultiMap.create();
    map.putAllValues(ATTRIBUTES);
    return map;
  }
  
  
}
