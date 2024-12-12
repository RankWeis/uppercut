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

import io.karatelabs.js.Token;
import static io.karatelabs.js.Token.*;
import java.util.ArrayDeque;
%%

%public
%class Lexer
%unicode
%type Token

%{
ArrayDeque<Integer> kkStack;
void kkPush() { if (kkStack == null) kkStack = new ArrayDeque<>(); kkStack.push(yystate()); }
int kkPop() { return kkStack.pop(); }
%}

WS = [ \t]
LF = \R

DIGIT = [0-9]
HEX_DIGIT = [0-9a-fA-F]
EXPONENT = [Ee] ["+""-"]? {DIGIT}*
NUMBER = ({DIGIT}+ ("." {DIGIT}+)? {EXPONENT}?) | ("." {DIGIT}+ {EXPONENT}?)
HEX = 0 [Xx] {HEX_DIGIT}*

L_COMMENT = "//".*
B_COMMENT = "/"\*([^*]|\*+[^*/])*(\*+"/")?
D_STRING = \"[^\"]*\"?
S_STRING = '[^']*'?
IDENT = [:jletter:][:jletterdigit:]*
T_STRING = [^`$]+ ("$"[^{])?

%state TEMPLATE PLACEHOLDER

%%

<YYINITIAL, PLACEHOLDER> {
  {WS}* {LF} {WS}*              { return WS_LF; }
  {WS}+                         { return WS; }
  "`"                           { kkPush(); yybegin(TEMPLATE); return BACKTICK; }
  "{"                           { return L_CURLY; }
  //====                        { return R_CURLY; }
  "["                           { return L_BRACKET; }
  "]"                           { return R_BRACKET; }
  "("                           { return L_PAREN; }
  ")"                           { return R_PAREN; }
  ","                           { return COMMA; }
  ":"                           { return COLON; }
  ";"                           { return SEMI; }
  "..."                         { return DOT_DOT_DOT; }
  "."                           { return DOT; }
  //====
  "null"                        { return NULL; }
  "true"                        { return TRUE; }
  "false"                       { return FALSE; }
  "function"                    { return FUNCTION; }
  "return"                      { return RETURN; }
  "try"                         { return TRY; }
  "catch"                       { return CATCH; }
  "finally"                     { return FINALLY; }
  "throw"                       { return THROW; }
  "new"                         { return NEW; }
  "var"                         { return VAR; }
  "let"                         { return LET; }
  "const"                       { return CONST; }
  "if"                          { return IF; }
  "else"                        { return ELSE; }
  "typeof"                      { return TYPEOF; }
  "instanceof"                  { return INSTANCEOF; }
  "delete"                      { return DELETE; }
  "for"                         { return FOR; }
  "in"                          { return IN; }
  "of"                          { return OF; }
  "do"                          { return DO; }
  "while"                       { return WHILE; }
  "switch"                      { return SWITCH; }
  "case"                        { return CASE; }
  "default"                     { return DEFAULT; }
  "break"                       { return BREAK; }
  //====
  "==="                         { return EQ_EQ_EQ; }
  "=="                          { return EQ_EQ; }
  "="                           { return EQ; }
  "=>"                          { return EQ_GT; }
  "<<="                         { return LT_LT_EQ; }
  "<<"                          { return LT_LT; }
  "<="                          { return LT_EQ; }
  "<"                           { return LT; }
  ">>>="                        { return GT_GT_GT_EQ; }
  ">>>"                         { return GT_GT_GT; }
  ">>="                         { return GT_GT_EQ; }
  ">>"                          { return GT_GT; }
  ">="                          { return GT_EQ; }
  ">"                           { return GT; }
  //====
  "!=="                         { return NOT_EQ_EQ; }
  "!="                          { return NOT_EQ; }
  "!"                           { return NOT; }
  "||="                         { return PIPE_PIPE_EQ; }
  "||"                          { return PIPE_PIPE; }
  "|="                          { return PIPE_EQ; }
  "|"                           { return PIPE; }
  "&&="                         { return AMP_AMP_EQ; }
  "&&"                          { return AMP_AMP; }
  "&="                          { return AMP_EQ; }
  "&"                           { return AMP; }
  "^="                          { return CARET_EQ; }
  "^"                           { return CARET; }
  "??"                          { return QUES_QUES; }
  "?"                           { return QUES; }
  //====
  "++"                          { return PLUS_PLUS; }
  "+="                          { return PLUS_EQ; }
  "+"                           { return PLUS; }
  "--"                          { return MINUS_MINUS; }
  "-="                          { return MINUS_EQ; }
  "-"                           { return MINUS; }
  "**="                         { return STAR_STAR_EQ; }
  "**"                          { return STAR_STAR; }
  "*="                          { return STAR_EQ; }
  "*"                           { return STAR; }
  "/="                          { return SLASH_EQ; }
  "/"                           { return SLASH; }
  "%="                          { return PERCENT_EQ; }
  "%"                           { return PERCENT; }
  "~"                           { return TILDE; }
  //====
  {L_COMMENT}                   { return L_COMMENT; }
  {B_COMMENT}                   { return B_COMMENT; }
  {S_STRING}                    { return S_STRING; }
  {D_STRING}                    { return D_STRING; }
  {NUMBER} | {HEX}              { return NUMBER; }
  {IDENT}                       { return IDENT; }
}

<YYINITIAL> "}"                 { return R_CURLY; }

<PLACEHOLDER> "}"               { yybegin(kkPop()); return R_CURLY; }

<TEMPLATE> {
    "`"                         { yybegin(kkPop()); return BACKTICK; }
    "${"                        { kkPush(); yybegin(PLACEHOLDER); return DOLLAR_L_CURLY; }
    {T_STRING}                  { return T_STRING; }
}

  /* error fallback */
  [^]                           { throw new Error("Illegal character <"+
                                                          yytext()+">"); }
