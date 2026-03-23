// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be 
// found in the LICENSE file.

package com.rankweis.uppercut.karate.lexer;

import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.CLOSE_PAREN;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.DECLARATION;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.DOUBLE_QUOTED_STRING;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.JSON_INJECTABLE;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.OPEN_PAREN;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.OPERATOR;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.PYSTRING_INCOMPLETE;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.SCENARIOS_KEYWORDS;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.SINGLE_QUOTED_STRING;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.TEXT;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.VARIABLE;

import com.intellij.json.json5.Json5Lexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.lexer.LexerPosition;
import com.intellij.lexer.XmlLexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.rankweis.uppercut.karate.lexer.impl.KarateJavascriptExtension;
import com.rankweis.uppercut.karate.psi.GherkinKeywordProvider;
import com.rankweis.uppercut.karate.psi.KarateTokenTypes;
import com.rankweis.uppercut.settings.KarateSettingsState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;


/**
 * Lexer for Karate feature files. Extends the base Gherkin lexer with support for
 * embedded JavaScript, JSON, and XML via language injection into sub-lexers.
 *
 * <h3>State Machine</h3>
 * <p>The lexer uses a state machine (myState) with two kinds of states:</p>
 * <ul>
 *   <li><b>Core states (0-10)</b>: Track position within Gherkin structure
 *       (e.g., after a step keyword, inside a table, inside a pystring)</li>
 *   <li><b>Injection states (1000+)</b>: Indicate delegation to a sub-lexer.
 *       The sub-lexer's own state is added as an offset within the range:
 *       <ul>
 *         <li>1000-1999 = JavaScript injection (via KarateJs or IntelliJ JS plugin)</li>
 *         <li>2000-2999 = JSON injection (via Json5Lexer)</li>
 *         <li>3000-3999 = XML injection (via XmlLexer)</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>Token Flow in advance()</h3>
 * <p>Each call to {@link #advance()} processes one token. Priority order:</p>
 * <ol>
 *   <li>Pystring markers (""")</li>
 *   <li>Active sub-lexer delegation (JS, JSON, XML)</li>
 *   <li>Pystring content detection and injection</li>
 *   <li>Whitespace</li>
 *   <li>Scenario/feature title text</li>
 *   <li>JSON/array literals ({/[) — only injected when preceded by whitespace or '('</li>
 *   <li>Table pipes (|)</li>
 *   <li>Step parameters ({@code <param>})</li>
 *   <li>Function definitions (triggers JS injection)</li>
 *   <li>Quoted strings ('...' and "...")</li>
 *   <li>Comments (#)</li>
 *   <li>Tags (@tag) — only recognized at line start</li>
 *   <li>Operators (==, !=, =, etc.)</li>
 *   <li>Gherkin keywords (Feature, Scenario, Given, When, Then, etc.)</li>
 *   <li>Action keywords (def, match, set, etc.) and declarations</li>
 *   <li>Plain text fallback</li>
 * </ol>
 */
public class LegacyUppercutLexer extends LexerBase {

  protected CharSequence myBuffer = Strings.EMPTY_CHAR_SEQUENCE;
  protected int myStartOffset = 0;
  protected int myEndOffset = 0;
  private int myPosition;
  private IElementType myCurrentToken;
  private int myCurrentTokenStart;
  private List<String> myKeywords;
  private int myState;

  // Core lexer states — track position within Gherkin structure
  private static final int STATE_DEFAULT = 0;
  private static final int STATE_TABLE = 2;
  private static final int STATE_AFTER_STEP_KEYWORD = 3;
  private static final int STATE_AFTER_SCENARIO_KEYWORD = 4;
  private static final int STATE_INSIDE_PYSTRING = 5;
  private static final int STATE_AFTER_ACTION_KEYWORD = 6;

  private static final int STATE_PARAMETER_INSIDE_PYSTRING = 7;
  private static final int STATE_PARAMETER_INSIDE_STEP = 8;
  private static final int STATE_AFTER_FEATURE_KEYWORD = 9;
  private static final int STATE_AFTER_OPERATOR = 10;

  // Injection state ranges — sub-lexer state is stored as offset within range.
  // e.g., INJECTING_JSON + jsonLexer.getState() gives a unique composite state.
  private static final int INJECTING_JAVASCRIPT = 1000;
  private static final int INJECTING_JSON = 2000;
  private static final int INJECTING_XML = 3000;

  public static final String PYSTRING_MARKER = "\"\"\"";

  // Tokens that cause advanceToNextInterestingToken() to stop consuming TEXT.
  // These are characters/strings that need special handling by advance().
  public static final List<String> INTERESTING_SYMBOLS =
    List.of("\n", "'", "\"", "#", "{", "[", "function", " ", "(", ")");

  // Karate marker expressions valid after '#' inside JSON values (e.g., "#null", "#regex").
  public static final List<String> INJECTABLE_STRINGS =
    List.of("ignore", "null", "notnull", "present", "notpresent", "array", "object", "boolean", "number", "string",
      "uuid", "regex", "?");

  // Action keywords whose docstring content should be treated as plain text,
  // not injected as JS/JSON/XML. These keywords produce raw string values in Karate.
  private static final Set<String> PLAIN_TEXT_KEYWORDS =
    Set.of("text", "csv", "yaml", "bytes", "doc");

  private final GherkinKeywordProvider myKeywordProvider;
  // Tracks the most recent action keyword (e.g., "def", "match", "text") on the current step.
  // Used by injectPyString() to decide whether docstring content is plain text or code.
  // Reset to null when a new step keyword is encountered.
  private String lastActionKeyword;
  List<String> scenarioKeywords = Stream.of("Scenario", "Background").toList();
  List<String> stepKeywords;
  // Keywords that, if found at the beginning of a line inside a multi-line construct,
  // signal that the construct has been "interrupted" (i.e., a new step/scenario started).
  List<String> interruptions;


  private String myCurLanguage;

  // Sub-lexers for language injection. JS lexer is initialized once at construction;
  // JSON and XML lexers are created on-demand per injection region.
  private final Lexer jsLexer;
  Lexer jsonLexer = null;
  Lexer xmlLexer = null;

  public LegacyUppercutLexer(GherkinKeywordProvider provider) {
    this(provider, false);
  }

  public LegacyUppercutLexer(GherkinKeywordProvider provider, boolean highlighting) {
    myKeywordProvider = provider;
    boolean useInternalEngine = KarateSettingsState.getInstance().isUseKarateJavaScriptEngine();
    if (useInternalEngine) {
      this.jsLexer =
        KarateJavascriptExtension.EP_NAME.getExtensionList().stream().toList().getLast().getLexer(highlighting);
    } else {
      this.jsLexer =
        KarateJavascriptExtension.EP_NAME.getExtensionList().stream().findFirst().map(l -> l.getLexer(highlighting))
          .orElse(null);
    }
    updateLanguage("en");
    stepKeywords = myKeywords.stream().filter(myKeywordProvider::isStepKeyword).toList();
    interruptions = new ArrayList<>();
    interruptions.addAll(stepKeywords);
    interruptions.addAll(scenarioKeywords);
  }

  private void updateLanguage(String language) {
    myCurLanguage = language;
    myKeywords = new ArrayList<>(myKeywordProvider.getAllKeywords(language));
    myKeywords.sort((o1, o2) -> o2.length() - o1.length());
  }

  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState, boolean advance) {
    myBuffer = buffer;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myPosition = startOffset;
    myState = initialState;

    // setup context
    if (myPosition != 0 && myState == STATE_DEFAULT) {
      String prev = myBuffer.subSequence(0, myPosition).toString();
      int pystringOccurrences = prev.split(PYSTRING_MARKER + "\n").length - 1;
      if (prev.endsWith(PYSTRING_MARKER)) {
        pystringOccurrences++;
      }
      if (pystringOccurrences % 2 == 1) {
        // currently in pystring markers
        myPosition = prev.lastIndexOf(PYSTRING_MARKER + "\n") + PYSTRING_MARKER.length() + 1;
        myState = STATE_INSIDE_PYSTRING;
      }
    }
    if (advance) {
      advance();
    }
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    start(buffer, startOffset, endOffset, initialState, true);
  }

  @Override
  public int getState() {
    return myState;
  }

  @Override
  public IElementType getTokenType() {
    return myCurrentToken;
  }

  @Override
  public int getTokenStart() {
    return myCurrentTokenStart;
  }

  @Override
  public int getTokenEnd() {
    return myPosition;
  }

  private boolean isStepParameter(@NotNull final String currentElementTerminator) {
    int pos = myPosition;

    if (myBuffer.charAt(pos) == '<') {
      while (pos < myEndOffset && myBuffer.charAt(pos) != '\n' && myBuffer.charAt(pos) != '>' && !isStringAtPosition(
        currentElementTerminator, pos)) {
        pos++;
      }

      return pos < myEndOffset && myBuffer.charAt(pos) == '>';
    }

    return false;
  }

  private boolean advanceIfQuoted(char quoteChar) {
    int pos = myPosition + 1;

    while (pos < myEndOffset && myBuffer.charAt(pos) != '\n' && myBuffer.charAt(pos) != quoteChar) {
      pos++;
    }

    boolean isQuotedStr = pos < myEndOffset && myBuffer.charAt(pos) == quoteChar;
    if (isQuotedStr) {
      myPosition = pos + 1;
      if (myPosition > myEndOffset) {
        myPosition = myEndOffset;
      }
    }
    return isQuotedStr;
  }

  /**
   * Attempts to lex the current '{' or '[' as the start of a JSON/JS object or array.
   * Finds the matching closing brace/bracket (handling nesting), checks that the content
   * isn't trivially non-JSON (e.g., just digits like array indices), and if valid,
   * starts injection for the matched region.
   *
   * <p>Uses {@link #isStringInterrupted} to detect if a new step/scenario keyword appears
   * at a line start within the braces, which would indicate broken structure rather than JSON.</p>
   *
   * @param preferJs if true, inject as JavaScript (used for ({...}) patterns where
   *                 content is a JS object literal, not strict JSON)
   * @return true if injection was started, false if this isn't a JSON/JS literal
   */
  private boolean advanceIfJsJson(boolean preferJs) {
    char openingBrace = myBuffer.charAt(myPosition);
    char closingBrace = myBuffer.charAt(myPosition) == '{' ? '}' : ']';
    int pos = myPosition + 1;
    int closingBracesRequired = 1;

    while (pos < myEndOffset) {
      int nextPos = isStringInterrupted(interruptions, List.of("" + openingBrace, "" + closingBrace), pos);
      if (nextPos == -1 || nextPos >= myEndOffset) {
        return false;
      }
      pos = nextPos;
      if (myBuffer.charAt(pos) == openingBrace) {
        closingBracesRequired++;
      } else if (myBuffer.charAt(pos) == closingBrace) {
        closingBracesRequired--;
        if (closingBracesRequired == 0) {
          break;
        }
      }
      pos++;
    }

    // Reject trivial matches like "[0]" or "{*}" that are array access / wildcards, not JSON
    boolean isJsJson =
      pos < myEndOffset && myBuffer.charAt(pos) == closingBrace && !myBuffer.subSequence(myPosition, pos).toString()
        .matches("[\\[{*\\d]*");
    if (isJsJson) {
      if (preferJs && jsLexer != null) {
        startInjectJs(myPosition, pos + 1);
      } else {
        startInjectJson(myPosition, pos + 1);
      }
    }
    return isJsJson;
  }

  private boolean advanceIfDeclaration() {
    int startingPos = myPosition;
    int pos = myPosition + 1;

    // Scan forward to find '=' for declaration/variable detection.
    // Also stop at ')' to avoid consuming closing parens into the token —
    // e.g., in "match (expr) ==" the ')' must remain a separate CLOSE_PAREN.
    while (pos < myEndOffset && myBuffer.charAt(pos) != '\n' && myBuffer.charAt(pos) != '='
      && myBuffer.charAt(pos) != ')') {
      pos++;
    }

    boolean positionOkay = pos < (myEndOffset - 1) && myBuffer.charAt(pos) == '=';
    // Look for 'declaration =' but not 'declaration =='
    boolean isDeclaration = positionOkay && myBuffer.charAt(pos + 1) != '=' && myBuffer.charAt(pos - 1) != '!';
    boolean isVariable =
      !isDeclaration && positionOkay && (nextNonSpace(pos + 1) == '=' || prevNonSpace(startingPos, pos - 1) == '!');
    if (isDeclaration || isVariable) {
      myPosition =
        pos > myPosition ? !Character.isLetterOrDigit(prevNonSpace(startingPos, pos - 1)) ? pos - 1 : pos : pos;
      if (myPosition > myEndOffset) {
        myPosition = myEndOffset;
      }
    }
    returnWhitespace(startingPos);
    if (isDeclaration) {
      myCurrentToken = DECLARATION;
    } else if (isVariable) {
      myCurrentToken = VARIABLE;
    }
    return isDeclaration || isVariable;
  }

  private char nextNonSpace(int pos) {
    while (pos < myEndOffset && myBuffer.charAt(pos) == ' ') {
      pos++;
    }
    return myBuffer.charAt(pos);
  }

  private char prevNonSpace(int start, int pos) {
    while (pos > start && myBuffer.charAt(pos) == ' ') {
      pos--;
    }
    return myBuffer.charAt(pos);
  }

  private boolean injecting() {
    return (myState == STATE_INSIDE_PYSTRING) || injectingJson() || injectingJavascript() || injectingXml();
  }

  private boolean injectingJson() {
    return (myState >= INJECTING_JSON) && (myState < (INJECTING_JSON + 1000));
  }

  private boolean injectingJavascript() {
    return (myState >= INJECTING_JAVASCRIPT) && (myState < (INJECTING_JAVASCRIPT + 1000));
  }

  private boolean injectingXml() {
    return myState >= INJECTING_XML && myState < (INJECTING_XML + 1000);
  }

  @Override
  public void advance() {
    if (myPosition >= myEndOffset) {
      myCurrentToken = null;
      return;
    }
    myCurrentTokenStart = myPosition;
    // Handle opening and closing markers """
    if (isStringAtPosition(PYSTRING_MARKER)) {
      myCurrentToken = KarateTokenTypes.PYSTRING_QUOTES;
      myPosition += PYSTRING_MARKER.length();
      if (injecting()) {
        myState = STATE_DEFAULT;
      } else {
        if (myPosition >= myEndOffset) {
          myCurrentToken = PYSTRING_INCOMPLETE;
          return;
        }
        int pystringInterrupted = isStringInterrupted(interruptions, List.of(PYSTRING_MARKER));
        if (pystringInterrupted == -1) {
          myCurrentToken = PYSTRING_INCOMPLETE;
          return;
        }
        myState = STATE_INSIDE_PYSTRING;
      }
      return;
    }
    if (injectingJavascript()) {
      if (myPosition >= jsLexer.getBufferEnd()) {
        myState = STATE_DEFAULT;
      } else {
        injectJs();
        return;
      }
    }
    if (injectingJson()) {
      if (myPosition >= jsonLexer.getBufferEnd()) {
        myState = STATE_DEFAULT;
      } else {
        injectJson();
        return;
      }
    }
    if (injectingXml()) {
      if (myPosition >= xmlLexer.getBufferEnd()) {
        myState = STATE_DEFAULT;
      } else {
        injectXml();
        return;
      }
    }
    if (myState == STATE_INSIDE_PYSTRING) {
      injectPyString();
      return;
    }
    char c = myBuffer.charAt(myPosition);
    if (myState == STATE_AFTER_OPERATOR) {
      if (c == '<') {
        Matcher matcher =
          Pattern.compile("<[^<>]+>[^<]*").matcher(myBuffer.subSequence(myPosition, getPositionOfNextLine()));
        if (!matcher.matches()) {
          // Probably is attempting xml
          startInjectXml(myPosition, getPositionOfNextLine());
          return;
        }
      }
      if (!Character.isWhitespace(c)) {
        myState = STATE_DEFAULT;
      }
    }
    if (Character.isWhitespace(c)) {
      advanceOverWhitespace();
      myCurrentToken = TokenType.WHITE_SPACE;
      while (myPosition < myEndOffset && Character.isWhitespace(myBuffer.charAt(myPosition))) {
        advanceOverWhitespace();
      }
    } else if (myState == STATE_AFTER_SCENARIO_KEYWORD || myState == STATE_AFTER_FEATURE_KEYWORD) {
      myCurrentToken = TEXT;
      advanceToNextLine(false);
      myState = STATE_DEFAULT;
    } else if (isStringAtPosition(PYSTRING_MARKER)) {
      injectPyString();
    } else if ((c == '{' || c == '[') && myState != STATE_TABLE) {
      // Only attempt JSON injection when '{' or '[' is preceded by whitespace or '('.
      // This prevents false injection for XPath attrs like [@dept='science'],
      // JsonPath filters like $[?(@.field)], and array access like list[0].
      // When preceded by '(' (e.g., ({ key: value })), inject as JavaScript
      // since the content is a JS object literal, not JSON.
      char prevChar = myPosition > 0 ? myBuffer.charAt(myPosition - 1) : ' ';
      boolean precededByWhitespace = myPosition == 0 || Character.isWhitespace(prevChar);
      boolean precededByParen = prevChar == '(';
      if ((precededByWhitespace || precededByParen) && advanceIfJsJson(precededByParen)) {
        return;
      }
      myCurrentToken = TEXT;
      advanceToNextInterestingToken();
    } else if (c == '|' && myState != STATE_INSIDE_PYSTRING
      && (myPosition + 1 >= myEndOffset || myBuffer.charAt(myPosition + 1) != '|')) {
      // Single '|' is a table pipe delimiter. '||' is a logical OR operator
      // (e.g., in Karate's "* if (a || b)") and should be treated as TEXT.
      myCurrentToken = KarateTokenTypes.PIPE;
      myPosition++;
      myState = STATE_TABLE;
    } else if (myState == STATE_PARAMETER_INSIDE_PYSTRING) {
      if (c == '>') {
        myState = STATE_INSIDE_PYSTRING;
        myPosition++;
        myCurrentToken = KarateTokenTypes.STEP_PARAMETER_BRACE;
      } else {
        advanceToParameterEnd(PYSTRING_MARKER);
        myCurrentToken = KarateTokenTypes.STEP_PARAMETER_TEXT;
      }
    } else if (myState == STATE_TABLE) {
      myCurrentToken = KarateTokenTypes.TABLE_CELL;
      while (myPosition < myEndOffset) {
        // Cucumber: 0.7.3 Table cells can now contain escaped bars - \| and escaped backslashes - \\
        if (myBuffer.charAt(myPosition) == '\\') {
          final int nextPos = myPosition + 1;
          if (nextPos < myEndOffset) {
            final char nextChar = myBuffer.charAt(nextPos);
            if (nextChar == '|' || nextChar == '\\') {
              myPosition += 2;
              continue;
            }
            // else - common case
          }
        } else if (myBuffer.charAt(myPosition) == '|' || myBuffer.charAt(myPosition) == '\n') {
          break;
        }
        myPosition++;
      }
      while (myPosition > 0 && Character.isWhitespace(myBuffer.charAt(myPosition - 1))) {
        myPosition--;
      }
    } else if (isStringAtPosition("function") && containsCharEarlierInLine('=') && jsLexer != null) {
      int endOfFunction = findNextMatchingClosingBrace();
      if (endOfFunction < 0) {
        endOfFunction = getPositionOfNextLine();
      }
      startInjectJs(myPosition, Math.min(endOfFunction + 1, myEndOffset));
    } else if (c == '\'') {
      myCurrentToken = SINGLE_QUOTED_STRING;
      if (!advanceIfQuoted('\'') && myPosition < myEndOffset) {
        advanceToNextLine();
      }
    } else if (c == '"') {
      myCurrentToken = DOUBLE_QUOTED_STRING;
      if (!advanceIfQuoted('"') && myPosition < myEndOffset) {
        advanceToNextLine();
      }
    } else if (c == '#') {
      myCurrentToken = KarateTokenTypes.COMMENT;
      advanceToNextLine(false);

      String commentText = myBuffer.subSequence(myCurrentTokenStart + 1, myPosition).toString().trim();
      final String language = fetchLocationLanguage(commentText);
      if (language != null) {
        updateLanguage(language);
      }
    } else if (c == ':' && myState != STATE_AFTER_STEP_KEYWORD) {
      myCurrentToken = KarateTokenTypes.COLON;
      myPosition++;
    } else if (c == '@' && isAtLineStart()) {
      // Tags (@tag) are only valid at the start of a line.
      // Without this check, '@' inside expressions like JsonPath $[?(@.field)]
      // would be consumed as a TAG token, eating subsequent characters.
      myCurrentToken = KarateTokenTypes.TAG;
      do {
        myPosition++;
      } while (myPosition < myEndOffset && isValidTagChar(myBuffer.charAt(myPosition)));
    } else if (isStringAtPosition("==") || isStringAtPosition("!=") || isStringAtPosition("<=") || isStringAtPosition(
      ">=")) {
      myPosition += 2;
      myCurrentToken = OPERATOR;
      myState = STATE_AFTER_OPERATOR;
    } else if (c == '=' || c == '<' || c == '>') {
      if (c == '<' && getStringUntilNextInterestingToken().trim().matches("<\\w+>")) {
        myCurrentToken = TEXT;
        advanceToNextInterestingToken();
      } else {
        myPosition++;
        myCurrentToken = OPERATOR;
        myState = STATE_AFTER_OPERATOR;
      }
    } else if (c == '(' || c == ')') {
      myPosition++;
      myCurrentToken = c == '(' ? OPEN_PAREN : CLOSE_PAREN;
    } else {
      if (myState == STATE_DEFAULT) {
        if (handleKeywords()) {
          return;
        }
      }
      if (myState == STATE_AFTER_STEP_KEYWORD) {
        for (String keyword : myKeywords) {
          if (myKeywordProvider.isActionKeyword(keyword) && isStringAtPosition(keyword)) {
            if (myKeywordProvider.isSpaceRequiredAfterKeyword(myCurLanguage, keyword) && (
              myPosition + keyword.length() >= myBuffer.length() || !Character.isWhitespace(
                myBuffer.charAt(myPosition + keyword.length())))) {
              continue;
            } else if (myPosition + keyword.length() < myBuffer.length() && Character.isLetter(
              myBuffer.charAt(myPosition + keyword.length()))) {
              continue;
            }
            myState = STATE_AFTER_ACTION_KEYWORD;
            myCurrentToken = KarateTokenTypes.ACTION_KEYWORD;
            lastActionKeyword = keyword;
            myPosition += keyword.length();
            return;
          }
        }
      }
      if (myState == STATE_PARAMETER_INSIDE_STEP) {
        advanceToParameterEnd("\n");
        myCurrentToken = KarateTokenTypes.STEP_PARAMETER_TEXT;
        return;
      } else if (myState == STATE_AFTER_ACTION_KEYWORD) {
        if (Character.isAlphabetic(myBuffer.charAt(myPosition)) && advanceIfDeclaration()) {
          myState = STATE_DEFAULT;
          return;
        } else {
          if (handleKeywords()) {
            return;
          }
        }
      } else if (isParameterAllowed()) {
        if (myPosition < myEndOffset && myBuffer.charAt(myPosition) == '<' && isStepParameter("\n")) {
          myState = STATE_PARAMETER_INSIDE_STEP;
          myPosition++;
          myCurrentToken = KarateTokenTypes.STEP_PARAMETER_BRACE;
        } else {
          myCurrentToken = KarateTokenTypes.TEXT;
          myState = STATE_DEFAULT;
          advanceToNextInterestingToken();
        }
        return;
      }
      myCurrentToken = KarateTokenTypes.TEXT;
      advanceToNextInterestingToken();
    }
  }

  /**
   * Finds the position of the '}' that matches the '{' at myPosition,
   * handling nested braces. Used to determine the end of a function body
   * for JavaScript injection.
   *
   * <p>Note: This does a simple brace count without skipping string literals,
   * so braces inside strings are counted. This works well enough for typical
   * Karate function bodies but could miscount in edge cases with unmatched
   * braces inside strings.</p>
   *
   * @return position of matching '}', or -1 if not found
   */
  @VisibleForTesting
  int findNextMatchingClosingBrace() {
    int pos = myPosition;
    int closingBracesRequired = 0;
    while (pos < myEndOffset) {
      if (myBuffer.charAt(pos) == '{') {
        closingBracesRequired++;
      } else if (myBuffer.charAt(pos) == '}') {
        closingBracesRequired--;
        if (closingBracesRequired <= 0) {
          break;
        }
      }
      pos++;
    }

    pos = Math.max(Math.min(pos, myEndOffset - 1), 0);
    if (myBuffer.charAt(pos) != '}') {
      return -1;
    } else {
      return pos;
    }
  }

  /**
   * Finds the position of the ')' that matches the '(' at myPosition.
   * Same approach as {@link #findNextMatchingClosingBrace()} but for parentheses.
   * Used by {@link #injectJson()} to find the end of Karate's #(expression) markers.
   *
   * @return position of matching ')', or -1 if not found
   */
  @VisibleForTesting
  int findNextMatchingClosingParen() {
    int pos = myPosition;
    int closingBracesRequired = 0;
    while (pos < myEndOffset) {
      if (myBuffer.charAt(pos) == '(') {
        closingBracesRequired++;
      } else if (myBuffer.charAt(pos) == ')') {
        closingBracesRequired--;
        if (closingBracesRequired <= 0) {
          break;
        }
      }
      pos++;
    }

    pos = Math.max(Math.min(pos, myEndOffset - 1), 0);
    if (myBuffer.charAt(pos) != ')') {
      return -1;
    } else {
      return pos;
    }
  }

  /**
   * Attempts to match the current position against all registered Gherkin keywords
   * (Feature, Scenario, Given, When, Then, And, But, etc.) sorted longest-first.
   * Sets myState based on the keyword type and returns true if a match was found.
   */
  private boolean handleKeywords() {
    for (String keyword : myKeywords) {
      int length = keyword.length();
      if (isStringAtPosition(keyword)) {
        if (myKeywordProvider.isSpaceRequiredAfterKeyword(myCurLanguage, keyword) && myEndOffset - myPosition > length
          && Character.isLetterOrDigit(myBuffer.charAt(myPosition + length))) {
          continue;
        }

        char followedByChar = myPosition + length < myEndOffset ? myBuffer.charAt(myPosition + length) : 0;
        myCurrentToken = myKeywordProvider.getTokenType(myCurLanguage, keyword);
        if (myCurrentToken == KarateTokenTypes.STEP_KEYWORD) {
          boolean followedByWhitespace = Character.isWhitespace(followedByChar) && followedByChar != '\n';
          if (followedByWhitespace != myKeywordProvider.isSpaceRequiredAfterKeyword(myCurLanguage, keyword)) {
            myCurrentToken = KarateTokenTypes.TEXT;
          }
        }
        myPosition += length;
        if (myCurrentToken == KarateTokenTypes.STEP_KEYWORD) {
          myState = STATE_AFTER_STEP_KEYWORD;
          lastActionKeyword = null;
        } else if (myCurrentToken == KarateTokenTypes.FEATURE_KEYWORD) {
          if (myPosition < myEndOffset - 1 && myBuffer.charAt(myPosition) == ':') {
            myPosition++;
          }
          myState = STATE_AFTER_FEATURE_KEYWORD;
        } else if (SCENARIOS_KEYWORDS.contains(myCurrentToken)) {
          if (myPosition < myEndOffset - 1 && myBuffer.charAt(myPosition) == ':') {
            myPosition++;
          }
          myState = STATE_AFTER_SCENARIO_KEYWORD;
        }

        return true;
      }
    }
    return false;
  }

  private void injectPyString() {
    injectPyString(true);
  }

  /**
   * Handles content between pystring markers ("""). Detects the content type by inspecting
   * the first non-whitespace character and delegates to the appropriate sub-lexer:
   * <ul>
   *   <li>'{' or '[' → JSON injection</li>
   *   <li>'<' → XML injection</li>
   *   <li>Everything else → JavaScript injection (if JS lexer available)</li>
   * </ul>
   *
   * <p>Special cases:</p>
   * <ul>
   *   <li>Plain text keywords (text, csv, yaml, bytes, doc) skip injection entirely,
   *       emitting the content as TEXT since Karate treats these as raw strings.</li>
   *   <li>Blank content between markers is emitted as WHITE_SPACE.</li>
   * </ul>
   *
   * @param multiLine true for standard pystrings, false for inline usage
   */
  private void injectPyString(boolean multiLine) {
    int endPos = myPosition;
    while (endPos < myEndOffset && !isStringAtPosition(PYSTRING_MARKER, endPos)) {
      endPos++;
    }
    myCurrentToken = KarateTokenTypes.PYSTRING;
    if (endPos >= myEndOffset) {
      myPosition = myEndOffset;
      myCurrentToken = KarateTokenTypes.PYSTRING_INCOMPLETE;
      return;
    }
    int startPos = myPosition;
    String strippedStr = myBuffer.subSequence(startPos, endPos).toString().strip();
    if (!multiLine && strippedStr.matches("\\[?[\\w+.]*]?")) {
      myCurrentToken = TEXT;
      myState = STATE_DEFAULT;
      advanceToNextLine();
    }
    if (strippedStr.isBlank() && endPos != myPosition) {
      myPosition = endPos;
      myCurrentToken = TokenType.WHITE_SPACE;
      myState = STATE_DEFAULT;
      return;
    }
    // Skip injection for action keywords that produce raw text values in Karate
    if (lastActionKeyword != null && PLAIN_TEXT_KEYWORDS.contains(lastActionKeyword)) {
      myPosition = endPos;
      myCurrentToken = TEXT;
      return;
    }
    if (StringUtil.startsWith(strippedStr, "{") || StringUtil.startsWith(strippedStr, "[")) {
      startInjectJson(startPos, endPos);
    } else if (StringUtil.startsWith(strippedStr, "<")) {
      startInjectXml(startPos, endPos);
    } else if (jsLexer != null) {
      startInjectJs(startPos, endPos);
    } else {
      myPosition = endPos;
      myCurrentToken = TEXT;
    }
  }

  private void startInjectJs(int startPos, int endPos) {
    if (endPos > myEndOffset) {
      endPos = myEndOffset;
    }
    jsLexer.start(myBuffer, startPos, endPos);
    myState = INJECTING_JAVASCRIPT;
    injectJs();
  }

  private void injectJs() {
    try {
      jsLexer.advance();
    } catch (Exception e) {
      myCurrentToken = TokenType.ERROR_ELEMENT;
      myPosition = jsLexer.getBufferEnd();
      return;
    }
    myCurrentToken = jsLexer.getTokenType();
    myPosition = jsLexer.getTokenEnd();
    myState = INJECTING_JAVASCRIPT + jsLexer.getState();
  }

  private void startInjectJson(int startPos, int endPos) {
    if (endPos > myEndOffset) {
      endPos = myEndOffset;
    }
    jsonLexer = new Json5Lexer();
    jsonLexer.start(myBuffer, startPos, endPos);
    myState = INJECTING_JSON + jsonLexer.getState();
    myCurrentToken = jsonLexer.getTokenType();
    myPosition = jsonLexer.getTokenEnd();
  }

  private void startInjectXml(int startPos, int endPos) {
    if (endPos > myEndOffset) {
      endPos = myEndOffset;
    }
    xmlLexer = new XmlLexer();
    xmlLexer.start(myBuffer, startPos, endPos);
    myState = INJECTING_XML + xmlLexer.getState();
    myCurrentToken = xmlLexer.getTokenType();
    myPosition = xmlLexer.getTokenEnd();
  }

  @Override
  public void restore(@NotNull LexerPosition position) {
    super.restore(position);
    if (injecting()) {
      int endPos = myPosition;
      while (endPos < myEndOffset && !isStringAtPosition(PYSTRING_MARKER, endPos)) {
        endPos++;
      }
      endPos = Math.min(endPos, myEndOffset);
      if (injectingJson()) {
        jsonLexer.start(myBuffer, myStartOffset, endPos, jsonLexer.getState() - INJECTING_JSON);
      } else if (injectingJavascript()) {
        jsLexer.start(myBuffer, myStartOffset, endPos, jsLexer.getState() - INJECTING_JAVASCRIPT);
      } else if (injectingXml()) {
        xmlLexer.start(myBuffer, myStartOffset, endPos, xmlLexer.getState() - INJECTING_XML);
      }
    }
  }

  /**
   * Advances one token within a JSON injection region. Before delegating to the JSON sub-lexer,
   * checks for Karate-specific marker expressions that should be treated as a single
   * JSON_INJECTABLE token rather than broken into JSON tokens:
   *
   * <ul>
   *   <li>{@code #(expression)} — embedded Karate expression (e.g., {@code #(response.id)})</li>
   *   <li>{@code "#(expression)"} — same but inside a quoted JSON string value</li>
   *   <li>{@code #keyword} — Karate schema markers like {@code #null}, {@code #string},
   *       {@code #regex}, {@code #notnull}, etc.</li>
   * </ul>
   *
   * <p>When a marker is detected, the entire marker is emitted as one JSON_INJECTABLE token
   * and the JSON sub-lexer is advanced past it to stay in sync.</p>
   */
  private void injectJson() {
    // Handle #(expression) — bare Karate embedded expression
    if (isStringAtPosition("#(")) {
      int nextMatchingClosingParen = findNextMatchingClosingParen();
      nextMatchingClosingParen = nextMatchingClosingParen == -1 ? myEndOffset : nextMatchingClosingParen;
      int closingBrace = Math.min(nextMatchingClosingParen + 1, myEndOffset);
      if (closingBrace > 0 && myBuffer.subSequence(myPosition, closingBrace).toString().trim().matches("#\\(\\S+\\)")) {
        myCurrentToken = JSON_INJECTABLE;
        myPosition = closingBrace;
        while (jsonLexer.getTokenEnd() < closingBrace) {
          jsonLexer.advance();
        }
        return;
      }
    }
    // Handle "#(expression)" — quoted Karate embedded expression
    if (isStringAtPosition("\"#(")) {
      int nextMatchingClosingParen = findNextMatchingClosingParen();
      nextMatchingClosingParen = nextMatchingClosingParen == -1 ? myEndOffset : nextMatchingClosingParen;
      int closingBrace = Math.min(nextMatchingClosingParen + 2, myEndOffset - 1);
      if (closingBrace > 0 && myBuffer.subSequence(myPosition, closingBrace).toString().trim()
        .matches("\"#\\(\\S+\\)\"")) {
        myCurrentToken = JSON_INJECTABLE;
        myPosition = closingBrace;
        while (jsonLexer.getTokenEnd() < closingBrace) {
          jsonLexer.advance();
        }
        return;
      }
    }
    // Handle #keyword — schema markers like #null, #string, #regex, etc.
    if (isStringAtPosition("#")) {
      int injectable = INJECTABLE_STRINGS.stream().filter(s -> isStringAtPosition("#" + s))
        .map(s -> s.length() + 1)
        .findFirst()
        .orElse(0);
      if (injectable > 0) {
        myCurrentToken = JSON_INJECTABLE;
        myPosition += injectable;
        while (jsonLexer.getTokenEnd() < myPosition) {
          jsonLexer.advance();
        }
        return;
      }
    }
    // Default: delegate to the JSON sub-lexer
    jsonLexer.advance();
    myCurrentToken = jsonLexer.getTokenType();
    myPosition = jsonLexer.getTokenEnd();
  }

  private void injectXml() {
    xmlLexer.advance();
    myState = INJECTING_XML + xmlLexer.getState();
    myPosition = xmlLexer.getTokenEnd();
  }

  protected boolean isParameterAllowed() {
    return myState == STATE_AFTER_ACTION_KEYWORD || myState == STATE_AFTER_SCENARIO_KEYWORD;
  }

  @Nullable
  public static String fetchLocationLanguage(final @NotNull String commentText) {
    if (commentText.startsWith("language:")) {
      return commentText.substring(9).trim();
    }
    return null;
  }

  private void advanceOverWhitespace() {
    if (myBuffer.charAt(myPosition) == '\n') {
      myState = STATE_DEFAULT;
    }
    myPosition++;
  }

  private boolean isStringAtPosition(String keyword) {
    int length = keyword.length();
    return myEndOffset - myPosition >= length && myBuffer.subSequence(myPosition, myPosition + length).toString()
      .equals(keyword);
  }

  private boolean isStringAtPosition(String keyword, int position) {
    int length = keyword.length();
    return myEndOffset - position >= length && myBuffer.subSequence(position, position + length).toString()
      .equals(keyword);
  }

  /**
   * Checks whether myPosition is at the start of a line (only whitespace between
   * the current position and the previous newline or start of buffer).
   * Used to guard tag recognition: '@' should only be a TAG at line start,
   * not inside expressions like JsonPath {@code $[?(@.field)]}.
   */
  private boolean isAtLineStart() {
    for (int pos = myPosition - 1; pos >= 0; pos--) {
      char ch = myBuffer.charAt(pos);
      if (ch == '\n') {
        return true;
      }
      if (!Character.isWhitespace(ch)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidTagChar(char c) {
    return !Character.isWhitespace(c) && c != '@';
  }

  /**
   * Advances myPosition past plain text content until hitting a character/string
   * from {@link #INTERESTING_SYMBOLS} that needs special handling by advance().
   * Trailing whitespace is returned (not consumed) so it can be emitted as a
   * separate WHITE_SPACE token.
   */
  private void advanceToNextInterestingToken() {
    myPosition++;
    int mark = myPosition;
    while (myPosition < myEndOffset && INTERESTING_SYMBOLS.stream().noneMatch(this::isStringAtPosition)) {
      myPosition++;
    }
    returnWhitespace(mark);
    myState = STATE_DEFAULT;
  }

  private String getStringUntilNextInterestingToken() {
    int mark = myPosition + 1;
    while (mark < myEndOffset) {
      final int finalMark = mark;
      if (INTERESTING_SYMBOLS.stream().noneMatch(s -> this.isStringAtPosition(s, finalMark))) {
        mark++;
      } else {
        break;
      }
    }
    return myBuffer.subSequence(myPosition, mark).toString();
  }

  /**
   * Backs up myPosition to exclude trailing whitespace from the current token.
   * This ensures whitespace is emitted as separate WHITE_SPACE tokens for proper formatting.
   */
  private void returnWhitespace(int mark) {
    while (myPosition > mark && Character.isWhitespace(myBuffer.charAt(myPosition - 1))) {
      myPosition--;
    }
  }

  private void advanceToParameterEnd(String endSymbol) {
    myPosition++;
    int mark = myPosition;
    while (myPosition < myEndOffset && !isStringAtPosition(endSymbol) && myBuffer.charAt(myPosition) != '>') {
      myPosition++;
    }

    if (myPosition < myEndOffset) {
      if (isStringAtPosition(endSymbol)) {
        myState = STATE_DEFAULT;
      }
    }

    returnWhitespace(mark);
  }

  /**
   * Checks whether a given character appears earlier on the same line (before myPosition).
   * Used to detect if '=' precedes a "function" keyword, which indicates a function
   * assignment (e.g., {@code * def foo = function() {...}}) that should trigger JS injection.
   */
  @VisibleForTesting
  boolean containsCharEarlierInLine(char token) {
    int pos = myPosition;
    while (pos >= 0 && myBuffer.charAt(pos) != '\n') {
      if (myBuffer.charAt(pos) == token) {
        return true;
      }
      pos--;
    }
    return false;
  }

  private void advanceToNextLine() {
    advanceToNextLine(true);
  }

  private void advanceToNextLine(boolean includeNewlines) {
    myPosition++;
    int mark = myPosition;
    while (myPosition < myEndOffset && myBuffer.charAt(myPosition) != '\n') {
      myPosition++;
    }

    if (includeNewlines) {
      if (myPosition < myEndOffset && myBuffer.charAt(myPosition) == '\n') {
        myPosition++;
      }
    } else {
      returnWhitespace(mark);
    }
    if (myPosition > myEndOffset) {
      myPosition = myEndOffset;
    }
  }


  // includingSelf = true means we count your position as a potential return, false means we start at the next
  // character.
  private int getPositionOfNextNonWhitespace(int startPosition, boolean includingSelf) {
    int end = startPosition;
    if (!Character.isWhitespace(myBuffer.charAt(end)) && includingSelf) {
      return end;
    }
    while (end < myEndOffset && Character.isWhitespace(myBuffer.charAt(end))) {
      end++;
    }
    return end;
  }

  private boolean isBeginningOfLine(int startPos) {
    while (startPos > 0 && myBuffer.charAt(startPos) != '\n') {
      if (!Character.isWhitespace(myBuffer.charAt(startPos))) {
        return false;
      }
      startPos--;
    }
    return true;
  }

  /**
   * Scans forward from myPosition looking for either a termination string (e.g., closing
   * brace or pystring marker) or an interruption (step/scenario keyword at line start).
   *
   * <p>Used by {@link #advanceIfJsJson()} to check that a JSON literal isn't broken by
   * a new step keyword, and by advance() to detect incomplete pystrings.</p>
   *
   * @param interruptions keywords that signal a structural break if found at line start
   * @param terminations  strings that signal successful end of the construct
   * @return position of the termination string, or -1 if interrupted or end-of-buffer
   */
  private int isStringInterrupted(List<String> interruptions, List<String> terminations) {
    return isStringInterrupted(interruptions, terminations, myPosition);
  }

  private int isStringInterrupted(List<String> interruptions, List<String> terminations, int myStartPosition) {
    int end = myStartPosition;
    while (end < myEndOffset) {
      end = getPositionOfNextNonWhitespace(end, true);
      int finalEnd = end;
      if (terminations.stream().anyMatch(s -> isStringAtPosition(s, finalEnd))) {
        return end;
      }
      if (interruptions.stream().anyMatch(s -> isStringAtPosition(s, finalEnd))) {
        if (isBeginningOfLine(end - 1)) {
          return -1;
        }
      }
      end++;
    }
    return -1;
  }

  private int getPositionOfNextLine() {
    int endPos = myPosition;
    endPos++;
    int mark = myPosition;
    while (endPos < myEndOffset && myBuffer.charAt(endPos) != '\n') {
      endPos++;
    }

    if (endPos > myEndOffset) {
      endPos = myEndOffset;
    }
    return endPos;
  }

  @Override
  @NotNull
  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getBufferEnd() {
    return myEndOffset;
  }
}

