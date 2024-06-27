// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be 
// found in the LICENSE file.
package com.rankweis.uppercut.karate.psi;

import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.DECLARATION;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.SCENARIOS_KEYWORDS;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.TEXT;
import static com.rankweis.uppercut.karate.psi.KarateTokenTypes.VARIABLE;

import com.intellij.lexer.LexerBase;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class GherkinLexer extends LexerBase {

  protected CharSequence myBuffer = Strings.EMPTY_CHAR_SEQUENCE;
  protected int myStartOffset = 0;
  protected int myEndOffset = 0;
  private int myPosition;
  private IElementType myCurrentToken;
  private int myCurrentTokenStart;
  private List<String> myKeywords;
  private List<String> myActionKeywords;
  private int myState;

  private final static int STATE_DEFAULT = 0;
  private final static int STATE_AFTER_KEYWORD = 1;
  private final static int STATE_TABLE = 2;
  private final static int STATE_AFTER_STEP_KEYWORD = 3;
  private final static int STATE_AFTER_SCENARIO_KEYWORD = 4;
  private final static int STATE_INSIDE_PYSTRING = 5;
  private final static int STATE_AFTER_ACTION_KEYWORD = 6;

  private final static int STATE_PARAMETER_INSIDE_PYSTRING = 7;
  private final static int STATE_PARAMETER_INSIDE_STEP = 8;

  private static final int STATE_QUOTE_INSIDE_PYSTRING = 10;
  private static final int STATE_QUOTE_INSIDE_STEP = 11;

  public static final String PYSTRING_MARKER = "\"\"\"";
  public static final List<String> INTERESTING_SYMBOLS = List.of("\n", "'", "\"", "#", "{", "[", "function");
  private final GherkinKeywordProvider myKeywordProvider;
  private String myCurLanguage;

  public GherkinLexer(GherkinKeywordProvider provider) {
    myKeywordProvider = provider;
    updateLanguage("en");
  }

  private void updateLanguage(String language) {
    myCurLanguage = language;
    myKeywords = new ArrayList<>(myKeywordProvider.getAllKeywords(language));
    myKeywords.sort((o1, o2) -> o2.length() - o1.length());
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myPosition = startOffset;
    myState = initialState;
    advance();
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

  private boolean advanceIfQuoted() {
    char startingQuote = myBuffer.charAt(myPosition);
    int pos = myPosition + 1;

    while (pos < myEndOffset && myBuffer.charAt(pos) != '\n' && myBuffer.charAt(pos) != startingQuote) {
      pos++;
    }

    boolean isQuotedStr = pos < myEndOffset && myBuffer.charAt(pos) == startingQuote;
    if (isQuotedStr) {
      myPosition = pos + 1;
      if (myPosition > myEndOffset) {
        myPosition = myEndOffset;
      }
    }
    return isQuotedStr;
  }
  
  private boolean advanceIfJsJson() {
    char openingBrace = myBuffer.charAt(myPosition);
    char closingBrace = myBuffer.charAt(myPosition) == '{' ? '}' : ']';
    int pos = myPosition + 1;
    int closingBracesRequired = 1;

    while (pos < myEndOffset && myBuffer.charAt(pos) != '\n') {
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

    boolean isJsJson =
      pos < myEndOffset && myBuffer.charAt(pos) == closingBrace && 
        !myBuffer.subSequence(myPosition, pos).toString().matches("[\\[{*\\d]*");
    if (isJsJson) {
      myPosition = pos + 1;
      if (myPosition > myEndOffset) {
        myPosition = myEndOffset;
      }
    }
    return isJsJson;
  }
  
  private boolean advanceIfDeclaration() {
    int startingPos = myPosition;
    int pos = myPosition + 1;

    while (pos < myEndOffset && myBuffer.charAt(pos) != '\n' && myBuffer.charAt(pos) != '=') {
      pos++;
    }

    boolean positionOkay = pos < (myEndOffset - 1) && myBuffer.charAt(pos) == '=';
    // Look for 'declaration =' but not 'declaration =='
    boolean isDeclaration = positionOkay && myBuffer.charAt(pos + 1) != '='
      && myBuffer.charAt(pos - 1) != '!';
    boolean isVariable =
      !isDeclaration && positionOkay && (myBuffer.charAt(pos + 1) == '=' || myBuffer.charAt(pos - 1) == '!');
    if (isDeclaration || isVariable) {
      myPosition = pos > myPosition ? pos - 1 : pos;
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

  @Override
  public void advance() {
    if (myPosition >= myEndOffset) {
      myCurrentToken = null;
      return;
    }
    myCurrentTokenStart = myPosition;
    char c = myBuffer.charAt(myPosition);

    if (myState == STATE_AFTER_SCENARIO_KEYWORD) {
      myCurrentToken = TEXT;
      advanceToNextLine();
      myState = STATE_DEFAULT;
    } if (isStringAtPosition(PYSTRING_MARKER)) {
      injectPyString();
    } else if (Character.isWhitespace(c)) {
      advanceOverWhitespace();
      myCurrentToken = TokenType.WHITE_SPACE;
      while (myPosition < myEndOffset && Character.isWhitespace(myBuffer.charAt(myPosition))) {
        advanceOverWhitespace();
      }
    } else if ( (c == '{' || c == '[') && myState != STATE_TABLE) {
      if (advanceIfJsJson()) {
        myCurrentToken = KarateTokenTypes.PYSTRING;
      } else {
        advanceToNextInterestingToken();
      }
    } else if ( isStringAtPosition("function")) {
      myCurrentToken = KarateTokenTypes.PYSTRING;
      advanceToNextLine();
    } else if (isQuote(c)) {
      myCurrentToken = KarateTokenTypes.QUOTE;
      if (!advanceIfQuoted() && myPosition < myEndOffset) {
        advanceToNextLine();
      }
    } else if (c == '|' && myState != STATE_INSIDE_PYSTRING) {
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
    } else if (c == '#') {
      myCurrentToken = KarateTokenTypes.COMMENT;
      advanceToNextLine();

      String commentText = myBuffer.subSequence(myCurrentTokenStart + 1, myPosition).toString().trim();
      final String language = fetchLocationLanguage(commentText);
      if (language != null) {
        updateLanguage(language);
      }
    } else if (c == ':' && myState != STATE_AFTER_STEP_KEYWORD) {
      myCurrentToken = KarateTokenTypes.COLON;
      myPosition++;
    } else if (c == '@') {
      myCurrentToken = KarateTokenTypes.TAG;
      myPosition++;
      while (myPosition < myEndOffset && isValidTagChar(myBuffer.charAt(myPosition))) {
        myPosition++;
      }
    } else {
      if (myState == STATE_DEFAULT) {
        for (String keyword : myKeywords) {
          int length = keyword.length();
          if (isStringAtPosition(keyword)) {
            if (myKeywordProvider.isSpaceRequiredAfterKeyword(myCurLanguage, keyword) &&
              myEndOffset - myPosition > length &&
              Character.isLetterOrDigit(myBuffer.charAt(myPosition + length))) {
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
            } else if (SCENARIOS_KEYWORDS.contains(myCurrentToken)) {
              myState = STATE_AFTER_SCENARIO_KEYWORD;
            } else {
              myState = STATE_AFTER_KEYWORD;
            }

            return;
          }
        }
      }
      if (myState == STATE_AFTER_STEP_KEYWORD) {
        for (String keyword : myKeywords) {
          if (myKeywordProvider.isActionKeyword(keyword) && isStringAtPosition(keyword)) {
            myState = STATE_AFTER_ACTION_KEYWORD;
            myCurrentToken = KarateTokenTypes.ACTION_KEYWORD;
            myPosition += keyword.length();
            return;
          }
        }
      }
      if (myState == STATE_PARAMETER_INSIDE_STEP) {
        if (c == '>') {
          myState = STATE_AFTER_ACTION_KEYWORD;
          myPosition++;
          myCurrentToken = KarateTokenTypes.STEP_PARAMETER_BRACE;
        } else {
          advanceToParameterEnd("\n");
          myCurrentToken = KarateTokenTypes.STEP_PARAMETER_TEXT;
        }
        return;
      } else if (myState == STATE_AFTER_ACTION_KEYWORD) {
        if (Character.isAlphabetic(myBuffer.charAt(myPosition)) && advanceIfDeclaration()) {
          myState = STATE_DEFAULT;
          return;
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

  private void injectPyString() {
    myPosition += PYSTRING_MARKER.length();
    while (myPosition < myEndOffset && !isStringAtPosition(PYSTRING_MARKER)) {
      myPosition++;
    }
    myPosition += PYSTRING_MARKER.length();
    myCurrentToken = KarateTokenTypes.PYSTRING;
    if (myPosition >= myEndOffset && !isStringAtPosition(PYSTRING_MARKER, myPosition - 3)) {
      myState = STATE_INSIDE_PYSTRING;
      myPosition = myEndOffset;
      myCurrentToken = KarateTokenTypes.PYSTRING_INCOMPLETE;
    }
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

  private String stringHelper(int offset) {
    return myBuffer.subSequence(myPosition, offset).toString();
  }

  private boolean isStringAtPosition(String keyword, int position) {
    int length = keyword.length();
    return myEndOffset - position >= length && myBuffer.subSequence(position, position + length).toString()
      .equals(keyword);
  }

  private static boolean isValidTagChar(char c) {
    return !Character.isWhitespace(c) && c != '@';
  }

  private void advanceToNextInterestingToken() {
    myPosition++;
    int mark = myPosition;
    while (myPosition < myEndOffset && INTERESTING_SYMBOLS.stream()
      .noneMatch(this::isStringAtPosition)) {
      myPosition++;
    }
    returnWhitespace(mark);
    myState = STATE_DEFAULT;
  }

  private void returnWhitespace(int mark) {
    while (myPosition > mark && Character.isWhitespace(myBuffer.charAt(myPosition - 1))) {
      myPosition--;
    }
  }

  private void advanceToParameterOrSymbol(String s, int parameterState, boolean shouldReturnWhitespace) {
    int mark = myPosition;

    while (myPosition < myEndOffset && !isStringAtPosition(s) && !isStepParameter(s)) {
      if (INTERESTING_SYMBOLS.contains(myBuffer.charAt(myPosition))) {
        return;
      }
      myPosition++;
    }

    if (shouldReturnWhitespace) {
      myState = STATE_DEFAULT;
      if (myPosition < myEndOffset) {
        if (!isStringAtPosition(s)) {
          myState = parameterState;
        }
      }

      returnWhitespace(mark);
    }
  }

  private int findOffset(Character match, Character... terminate) {
    int mark = myPosition;
    while (mark < myEndOffset) {
      char c = myBuffer.charAt(mark);
      if (c == match) {
        return mark - myPosition;
      } else if (Arrays.stream(terminate).anyMatch(ch -> ch == c)) {
        return -1;
      }
      mark++;
    }
    return -1;
  }

  private boolean isQuote(char c) {
    return c == '\'' || c == '"';
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
  
  private void advanceToNextLine() {
    myPosition++;
    int mark = myPosition;
    while (myPosition < myEndOffset && myBuffer.charAt(myPosition) != '\n') {
      myPosition++;
    }

    if (myPosition < myEndOffset && myBuffer.charAt(myPosition) == '\n') {
      myPosition++;
    }
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
