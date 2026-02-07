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

public enum Type {

    _CHUNK,
    ROOT,
    PROGRAM,
    STATEMENT,
    IF_STMT,
    VAR_STMT,
    VAR_STMT_NAMES,
    RETURN_STMT,
    TRY_STMT,
    THROW_STMT,
    FOR_STMT,
    WHILE_STMT,
    DO_WHILE_STMT,
    SWITCH_STMT,
    CASE_BLOCK,
    DEFAULT_BLOCK,
    BREAK_STMT,
    DELETE_STMT,
    BLOCK,
    EOS,
    EXPR,
    FN_EXPR,
    FN_ARROW_EXPR,
    FN_DECL_ARGS,
    FN_DECL_ARG,
    NEW_EXPR,
    TYPEOF_EXPR,
    INSTANCEOF_EXPR,
    FN_CALL_EXPR,
    FN_CALL_ARGS,
    FN_CALL_ARG,
    ASSIGN_EXPR,
    LOGIC_EXPR,
    LOGIC_AND_EXPR,
    LOGIC_TERN_EXPR,
    LOGIC_BIT_EXPR,
    MATH_ADD_EXPR,
    MATH_MUL_EXPR,
    MATH_EXP_EXPR,
    MATH_POST_EXPR,
    MATH_PRE_EXPR,
    PATH_EXPR,
    REF_EXPR,
    REF_DOT_EXPR,
    REF_BRACKET_EXPR,
    UNARY_EXPR,
    LIT_OBJECT,
    OBJECT_ELEM,
    LIT_ARRAY,
    ARRAY_ELEM,
    LIT_EXPR,
    PAREN_EXPR,
    LIT_TEMPLATE,
    PLACEHOLDER

}
