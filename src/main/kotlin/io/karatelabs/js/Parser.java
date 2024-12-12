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

import static com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter.getElement;
import static io.karatelabs.js.Token.B_COMMENT;
import static io.karatelabs.js.Token.L_COMMENT;
import static io.karatelabs.js.Token.WS;
import static io.karatelabs.js.Token.WS_LF;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import java.io.CharArrayReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Parser {

    static final Logger logger = LoggerFactory.getLogger(Parser.class);

    private final List<Chunk> chunks;
    @Nullable
    private final PsiBuilder builder;
    private int currentOffset;
    private final int size;

    private int position = 0;
    private Marker marker;

    enum Shift {
        NONE, LEFT, RIGHT
    }

    public Parser(Source source) {
        chunks = getChunks(source);
        this.builder = null;
        size = chunks.size();
        marker = new Marker(position, null, new Node(Type.ROOT), -1);
    }

    public Parser(Source source, @Nullable PsiBuilder builder, int currentOffset) {
        chunks = getChunks(source);
        this.builder = builder;
        this.currentOffset = currentOffset;
        size = chunks.size();
        marker = new Marker(position, null, new Node(Type.ROOT), -1);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, position - 7);
        int end = Math.min(position + 7, size);
        for (int i = start; i < end; i++) {
            if (i == 0) {
                sb.append("| ");
            }
            if (i == position) {
                sb.append(">>");
            }
            sb.append(chunks.get(i));
            sb.append(' ');
        }
        if (position == size) {
            sb.append(">>");
        }
        sb.append("|");
        sb.append("\ncurrent node: ");
        sb.append(marker);
        return sb.toString();
    }

    private void error(String message) {
        Chunk chunk;
        if (position == size) {
            chunk = chunks.get(position - 1);
        } else {
            chunk = chunks.get(position);
        }
        String errorMessage = message + "\n"
                + chunk.getPosition()
                + " " + chunk + "\nparser state: " + this;
        throw new EvalError(errorMessage);
    }

    private void error(Type... expected) {
        error("expected: " + Arrays.asList(expected));
    }

    private void error(Token... expected) {
        error("expected: " + Arrays.asList(expected));
    }

    private void enter(Type type) {
        enterIf(type, null);
    }

    private boolean enter(Type type, Token... tokens) {
        return enterIf(type, tokens);
    }

    private boolean enterIf(Type type, Token[] tokens) {
        if (tokens != null) {
            if (!peekAnyOf(tokens)) {
                return false;
            }
        }
        advanceWsComment();
        Node node = new Node(type, builder);
        Marker caller = marker;
        marker = new Marker(position, caller, node, marker.depth + 1);
        if (marker.depth > 100) {
            throw new RuntimeException("too much recursion");
        }
        if (tokens != null) {
            consumeNext();
        }
        return true;
    }

    private boolean exit() {
        return exit(true, false, Shift.NONE);
    }

    private boolean exit(boolean result, boolean mandatory) {
        return exit(result, mandatory, Shift.NONE);
    }

    private void exit(Shift shift) {
        exit(true, false, shift);
    }

    private boolean exit(boolean result, boolean mandatory, Shift shift) {
        if (mandatory && !result) {
            error(marker.node.type);
            marker.node.marker.error("expected: " + marker.node.type);
        }
        if (result) {
            marker.node.done();
            Node parent = marker.caller.node;
            Node node = marker.node;
            switch (shift) {
                case LEFT:
                    Node prev = parent.children.removeFirst(); // remove previous sibling
                    node.children.addFirst(prev); // and make it the first child
                    parent.children.add(node);
                    break;
                case NONE:
                    parent.children.add(node);
                    break;
                case RIGHT:
                    Node prevSibling = parent.children.removeFirst(); // remove previous sibling
                    if (prevSibling.type == node.type) {
                        advanceWsComment();
                        Node newNode = new Node(node.type, builder);
                        parent.children.add(newNode);
                        newNode.children.add(prevSibling.children.get(0)); // prev lhs
                        newNode.children.add(prevSibling.children.get(1)); // operator
                        advanceWsComment();
                        Node newRhs = new Node(node.type, builder);
                        newNode.children.add(newRhs);
                        newRhs.children.add(prevSibling.children.get(2)); // prev rhs becomes current lhs
                        newRhs.children.add(node.children.get(0)); // operator
                        newRhs.children.add(node.children.get(1)); // current rhs
                    } else {
                        node.children.addFirst(prevSibling); // move previous sibling to first child
                        parent.children.add(node);
                    }
            }
        } else {
            position = marker.position;
            marker.node.marker.rollbackTo();
        }
        marker = marker.caller;
        return result;
    }

    private static List<Chunk> getChunks(Source source) {
        CharArrayReader reader = new CharArrayReader(source.text.toCharArray());
        Lexer lexer = new Lexer(reader);
        lexer.reset(source.text, 0, source.text.length(), 0);
        List<Chunk> list = new ArrayList<>();
        Chunk prev = null;
        int line = 0;
        int col = 0;
        try {
            while (true) {
                Token token = lexer.yylex();
                if (token == null) {
                    break;
                }
                String text = lexer.yytext().toString();
                int length = lexer.yylength();
                Chunk chunk = new Chunk(source, token, line, col, text);
                if (token == WS_LF || token == Token.B_COMMENT) {
                    for (int i = 0; i < length; i++) {
                        if (text.charAt(i) == '\n') {
                            col = 0;
                            line++;
                        } else {
                            col++;
                        }
                    }
                } else {
                    col += length;
                }
                chunk.prev = prev;
                if (prev != null) {
                    prev.next = chunk;
                }
                prev = chunk;
                if (token.primary) {
                    list.add(chunk);
                }
            }
        } catch (Throwable e) {
            System.err.println("lexer failed at [" + (line + 1) + ":" + (col + 1) + "] prev: " + prev);
            System.err.println(source.getStringForLog());
            throw new RuntimeException(e);
        }
        return list;
    }

    private boolean peekIf(Token token) {
        if (position == size) {
            return false;
        }
        return chunks.get(position).token == token;
    }

    private void consumeNext() {
        advanceWsComment();
        if (builder != null && builder.getCurrentOffset() < currentOffset) {
            builder.advanceLexer();
        }
        Node node = new Node(chunks.get(position++));
        marker.node.children.add(node);
    }

    private void advanceWsComment() {
        if (builder == null) {
            return;
        }
        while (builder.getCurrentOffset() < currentOffset && builder.getTokenType() == getElement(WS) || builder.getTokenType() == getElement(WS_LF)) {
            builder.advanceLexer();
        }
        advanceComments();
    }

    private void advanceComments() {
        if(builder == null) {
            return;
        }
        while (builder.getTokenType() == getElement(L_COMMENT) || builder.getTokenType() == getElement(B_COMMENT)) {
            IElementType type = builder.getTokenType();
            PsiBuilder.Marker mark = builder.mark();
            builder.advanceLexer();
            mark.done(type);
            while (builder.getCurrentOffset() < currentOffset && builder.getTokenType() == getElement(WS) || builder.getTokenType() == getElement(WS_LF)) {
                builder.advanceLexer();
            }
        }
    }

    private void consume(Token token) {
        if (!consumeIf(token)) {
            error(token);
        }
    }

    private boolean consumeIf(Token token) {
        if (peekIf(token)) {
            advanceWsComment();
            if (builder != null && builder.getCurrentOffset() < currentOffset) {
                builder.advanceLexer();
            }
            Node node = new Node(chunks.get(position++));
            marker.node.children.add(node);
            return true;
        }
        return false;
    }

    private boolean peekAnyOf(Token... tokens) {
        for (Token token : tokens) {
            if (peekIf(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyOf(Token... tokens) {
        for (Token token : tokens) {
            if (consumeIf(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean eos() {
        if (position == size) { // eof
            return true;
        }
        Chunk chunk = chunks.get(position);
        if (chunk.token == Token.R_CURLY) {
            return true;
        }
        if (enter(Type.EOS, Token.SEMI)) {
            return exit();
        }
        return (chunk.prev != null && chunk.prev.token == WS_LF);
    }

    //==================================================================================================================
    //
    public Node parse() {
        advanceWsComment();
        enter(Type.PROGRAM);
        final Node program = marker.node;
        while (true) {
            if (!statement(false)) {
                break;
            }
        }
        if (position != size) {
            error("cannot parse statement");
        }
        exit();
        return program;
    }

    private boolean statement(boolean mandatory) {
        enter(Type.STATEMENT);
        boolean result = if_stmt();
        result = result || (var_stmt() && eos());
        result = result || (return_stmt() && eos());
        result = result || (throw_stmt() && eos());
        result = result || try_stmt();
        result = result || for_stmt();
        result = result || while_stmt();
        result = result || switch_stmt();
        result = result || (break_stmt() && eos());
        result = result || (delete_stmt() && eos());
        result = result || (expr(-1, false) && eos());
        result = result || block(false);
        result = result || consumeIf(Token.SEMI); // empty statement
        return exit(result, mandatory);
    }

    private boolean if_stmt() {
        if (!enter(Type.IF_STMT, Token.IF)) {
            return false;
        }
        consume(Token.L_PAREN);
        expr(-1, true);
        consume(Token.R_PAREN);
        statement(true);
        if (consumeIf(Token.ELSE)) {
            statement(true);
        }
        return exit();
    }

    private boolean var_stmt() {
        if (!enter(Type.VAR_STMT, Token.VAR, Token.CONST, Token.LET)) {
            return false;
        }
        if (!var_stmt_names()) {
            error(Type.VAR_STMT_NAMES);
        }
        if (consumeIf(Token.EQ)) {
            expr(-1, true);
        }
        return exit();
    }

    private boolean var_stmt_names() {
        if (!enter(Type.VAR_STMT_NAMES, Token.IDENT)) {
            return false;
        }
        while (consumeIf(Token.COMMA)) {
            consume(Token.IDENT);
        }
        return exit();
    }

    private boolean return_stmt() {
        if (!enter(Type.RETURN_STMT, Token.RETURN)) {
            return false;
        }
        expr(-1, false);
        return exit();
    }

    private boolean throw_stmt() {
        if (!enter(Type.THROW_STMT, Token.THROW)) {
            return false;
        }
        expr(-1, true);
        return exit();
    }

    private boolean try_stmt() {
        if (!enter(Type.TRY_STMT, Token.TRY)) {
            return false;
        }
        block(true);
        if (consumeIf(Token.CATCH)) {
            if (consumeIf(Token.L_PAREN) && consumeIf(Token.IDENT) && consumeIf(Token.R_PAREN) && block(true)) {
                if (consumeIf(Token.FINALLY)) {
                    block(true);
                }
            } else {
                error(Token.CATCH);
            }
        } else if (consumeIf(Token.FINALLY)) {
            block(true);
        } else {
            error("expected " + Token.CATCH + " or " + Token.FINALLY);
        }
        return exit();
    }

    private boolean for_stmt() {
        if (!enter(Type.FOR_STMT, Token.FOR)) {
            return false;
        }
        consume(Token.L_PAREN);
        if (peekIf(Token.SEMI) || var_stmt() || expr(-1, false)) {
            // ok
        } else {
            error(Type.VAR_STMT, Type.EXPR);
        }
        if (consumeIf(Token.SEMI)) {
            if (peekIf(Token.SEMI) || expr(-1, false)) {
                if (consumeIf(Token.SEMI)) {
                    if (peekIf(Token.R_PAREN) || expr(-1, false)) {
                        // ok
                    } else {
                        error(Type.EXPR);
                    }
                } else {
                    error(Token.SEMI);
                }
            } else {
                error(Type.EXPR);
            }
        } else if (anyOf(Token.IN, Token.OF)) {
            expr(-1, true);
        } else {
            error(Token.SEMI, Token.IN, Token.OF);
        }
        consume(Token.R_PAREN);
        statement(true);
        return exit();
    }

    private boolean while_stmt() {
        if (!enter(Type.WHILE_STMT, Token.WHILE)) {
            return false;
        }
        consume(Token.L_PAREN);
        expr(-1, true);
        consume(Token.R_PAREN);
        statement(true);
        return exit();
    }

    private boolean switch_stmt() {
        if (!enter(Type.SWITCH_STMT, Token.SWITCH)) {
            return false;
        }
        consume(Token.L_PAREN);
        expr(-1, true);
        consume(Token.R_PAREN);
        consume(Token.L_CURLY);
        while (true) {
            if (!case_block()) {
                break;
            }
        }
        default_block();
        consume(Token.R_CURLY);
        return exit();
    }

    private boolean case_block() {
        if (!enter(Type.CASE_BLOCK, Token.CASE)) {
            return false;
        }
        expr(-1, true);
        consume(Token.COLON);
        while (true) {
            if (!statement(false)) {
                break;
            }
        }
        return exit();
    }

    private void default_block() {
        if (!enter(Type.DEFAULT_BLOCK, Token.DEFAULT)) {
            return;
        }
        consume(Token.COLON);
        while (true) {
            if (!statement(false)) {
                break;
            }
        }
    }

    private boolean break_stmt() {
        if (!enter(Type.BREAK_STMT, Token.BREAK)) {
            return false;
        }
        return exit();
    }

    // as per spec this is an expression
    private boolean delete_stmt() {
        if (!enter(Type.DELETE_STMT, Token.DELETE)) {
            return false;
        }
        expr(8, true);
        return exit();
    }

    private boolean block(boolean mandatory) {
        if (!enter(Type.BLOCK, Token.L_CURLY)) {
            if (mandatory) {
                error(Type.BLOCK);
            }
            return false;
        }
        while (true) {
            if (!statement(false)) {
                break;
            }
        }
        consume(Token.R_CURLY);
        return exit();
    }

    //==================================================================================================================
    //
    private boolean expr(int priority, boolean mandatory) {
        enter(Type.EXPR);
        boolean result = fn_arrow_expr();
        result = result || fn_expr();
        result = result || new_expr();
        result = result || typeof_expr();
        result = result || ref_expr();
        result = result || lit_expr();
        result = result || paren_expr();
        result = result || unary_expr();
        result = result || math_pre_expr();
        expr_rhs(priority);
        return exit(result, mandatory);
    }

    private void expr_rhs(int priority) {
        while (true) {
            if (priority < 0 && enter(Type.ASSIGN_EXPR,
                    Token.EQ, Token.PLUS_EQ, Token.MINUS_EQ,
                    Token.STAR_EQ, Token.SLASH_EQ, Token.PERCENT_EQ, Token.STAR_STAR_EQ,
                    Token.GT_GT_EQ, Token.LT_LT_EQ, Token.GT_GT_GT_EQ)) {
                expr(-1, true);
                exit(Shift.RIGHT);
            } else if (priority < 1 && enter(Type.LOGIC_TERN_EXPR, Token.QUES)) {
                expr(-1, true);
                consume(Token.COLON);
                expr(-1, true);
                exit(Shift.RIGHT);
            } else if (priority < 2 && enter(Type.LOGIC_AND_EXPR, Token.AMP_AMP, Token.PIPE_PIPE)) {
                expr(2, true);
                exit(Shift.LEFT);
            } else if (priority < 3 && enter(Type.LOGIC_EXPR,
                    Token.EQ_EQ_EQ, Token.NOT_EQ_EQ, Token.EQ_EQ, Token.NOT_EQ,
                    Token.LT, Token.GT, Token.LT_EQ, Token.GT_EQ)) {
                expr(3, true);
                exit(Shift.LEFT);
            } else if (priority < 4 && enter(Type.LOGIC_BIT_EXPR, Token.AMP, Token.PIPE, Token.CARET,
                    Token.GT_GT, Token.LT_LT, Token.GT_GT_GT)) {
                expr(4, true);
                exit(Shift.LEFT);
            } else if (priority < 5 && enter(Type.MATH_ADD_EXPR, Token.PLUS, Token.MINUS)) {
                expr(5, true);
                exit(Shift.LEFT);
            } else if (priority < 6 && enter(Type.MATH_MUL_EXPR, Token.STAR, Token.SLASH, Token.PERCENT)) {
                expr(6, true);
                exit(Shift.LEFT);
            } else if (priority < 7 && peekIf(Token.STAR_STAR)) {
                while (true) {
                    enter(Type.MATH_EXP_EXPR);
                    consumeNext();
                    expr(7, true);
                    exit(Shift.RIGHT);
                    if (!peekIf(Token.STAR_STAR)) {
                        break;
                    }
                }
            } else if (enter(Type.FN_CALL_EXPR, Token.L_PAREN)) {
                fn_call_args();
                consume(Token.R_PAREN);
                exit(Shift.LEFT);
            } else if (enter(Type.REF_DOT_EXPR, Token.DOT)) {
                consume(Token.IDENT);
                exit(Shift.LEFT);
            } else if (enter(Type.REF_BRACKET_EXPR, Token.L_BRACKET)) {
                expr(-1, true);
                consume(Token.R_BRACKET);
                exit(Shift.LEFT);
            } else if (enter(Type.MATH_POST_EXPR, Token.PLUS_PLUS, Token.MINUS_MINUS)) {
                exit(Shift.LEFT);
            } else if (enter(Type.INSTANCEOF_EXPR, Token.INSTANCEOF)) {
                consume(Token.IDENT);
                exit(Shift.LEFT);
            } else {
                break;
            }
        }
    }

    private boolean fn_arrow_expr() {
        enter(Type.FN_ARROW_EXPR);
        boolean result = consumeIf(Token.IDENT);
        result = result || (consumeIf(Token.L_PAREN) && fn_decl_args() && consumeIf(Token.R_PAREN));
        result = result && consumeIf(Token.EQ_GT);
        result = result && (block(false) || expr(-1, false));
        return exit(result, false);
    }

    private boolean fn_expr() {
        if (!enter(Type.FN_EXPR, Token.FUNCTION)) {
            return false;
        }
        consumeIf(Token.IDENT);
        consume(Token.L_PAREN);
        fn_decl_args();
        consume(Token.R_PAREN);
        block(true);
        return exit();
    }

    private boolean fn_decl_args() {
        enter(Type.FN_DECL_ARGS);
        while (true) {
            if (peekIf(Token.R_PAREN)) {
                break;
            }
            if (!fn_decl_arg()) {
                break;
            }
        }
        return exit();
    }

    private boolean fn_decl_arg() {
        enter(Type.FN_DECL_ARG);
        if (consumeIf(Token.DOT_DOT_DOT)) {
            consume(Token.IDENT);
            if (!peekIf(Token.R_PAREN)) {
                error(Token.R_PAREN);
            }
            return exit();
        }
        boolean result = consumeIf(Token.IDENT);
        result = result && (consumeIf(Token.COMMA) || peekIf(Token.R_PAREN));
        return exit(result, false);
    }

    private boolean fn_call_args() {
        enter(Type.FN_CALL_ARGS);
        while (true) {
            if (peekIf(Token.R_PAREN)) {
                break;
            }
            if (!fn_call_arg()) {
                break;
            }
        }
        return exit();
    }

    private boolean fn_call_arg() {
        enter(Type.FN_CALL_ARG);
        consumeIf(Token.DOT_DOT_DOT);
        boolean result = expr(-1, false);
        result = result && (consumeIf(Token.COMMA) || peekIf(Token.R_PAREN));
        return exit(result, false);
    }

    private boolean new_expr() {
        if (!enter(Type.NEW_EXPR, Token.NEW)) {
            return false;
        }
        expr(8, true);
        return exit();
    }

    private boolean typeof_expr() {
        if (!enter(Type.TYPEOF_EXPR, Token.TYPEOF)) {
            return false;
        }
        expr(8, true);
        return exit();
    }

    private boolean ref_expr() {
        if (!enter(Type.REF_EXPR, Token.IDENT)) {
            return false;
        }
        return exit();
    }

    private boolean lit_expr() {
        enter(Type.LIT_EXPR);
        boolean result = lit_object() || lit_array();
        result = result || anyOf(Token.S_STRING, Token.D_STRING, Token.NUMBER, Token.TRUE, Token.FALSE, Token.NULL);
        result = result || lit_template();
        return exit(result, false);
    }

    private boolean lit_template() {
        if (!enter(Type.LIT_TEMPLATE, Token.BACKTICK)) {
            return false;
        }
        while (true) {
            if (consumeIf(Token.BACKTICK)) {
                break;
            }
            if (!consumeIf(Token.T_STRING)) {
                if (consumeIf(Token.DOLLAR_L_CURLY)) {
                    expr(-1, false);
                    consume(Token.R_CURLY);
                }
            }
        }
        return exit();
    }

    private boolean unary_expr() {
        if (!enter(Type.UNARY_EXPR, Token.NOT, Token.TILDE)) {
            return false;
        }
        expr(-1, true);
        return exit();
    }

    private boolean math_pre_expr() {
        if (!enter(Type.MATH_PRE_EXPR, Token.PLUS_PLUS, Token.MINUS_MINUS, Token.MINUS, Token.PLUS)) {
            return false;
        }
        if (expr(8, false) || consumeIf(Token.NUMBER)) {
            // all good
        } else {
            error(Type.EXPR);
        }
        return exit();
    }

    private boolean lit_object() {
        if (!enter(Type.LIT_OBJECT, Token.L_CURLY)) {
            return false;
        }
        while (true) {
            if (peekIf(Token.R_CURLY)) {
                break;
            }
            if (!object_elem()) {
                break;
            }
        }
        boolean result = consumeIf(Token.R_CURLY);
        return exit(result, false);
    }

    private boolean object_elem() {
        if (!enter(Type.OBJECT_ELEM, Token.IDENT, Token.S_STRING, Token.D_STRING, Token.NUMBER)) {
            return false;
        }
        if (!consumeIf(Token.COLON)) {
            return exit(false, false); // could be block
        }
        expr(-1, true);
        if (consumeIf(Token.COMMA) || peekIf(Token.R_CURLY)) {
            // all good
        } else {
            error(Token.COMMA, Token.R_CURLY);
        }
        return exit();
    }

    private boolean lit_array() {
        if (!enter(Type.LIT_ARRAY, Token.L_BRACKET)) {
            return false;
        }
        while (true) {
            if (peekIf(Token.R_BRACKET)) {
                break;
            }
            if (!array_elem()) {
                break;
            }
        }
        consume(Token.R_BRACKET);
        return exit();
    }

    private boolean array_elem() {
        enter(Type.ARRAY_ELEM);
        expr(-1, false); // optional for sparse array
        if (consumeIf(Token.COMMA) || peekIf(Token.R_BRACKET)) {
            // all good
        } else {
            error(Token.COMMA, Token.R_BRACKET);
        }
        return exit();
    }

    private boolean paren_expr() {
        if (!enter(Type.PAREN_EXPR, Token.L_PAREN)) {
            return false;
        }
        expr(-1, true);
        consume(Token.R_PAREN);
        return exit();
    }

}
