/*
 * The MIT License
 *
 * Copyright 2024 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.js;

public enum Token {

    _NODE,
    WS_LF(false),
    WS(false),
    BACKTICK,
    L_CURLY,
    R_CURLY,
    L_BRACKET,
    R_BRACKET,
    L_PAREN,
    R_PAREN,
    COMMA,
    COLON,
    SEMI,
    DOT_DOT_DOT,
    DOT,
    //====
    NULL,
    TRUE,
    FALSE,
    FUNCTION,
    RETURN,
    TRY,
    CATCH,
    FINALLY,
    THROW,
    NEW,
    VAR,
    LET,
    CONST,
    IF,
    ELSE,
    TYPEOF,
    INSTANCEOF,
    DELETE,
    FOR,
    IN,
    OF,
    DO,
    WHILE,
    SWITCH,
    CASE,
    DEFAULT,
    BREAK,
    //====
    EQ_EQ_EQ,
    EQ_EQ,
    EQ,
    EQ_GT, // arrow
    LT_LT_EQ,
    LT_LT,
    LT_EQ,
    LT,
    GT_GT_GT_EQ,
    GT_GT_GT,
    GT_GT_EQ,
    GT_GT,
    GT_EQ,
    GT,
    //====
    NOT_EQ_EQ,
    NOT_EQ,
    NOT,
    PIPE_PIPE_EQ,
    PIPE_PIPE,
    PIPE_EQ,
    PIPE,
    AMP_AMP_EQ,
    AMP_AMP,
    AMP_EQ,
    AMP,
    CARET_EQ,
    CARET,
    QUES_QUES,
    QUES,
    //====
    PLUS_PLUS,
    PLUS_EQ,
    PLUS,
    MINUS_MINUS,
    MINUS_EQ,
    MINUS,
    STAR_STAR_EQ,
    STAR_STAR,
    STAR_EQ,
    STAR,
    SLASH_EQ,
    SLASH,
    PERCENT_EQ,
    PERCENT,
    TILDE,
    //====
    L_COMMENT(false),
    B_COMMENT(false),
    S_STRING,
    D_STRING,
    NUMBER,
    IDENT,
    //====
    DOLLAR_L_CURLY,
    T_STRING;

    public final boolean primary;

    Token() {
        primary = true;
    }

    Token(boolean primary) {
        this.primary = primary;
    }

}
