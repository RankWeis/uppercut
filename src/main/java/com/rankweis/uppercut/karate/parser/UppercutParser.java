package com.rankweis.uppercut.karate.parser;

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
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import com.rankweis.uppercut.karate.psi.UppercutElementTypes;
import com.rankweis.uppercut.settings.KarateSettingsState;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parser for Karate (.feature) files.
 *
 * <p>Parses the Gherkin structure (Feature, Scenario, Step, Table, etc.)
 * and delegates embedded content (JavaScript, JSON, XML) to sub-parsers.
 *
 * <p>Grammar overview:
 * <pre>
 *   File           → Tag* Feature*
 *   Feature        → FEATURE_KEYWORD FeatureHeader? FeatureElements
 *   FeatureElements→ (Rule | Tag* Scenario)*
 *   Scenario       → (SCENARIO_KEYWORD | SCENARIO_OUTLINE_KEYWORD) Step* ExamplesBlock?
 *   Step           → STEP_KEYWORD StepContent (Table | Pystring)?
 *   StepContent    → (TEXT | OPERATOR | Declaration | Variable | Paren | QuotedString)*
 *   Pystring       → PYSTRING_QUOTES EmbeddedContent PYSTRING_QUOTES
 *   Table          → (PIPE TableCell)+ per row
 *   ExamplesBlock  → EXAMPLES_KEYWORD COLON? Table?
 * </pre>
 */
public class UppercutParser implements PsiParser {

  /**
   * When non-null, overrides the detected injection type for the next pystring.
   * Set when step text contains a type hint like {@code text foo =} before
   * an embedded block — the word before the operator becomes the override.
   */
  private @Nullable String overrideInjection;

  private static final TokenSet SCENARIO_END_TOKENS =
    TokenSet.create(KarateTokenTypes.BACKGROUND_KEYWORD, KarateTokenTypes.SCENARIO_KEYWORD,
      KarateTokenTypes.SCENARIO_OUTLINE_KEYWORD, KarateTokenTypes.RULE_KEYWORD,
      KarateTokenTypes.FEATURE_KEYWORD);

  @Override
  @NotNull
  public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    parseFileTopLevel(builder);
    marker.done(UppercutParserDefinition.KARATE_FILE);
    return builder.getTreeBuilt();
  }

  // ── Top-level structure ──────────────────────────────────────────

  private void parseFileTopLevel(PsiBuilder builder) {
    while (!builder.eof()) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == KarateTokenTypes.FEATURE_KEYWORD) {
        parseFeature(builder);
      } else if (tokenType == KarateTokenTypes.TAG) {
        parseTags(builder);
      } else if (isEmbeddedContent(tokenType)) {
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

      if (KarateTokenTypes.SCENARIOS_KEYWORDS.contains(tokenType)
        || tokenType == KarateTokenTypes.RULE_KEYWORD
        || tokenType == KarateTokenTypes.BACKGROUND_KEYWORD
        || tokenType == KarateTokenTypes.TAG) {
        if (descMarker != null) {
          descMarker.done(UppercutElementTypes.FEATURE_HEADER);
          descMarker = null;
        }
        parseFeatureElements(builder);

        if (builder.getTokenType() == KarateTokenTypes.FEATURE_KEYWORD) {
          break;
        }
      }
      if (isEmbeddedContent(tokenType)) {
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

  // ── Feature elements (Rules, Scenarios) ──────────────────────────

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
      parseTags(builder);

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

  // ── Scenario ─────────────────────────────────────────────────────

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
      } else if (isEmbeddedContent(builder.getTokenType())) {
        parsePystring(builder);
      } else {
        builder.advanceLexer();
      }
    }
  }

  /**
   * Looks ahead past any tags to check if the next non-tag token ends the
   * current scenario (i.e., starts a new scenario, background, rule, or feature).
   */
  private boolean atScenarioEnd(PsiBuilder builder) {
    int i = 0;
    while (builder.lookAhead(i) == KarateTokenTypes.TAG) {
      i++;
    }
    final IElementType tokenType = builder.lookAhead(i);
    return tokenType == null || SCENARIO_END_TOKENS.contains(tokenType);
  }

  // ── Steps ────────────────────────────────────────────────────────

  private void parseStep(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    overrideInjection = null;
    builder.advanceLexer();
    parseStepContent(builder);
    final IElementType tokenTypeAfterName = builder.getTokenType();
    if (tokenTypeAfterName == KarateTokenTypes.PIPE) {
      parseTable(builder);
    } else if (isEmbeddedContent(tokenTypeAfterName)) {
      parsePystring(builder);
    }
    // If there's more text after a pystring, parse it as a continuation step
    final IElementType tokenTypeAfterPyString = builder.getTokenType();
    if (tokenTypeAfterPyString != tokenTypeAfterName && TEXT_LIKE.contains(tokenTypeAfterPyString)) {
      parseStep(builder);
    }

    marker.done(UppercutElementTypes.STEP);
  }

  /**
   * Parses the inline content of a step line: text, operators, variables,
   * declarations, parenthesized expressions, step parameters, and quoted strings.
   * Also detects override injection hints (e.g., {@code text foo =}) that tell
   * {@link #parsePystring} to treat the following block as plain text.
   */
  private void parseStepContent(PsiBuilder builder) {
    LinkedList<PsiBuilder.Marker> parenStack = new LinkedList<>();
    int prevTokenEnd = -1;

    while (isStepContentToken(builder.getTokenType())) {
      if (isEmbeddedContent(builder.getTokenType())) {
        parsePystring(builder);
        continue;
      }
      detectOverrideInjection(builder);
      if (hadLineBreakBefore(builder, prevTokenEnd) || builder.eof()) {
        drainUnbalancedParens(builder, parenStack);
        break;
      }
      prevTokenEnd = builder.getCurrentOffset() + getTokenLength(builder.getTokenText());
      if (advanceParenOrNamedToken(builder, parenStack)) {
        continue;
      }
      builder.advanceLexer();
    }
    drainUnbalancedParens(builder, parenStack);
  }

  /**
   * Returns true if the token type can appear within step content on the same line.
   */
  private boolean isStepContentToken(@Nullable IElementType type) {
    return TEXT_LIKE.contains(type)
      || type == OPEN_PAREN
      || type == CLOSE_PAREN
      || type == KarateTokenTypes.STEP_PARAMETER_BRACE
      || type == KarateTokenTypes.STEP_PARAMETER_TEXT
      || type == KarateTokenTypes.ACTION_KEYWORD
      || type == DECLARATION
      || type == VARIABLE
      || type == SINGLE_QUOTED_STRING
      || type == DOUBLE_QUOTED_STRING
      || isEmbeddedContent(type);
  }

  /**
   * Looks ahead for a {@code TEXT TEXT OPERATOR} pattern that hints the
   * following pystring should be treated as a specific content type
   * (e.g., "text" means plain text instead of JS/JSON). Uses rollback
   * so the builder position is unchanged after this call.
   */
  private void detectOverrideInjection(PsiBuilder builder) {
    if (KarateTokenTypes.TEXT != builder.getTokenType()) {
      return;
    }
    Marker reset = builder.mark();
    String candidateType = builder.getTokenText();
    builder.advanceLexer();
    if (KarateTokenTypes.TEXT == builder.getTokenType()) {
      builder.advanceLexer();
      if (OPERATOR == builder.getTokenType()) {
        overrideInjection = candidateType;
      } else {
        overrideInjection = null;
      }
      builder.advanceLexer();
    }
    reset.rollbackTo();
  }

  /**
   * Handles parenthesized expressions and named tokens (step parameters,
   * declarations, variables). Returns true if a token was consumed.
   */
  private boolean advanceParenOrNamedToken(PsiBuilder builder, LinkedList<Marker> parenStack) {
    if (builder.getTokenType() == OPEN_PAREN) {
      parenStack.push(builder.mark());
      builder.advanceLexer();
      return true;
    }
    if (builder.getTokenType() == CLOSE_PAREN) {
      if (!parenStack.isEmpty()) {
        parenStack.pop().done(UppercutElementTypes.PAREN_ELEMENT);
      } else {
        builder.error("Unbalanced parentheses");
      }
      builder.advanceLexer();
      return true;
    }
    return parseStepParameter(builder) || parseDeclaration(builder) || parseVariable(builder);
  }

  private static void drainUnbalancedParens(PsiBuilder builder, LinkedList<Marker> parenStack) {
    while (!parenStack.isEmpty()) {
      parenStack.pop().rollbackTo();
      builder.error("Unbalanced parentheses");
    }
  }

  // ── Named token helpers ──────────────────────────────────────────

  private boolean parseStepParameter(PsiBuilder builder) {
    if (builder.getTokenType() == KarateTokenTypes.STEP_PARAMETER_TEXT) {
      final PsiBuilder.Marker m = builder.mark();
      builder.advanceLexer();
      m.done(UppercutElementTypes.STEP_PARAMETER);
      return true;
    }
    return false;
  }

  private boolean parseDeclaration(PsiBuilder builder) {
    if (builder.getTokenType() == DECLARATION) {
      final PsiBuilder.Marker m = builder.mark();
      builder.advanceLexer();
      m.done(UppercutElementTypes.DECLARATION);
      return true;
    }
    return false;
  }

  private boolean parseVariable(PsiBuilder builder) {
    if (builder.getTokenType() == VARIABLE) {
      final PsiBuilder.Marker m = builder.mark();
      builder.advanceLexer();
      m.done(UppercutElementTypes.VARIABLE);
      return true;
    }
    return false;
  }

  // ── Embedded content (pystrings, JS, JSON, XML) ──────────────────

  /**
   * Parses a pystring block: the triple-quote delimiters plus the embedded
   * content between them. Delegates to the appropriate sub-parser based on
   * the detected language (or {@link #overrideInjection} if set).
   */
  private void parsePystring(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    if (builder.eof()) {
      marker.done(UppercutElementTypes.PYSTRING);
      return;
    }
    if (builder.getTokenType() == PYSTRING_QUOTES) {
      builder.advanceLexer();
      if (builder.eof()) {
        marker.done(UppercutElementTypes.PYSTRING);
        return;
      }
    }
    parseEmbeddedContent(builder);
    if (builder.getTokenType() == PYSTRING_QUOTES) {
      builder.advanceLexer();
    }
    marker.done(UppercutElementTypes.PYSTRING);
  }

  /**
   * Dispatches embedded content to the correct sub-parser based on the
   * token's language or the override injection hint.
   */
  private void parseEmbeddedContent(PsiBuilder builder) {
    if (overrideInjection != null && overrideInjection.equalsIgnoreCase("text")) {
      parseAsPlainText(builder);
      return;
    }
    IElementType tokenType = builder.getTokenType();
    if (tokenType == null) {
      return;
    }
    KarateJavascriptParsingExtensionPoint jsExt = getJsExtension();
    if (jsExt.isJsLanguage(tokenType.getLanguage())) {
      parseLanguage(builder, JAVASCRIPT, jsExt.parseJs());
    } else if (isJsonLanguage(tokenType.getLanguage())) {
      new KarateJsonParser().parseLight(JSON, builder);
    } else if (tokenType.getLanguage() == XMLLanguage.INSTANCE) {
      parseLanguage(builder, XML, b -> {
        while (!b.eof()
          && Objects.requireNonNull(b.getTokenType()).getLanguage() == XMLLanguage.INSTANCE) {
          b.advanceLexer();
        }
      });
    } else {
      builder.advanceLexer();
    }
  }

  private static void parseAsPlainText(PsiBuilder builder) {
    Language lang = Objects.requireNonNull(builder.getTokenType()).getLanguage();
    Marker mark = builder.mark();
    while (builder.getTokenType() != null && builder.getTokenType().getLanguage() == lang) {
      builder.remapCurrentToken(TEXT_BLOCK);
      builder.advanceLexer();
    }
    mark.done(TEXT_BLOCK);
  }

  private static void parseLanguage(
    PsiBuilder builder, IElementType elementType, Consumer<PsiBuilder> doParse) {
    PsiBuilder.Marker languageMarker = builder.mark();
    if (!builder.eof() && builder.getTokenType() != null
      && builder.getTokenType() != KarateTokenTypes.PYSTRING_QUOTES) {
      doParse.accept(builder);
      languageMarker.done(elementType);
    } else {
      languageMarker.drop();
    }
  }

  // ── Tags ─────────────────────────────────────────────────────────

  private void parseTags(PsiBuilder builder) {
    while (builder.getTokenType() == KarateTokenTypes.TAG) {
      final PsiBuilder.Marker tagMarker = builder.mark();
      builder.advanceLexer();
      tagMarker.done(UppercutElementTypes.TAG);
    }
  }

  // ── Examples & Tables ────────────────────────────────────────────

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
    while (builder.getTokenType() == KarateTokenTypes.PIPE
      || builder.getTokenType() == KarateTokenTypes.TABLE_CELL) {
      final IElementType tokenType = builder.getTokenType();
      final boolean hasLineBreakBefore = hadLineBreakBefore(builder, prevCellEnd);

      // Start a new cell after a pipe (unless we're at a row boundary)
      if (prevToken == KarateTokenTypes.PIPE && !hasLineBreakBefore) {
        cellMarker = builder.mark();
      }
      if (tokenType == KarateTokenTypes.PIPE && cellMarker != null) {
        cellMarker.done(UppercutElementTypes.TABLE_CELL);
        cellMarker = null;
      }

      // Line break means a new row
      if (hasLineBreakBefore) {
        rowMarker.done(
          isHeaderRow ? UppercutElementTypes.TABLE_HEADER_ROW : UppercutElementTypes.TABLE_ROW);
        isHeaderRow = false;
        rowMarker = builder.mark();
      }
      prevCellEnd = builder.getCurrentOffset() + getTokenLength(builder.getTokenText());
      prevToken = tokenType;
      builder.advanceLexer();
    }

    if (cellMarker != null) {
      cellMarker.done(UppercutElementTypes.TABLE_CELL);
    }
    rowMarker.done(
      isHeaderRow ? UppercutElementTypes.TABLE_HEADER_ROW : UppercutElementTypes.TABLE_ROW);
    marker.done(UppercutElementTypes.TABLE);
  }

  // ── Utilities ────────────────────────────────────────────────────

  /**
   * Returns the active JavaScript parsing extension based on user settings.
   * When the internal Karate JS engine is selected, uses the last registered
   * extension (the fallback); otherwise uses the first (the IntelliJ JS plugin).
   */
  private static KarateJavascriptParsingExtensionPoint getJsExtension() {
    boolean useInternalEngine =
      KarateSettingsState.getInstance().isUseKarateJavaScriptEngine();
    var extensions = KarateJavascriptExtension.EP_NAME.getExtensionList();
    return useInternalEngine ? extensions.getLast() : extensions.getFirst();
  }

  /**
   * Returns true if the token type represents embedded content that starts
   * a pystring or injected language block (JS, JSON, XML, or a pystring delimiter).
   */
  private boolean isEmbeddedContent(@Nullable IElementType tokenType) {
    if (tokenType == null) {
      return false;
    }
    if (tokenType == KarateTokenTypes.PYSTRING || tokenType == PYSTRING_QUOTES) {
      return true;
    }
    Language lang = tokenType.getLanguage();
    return getJsExtension().isJsLanguage(lang)
      || isJsonLanguage(lang)
      || lang.is(XMLLanguage.INSTANCE);
  }

  private static boolean isJsonLanguage(Language lang) {
    return lang.is(Json5Language.INSTANCE) || lang.is(JsonLanguage.INSTANCE);
  }

  private boolean hadLineBreakBefore(PsiBuilder builder, int prevTokenEnd) {
    if (prevTokenEnd < 0 || prevTokenEnd > builder.getCurrentOffset()) {
      return false;
    }
    return builder.getOriginalText()
      .subSequence(prevTokenEnd, builder.getCurrentOffset())
      .toString()
      .contains("\n");
  }

  private int getTokenLength(@Nullable final String tokenText) {
    return tokenText != null ? tokenText.length() : 0;
  }
}
