package com.rankweis.uppercut.karate.psi;

import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.CLOSE_PAREN;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.DECLARATION;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.DOUBLE_QUOTED_STRING;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.OPEN_PAREN;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.OPERATOR;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.PYSTRING_QUOTES;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.SINGLE_QUOTED_STRING;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.TEXT_LIKE;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.VARIABLE;
import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.JAVASCRIPT;
import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.JSON;
import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.TEXT_BLOCK;
import static com.rankweis.uppercut.karate.psi.UppercutElementTypes.XML;

import com.intellij.json.JsonLanguage;
import com.intellij.json.json5.Json5Language;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.lang.PsiParser;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.rankweis.uppercut.karate.lexer.KarateJavascriptParsingExtensionPoint;
import com.rankweis.uppercut.karate.lexer.impl.KarateJavascriptExtension;
import com.rankweis.uppercut.parser.KarateJsonParser;
import com.rankweis.uppercut.settings.KarateSettingsState;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UppercutParser implements PsiParser {

  private @NonNls @Nullable String overrideInjection;

  LinkedList<PsiBuilder.Marker> parens = new LinkedList<>();
  private static final TokenSet SCENARIO_END_TOKENS =
    TokenSet.create(KarateTokenTypes.BACKGROUND_KEYWORD, KarateTokenTypes.SCENARIO_KEYWORD,
      KarateTokenTypes.SCENARIO_OUTLINE_KEYWORD, KarateTokenTypes.RULE_KEYWORD, KarateTokenTypes.FEATURE_KEYWORD);

  @Override
  @NotNull
  public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    parseFileTopLevel(builder);
    marker.done(UppercutParserDefinition.KARATE_FILE);
    return builder.getTreeBuilt();
  }

  private void parseFileTopLevel(PsiBuilder builder) {
    while (!builder.eof()) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == KarateTokenTypes.FEATURE_KEYWORD) {
        parseFeature(builder);
      } else if (tokenType == KarateTokenTypes.TAG) {
        parseTags(builder);
      } else if (isPystring(tokenType)) {
        parsePystring(builder);
      } else {
        builder.advanceLexer();
      }
    }
  }

  private void parseFeature(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();

    assert builder.getTokenType() == KarateTokenTypes.FEATURE_KEYWORD;
    final int featureEnd = builder.getCurrentOffset() + getTokenLength(builder.getTokenText());

    PsiBuilder.Marker descMarker = null;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (TEXT_LIKE.contains(tokenType) && descMarker == null) {
        if (hadLineBreakBefore(builder, featureEnd)) {
          descMarker = builder.mark();
        }
      }

      if (KarateTokenTypes.SCENARIOS_KEYWORDS.contains(tokenType) || tokenType == KarateTokenTypes.RULE_KEYWORD
        || tokenType == KarateTokenTypes.BACKGROUND_KEYWORD || tokenType == KarateTokenTypes.TAG) {
        if (descMarker != null) {
          descMarker.done(UppercutElementTypes.FEATURE_HEADER);
          descMarker = null;
        }
        parseFeatureElements(builder);

        if (builder.getTokenType() == KarateTokenTypes.FEATURE_KEYWORD) {
          break;
        }
      }
      if (isPystring(tokenType)) {
        parsePystring(builder);
      }
      builder.advanceLexer();
      if (builder.eof()) {
        break;
      }
    }
    if (descMarker != null) {
      descMarker.done(UppercutElementTypes.FEATURE_HEADER);
    }
    marker.done(UppercutElementTypes.FEATURE);
  }

  private boolean hadLineBreakBefore(PsiBuilder builder, int prevTokenEnd) {
    if (prevTokenEnd < 0 || prevTokenEnd > builder.getCurrentOffset()) {
      return false;
    }
    final String precedingText =
      builder.getOriginalText().subSequence(prevTokenEnd, builder.getCurrentOffset()).toString();
    return precedingText.contains("\n");
  }

  private void parseTags(PsiBuilder builder) {
    while (builder.getTokenType() == KarateTokenTypes.TAG) {
      final PsiBuilder.Marker tagMarker = builder.mark();
      builder.advanceLexer();
      tagMarker.done(UppercutElementTypes.TAG);
    }
  }

  private void parseFeatureElements(PsiBuilder builder) {
    PsiBuilder.Marker ruleMarker = null;
    while (builder.getTokenType() != KarateTokenTypes.FEATURE_KEYWORD && !builder.eof()) {
      if (builder.getTokenType() == KarateTokenTypes.RULE_KEYWORD) {
        if (ruleMarker != null) {
          ruleMarker.done(UppercutElementTypes.RULE);
        }
        ruleMarker = builder.mark();
        builder.advanceLexer();
        if (builder.getTokenType() == KarateTokenTypes.COLON) {
          builder.advanceLexer();
        } else {
          break;
        }

        while (TEXT_LIKE.contains(builder.getTokenType())) {
          builder.advanceLexer();
        }
      }

      final PsiBuilder.Marker marker = builder.mark();
      // tags
      parseTags(builder);

      // scenarios
      IElementType startTokenType = builder.getTokenType();
      final boolean outline = startTokenType == KarateTokenTypes.SCENARIO_OUTLINE_KEYWORD;
      builder.advanceLexer();
      parseScenario(builder);
      marker.done(outline ? UppercutElementTypes.SCENARIO_OUTLINE : UppercutElementTypes.SCENARIO);
    }
    if (ruleMarker != null) {
      ruleMarker.done(UppercutElementTypes.RULE);
    }
  }

  private void parseScenario(PsiBuilder builder) {
    while (!atScenarioEnd(builder)) {
      if (builder.getTokenType() == KarateTokenTypes.TAG) {
        final PsiBuilder.Marker marker = builder.mark();
        parseTags(builder);
        if (atScenarioEnd(builder)) {
          marker.rollbackTo();
          break;
        } else {
          marker.drop();
        }
      }

      if (parseStepParameter(builder)) {
        continue;
      }

      if (builder.getTokenType() == KarateTokenTypes.STEP_KEYWORD) {
        parseStep(builder);
      } else if (builder.getTokenType() == KarateTokenTypes.EXAMPLES_KEYWORD) {
        parseExamplesBlock(builder);
      } else if (isPystring(builder.getTokenType())) {
        parsePystring(builder);
      } else {
        builder.advanceLexer();
      }
    }
  }

  private boolean atScenarioEnd(PsiBuilder builder) {
    int i = 0;
    while (builder.lookAhead(i) == KarateTokenTypes.TAG) {
      i++;
    }
    final IElementType tokenType = builder.lookAhead(i);
    return tokenType == null || SCENARIO_END_TOKENS.contains(tokenType);
  }

  private boolean parseStepParameter(PsiBuilder builder) {
    if (builder.getTokenType() == KarateTokenTypes.STEP_PARAMETER_TEXT) {
      final PsiBuilder.Marker stepParameterMarker = builder.mark();
      builder.advanceLexer();
      stepParameterMarker.done(UppercutElementTypes.STEP_PARAMETER);
      return true;
    }
    return false;
  }

  private boolean parseDeclaration(PsiBuilder builder) {
    if (builder.getTokenType() == DECLARATION) {
      final PsiBuilder.Marker stepParameterMarker = builder.mark();
      builder.advanceLexer();
      stepParameterMarker.done(UppercutElementTypes.DECLARATION);
      return true;
    }
    return false;
  }

  private boolean parseVariable(PsiBuilder builder) {
    if (builder.getTokenType() == VARIABLE) {
      final PsiBuilder.Marker stepParameterMarker = builder.mark();
      builder.advanceLexer();
      stepParameterMarker.done(UppercutElementTypes.VARIABLE);
      return true;
    }
    return false;
  }

  private void parseStep(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    overrideInjection = null;
    builder.advanceLexer();
    parseTextLikeObjects(builder);
    final IElementType tokenTypeAfterName = builder.getTokenType();
    if (tokenTypeAfterName == KarateTokenTypes.PIPE) {
      parseTable(builder);
    } else if (isPystring(tokenTypeAfterName)) {
      parsePystring(builder);
    }
    final IElementType tokenTypeAfterPyString = builder.getTokenType();
    if (tokenTypeAfterPyString != tokenTypeAfterName && TEXT_LIKE.contains(tokenTypeAfterPyString)) {
      parseStep(builder);
    }

    marker.done(UppercutElementTypes.STEP);
  }

  private void parseTextLikeObjects(PsiBuilder builder) {
    int prevTokenEnd = -1;
    while (TEXT_LIKE.contains(builder.getTokenType()) || builder.getTokenType() == OPEN_PAREN
      || builder.getTokenType() == CLOSE_PAREN || builder.getTokenType() == KarateTokenTypes.STEP_PARAMETER_BRACE
      || builder.getTokenType() == KarateTokenTypes.STEP_PARAMETER_TEXT
      || builder.getTokenType() == KarateTokenTypes.ACTION_KEYWORD || builder.getTokenType() == DECLARATION
      || builder.getTokenType() == VARIABLE || builder.getTokenType() == SINGLE_QUOTED_STRING
      || builder.getTokenType() == DOUBLE_QUOTED_STRING || isPystring(builder.getTokenType())) {
      if (isPystring(builder.getTokenType())) {
        parsePystring(builder);
        continue;
      }
      if (KarateTokenTypes.TEXT == builder.getTokenType()) {
        Marker reset = builder.mark();
        String overridenType = builder.getTokenText();
        builder.advanceLexer();
        if (KarateTokenTypes.TEXT == builder.getTokenType()) {
          builder.advanceLexer();
          if (OPERATOR == builder.getTokenType()) {
            overrideInjection = overridenType;
          } else {
            overrideInjection = null;
          }
          builder.advanceLexer();
        }
        reset.rollbackTo();
      }
      if (hadLineBreakBefore(builder, prevTokenEnd) || builder.eof()) {
        if (!parens.isEmpty()) {
          while (!parens.isEmpty()) {
            parens.pop().rollbackTo();
            builder.error("Unbalanced parentheses");
          }
        }
        break;
      }
      String tokenText = builder.getTokenText();
      prevTokenEnd = builder.getCurrentOffset() + getTokenLength(tokenText);
      if (builder.getTokenType() == OPEN_PAREN) {
        final PsiBuilder.Marker marker = builder.mark();
        parens.push(marker);
        builder.advanceLexer();
        continue;
      }
      if (builder.getTokenType() == CLOSE_PAREN) {
        if (!parens.isEmpty()) {
          parens.pop().done(UppercutElementTypes.PAREN_ELEMENT);
        } else {
          builder.error("Unbalanced parentheses");
        }
        builder.advanceLexer();
        continue;
      }
      if (!parseStepParameter(builder) && !parseDeclaration(builder) && !parseVariable(builder)) {
        builder.advanceLexer();
      }
    }
    if (!parens.isEmpty()) {
      while (!parens.isEmpty()) {
        parens.pop().rollbackTo();
        builder.error("Unbalanced parentheses");
      }
    }
  }

  private void parsePystring(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    if (builder.eof()) {
      marker.done(UppercutElementTypes.PYSTRING);
      return;
    }
    if (builder.getTokenType() == PYSTRING_QUOTES) {
      if (builder.getTokenType() == KarateTokenTypes.PYSTRING_QUOTES) {
        builder.advanceLexer();
        if (builder.eof()) {
          marker.done(UppercutElementTypes.PYSTRING);
          return;
        }
      }
    }
    Optional<KarateJavascriptParsingExtensionPoint> jsExt;
    boolean useInternalEngine = KarateSettingsState.getInstance().isUseKarateJavaScriptEngine();
    if (useInternalEngine) {
      jsExt = Optional.ofNullable(KarateJavascriptExtension.EP_NAME.getExtensionList().stream().toList().getLast());
    } else {
      jsExt = KarateJavascriptExtension.EP_NAME.getExtensionList().stream().findFirst();
    }
    if (overrideInjection != null && overrideInjection.equalsIgnoreCase("text")) {
      Language l = Objects.requireNonNull(builder.getTokenType()).getLanguage();
      Marker mark = builder.mark();
      while (builder.getTokenType().getLanguage() == l) {
        builder.remapCurrentToken(TEXT_BLOCK);
        builder.advanceLexer();
      }
      mark.done(TEXT_BLOCK);
    } else {
      if (jsExt.map(j -> j.isJsLanguage(Objects.requireNonNull(builder.getTokenType()).getLanguage())).orElse(false)) {
        parseLanguage(builder, JAVASCRIPT, jsExt.map(KarateJavascriptParsingExtensionPoint::parseJs).orElseThrow());
      } else if (builder.getTokenType() != null && (builder.getTokenType().getLanguage() == Json5Language.INSTANCE
        || builder.getTokenType().getLanguage() == JsonLanguage.INSTANCE)) {
        new KarateJsonParser().parseLight(JSON, builder);
      } else if (builder.getTokenType() != null && builder.getTokenType().getLanguage() == XMLLanguage.INSTANCE) {
        parseLanguage(builder, XML, (b) -> {
          while (!b.eof() && Objects.requireNonNull(b.getTokenType()).getLanguage() == XMLLanguage.INSTANCE) {
            builder.advanceLexer();
          }
        });
      } else {
        builder.advanceLexer();
      }
    }
    if (builder.getTokenType() == PYSTRING_QUOTES) {
      builder.advanceLexer();
    }
    marker.done(UppercutElementTypes.PYSTRING);
  }

  private static void parseLanguage(PsiBuilder builder, IElementType closingTag, Consumer<PsiBuilder> doParse) {

    PsiBuilder.Marker languageMarker = null;
    if (closingTag != null) {
      languageMarker = builder.mark();
    }
    if (!builder.eof() && builder.getTokenType() != null
      && builder.getTokenType() != KarateTokenTypes.PYSTRING_QUOTES) {
      doParse.accept(builder);
      if (languageMarker != null) {
        languageMarker.done(closingTag);
      }
    }
  }

  private void parseExamplesBlock(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == KarateTokenTypes.COLON) {
      builder.advanceLexer();
    }
    while (builder.getTokenType() == KarateTokenTypes.TEXT) {
      builder.advanceLexer();
    }
    if (builder.getTokenType() == KarateTokenTypes.PIPE) {
      parseTable(builder);
    }
    marker.done(UppercutElementTypes.EXAMPLES_BLOCK);
  }

  private void parseTable(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    PsiBuilder.Marker rowMarker = builder.mark();
    int prevCellEnd = -1;
    boolean isHeaderRow = true;
    PsiBuilder.Marker cellMarker = null;

    IElementType prevToken = null;
    while (builder.getTokenType() == KarateTokenTypes.PIPE || builder.getTokenType() == KarateTokenTypes.TABLE_CELL) {
      final IElementType tokenType = builder.getTokenType();

      final boolean hasLineBreakBefore = hadLineBreakBefore(builder, prevCellEnd);

      // cell - is all between pipes
      if (prevToken == KarateTokenTypes.PIPE) {
        // Don't start new cell if prev was last in the row
        // it's not a cell, we just need to close a row
        if (!hasLineBreakBefore) {
          cellMarker = builder.mark();
        }
      }
      if (tokenType == KarateTokenTypes.PIPE) {
        if (cellMarker != null) {
          closeCell(cellMarker);
          cellMarker = null;
        }
      }

      if (hasLineBreakBefore) {
        closeRowMarker(rowMarker, isHeaderRow);
        isHeaderRow = false;
        rowMarker = builder.mark();
      }
      prevCellEnd = builder.getCurrentOffset() + getTokenLength(builder.getTokenText());
      prevToken = tokenType;
      builder.advanceLexer();
    }

    if (cellMarker != null) {
      closeCell(cellMarker);
    }
    closeRowMarker(rowMarker, isHeaderRow);
    marker.done(UppercutElementTypes.TABLE);
  }

  private void closeCell(PsiBuilder.Marker cellMarker) {
    cellMarker.done(UppercutElementTypes.TABLE_CELL);
  }

  private void closeRowMarker(PsiBuilder.Marker rowMarker, boolean headerRow) {
    rowMarker.done(headerRow ? UppercutElementTypes.TABLE_HEADER_ROW : UppercutElementTypes.TABLE_ROW);
  }

  private int getTokenLength(@Nullable final String tokenText) {
    return tokenText != null ? tokenText.length() : 0;
  }

  private boolean isPystring(IElementType tokenType) {
    if (tokenType == null) {
      return false;
    }
    boolean useInternalEngine = KarateSettingsState.getInstance().isUseKarateJavaScriptEngine();
    KarateJavascriptParsingExtensionPoint ex;
    if (useInternalEngine) {
      ex = KarateJavascriptExtension.EP_NAME.getExtensionList().stream().toList().getLast();
    } else {
      ex = KarateJavascriptExtension.EP_NAME.getExtensionList().stream().findFirst().get();
    }
    return tokenType == KarateTokenTypes.PYSTRING || tokenType == PYSTRING_QUOTES || ex.isJsLanguage(
      tokenType.getLanguage()) || tokenType.getLanguage().is(Json5Language.INSTANCE) || tokenType.getLanguage()
      .is(JsonLanguage.INSTANCE) || tokenType.getLanguage().is(XMLLanguage.INSTANCE);
  }
}
