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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Interpreter {

    static final Logger logger = LoggerFactory.getLogger(Interpreter.class);

    private static List<String> argNames(Node fnArgs) {
        List<String> list = new ArrayList<>(fnArgs.children.size());
        for (Node fnArg : fnArgs.children) {
            list.add(fnArg.children.get(0).getText());
        }
        return list;
    }

    private static Terms terms(Node node, Context context) {
        return new Terms(context, node.children);
    }

    private static Terms terms(Object lhs, Object rhs) {
        return new Terms(lhs, rhs);
    }

    private static Object evalChunk(Node node, Context context) {
        switch (node.chunk.token) {
            case IDENT:
                String varName = node.getText();
                if (!context.hasKey(varName)) {
                    throw new RuntimeException("unknown identifier: " + varName);
                }
                return context.get(varName);
            case S_STRING:
            case D_STRING:
                return node.chunk.text.substring(1, node.chunk.text.length() - 1);
            case NUMBER:
                return Terms.toNumber(node.chunk.text);
            case NULL:
                return null;
            case TRUE:
                return true;
            case FALSE:
                return false;
            default:
                throw new RuntimeException(node.toStringError("eval - unexpected chunk"));
        }
    }

    private static Object evalAssignExpr(Node node, Context context) {
        JsProperty prop = new JsProperty(node.children.get(0), context);
        Object value = eval(node.children.get(2), context);
        switch (node.children.get(1).chunk.token) {
            case EQ:
                prop.set(value);
                return value;
            case PLUS_EQ:
                value = Terms.add(prop.get(), value);
                break;
            case MINUS_EQ:
                value = terms(prop.get(), value).min();
                break;
            case STAR_EQ:
                value = terms(prop.get(), value).mul();
                break;
            case SLASH_EQ:
                value = terms(prop.get(), value).div();
                break;
            case PERCENT_EQ:
                value = terms(prop.get(), value).mod();
                break;
            case STAR_STAR_EQ:
                value = terms(prop.get(), value).exp();
                break;
            case GT_GT_EQ:
                value = terms(prop.get(), value).bitShiftRight();
                break;
            case LT_LT_EQ:
                value = terms(prop.get(), value).bitShiftLeft();
                break;
            case GT_GT_GT_EQ:
                value = terms(prop.get(), value).bitShiftRightUnsigned();
                break;
            default:
                throw new RuntimeException("unexpected assignment operator: " + node.children.get(1));
        }
        prop.set(value);
        return value;
    }

    private static Object evalBlock(Node node, Context context) {
        Object blockResult = null;
        for (Node child : node.children) {
            if (child.type == Type.STATEMENT) {
                blockResult = eval(child, context);
                if (context.isStopped()) {
                    break;
                }
            }
        }
        // handle return statement
        return context.isStopped() ? context.getReturnValue() : blockResult;
    }

    @SuppressWarnings("unchecked")
    private static Object evalDeleteStmt(Node node, Context context) {
        JsProperty prop = new JsProperty(node.children.get(1), context);
        String key = prop.name == null ? prop.index + "" : prop.name;
        if (prop.object instanceof Map) {
            ((Map<String, Object>) prop.object).remove(key);
        } else if (prop.object instanceof ObjectLike) {
            ((ObjectLike) prop.object).remove(key);
        }
        return true;
    }

    private static Object evalDotExpr(Node node, Context context) {
        // TODO refactor and merge code from evalFnCall into JsProperty
        JsProperty prop = new JsProperty(node, context);
        Object result = prop.get();
        if (result == Undefined.INSTANCE) {
            String className = node.getText();
            if (Engine.JAVA_BRIDGE.typeExists(className)) {
                return new JavaClass(className);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object evalFnCall(Node node, Context context) {
        JsProperty prop = new JsProperty(node.children.get(0), context);
        Object o = prop.get(true);
        Invokable invokable;
        if (o instanceof Invokable) {
            invokable = (Invokable) o;
        } else if (o instanceof JavaClass) {
            JavaClass jc = (JavaClass) o;
            invokable = jc::construct;
        } else if (JavaFunction.isFunction(o)) {
            invokable = new JavaFunction(o);
        } else if (o == Undefined.INSTANCE) {
            String className = node.children.get(0).getText();
            try {
                invokable = args -> Engine.JAVA_BRIDGE.construct(className, args);
            } catch (Exception e) {
                throw new RuntimeException("not a function: " + className);
            }
        } else if (o != null) { // try java interop
            JavaObject jo = new JavaObject(o);
            invokable = new JavaInvokable(prop.name, jo);
        } else {
            throw new RuntimeException("null is not a function");
        }
        Node fnArgsNode = node.children.get(2);
        int argsCount = fnArgsNode.children.size();
        List<Object> argsList = new ArrayList<>();
        for (int i = 0; i < argsCount; i++) {
            Node fnArgNode = fnArgsNode.children.get(i);
            Node argNode = fnArgNode.children.get(0);
            if (argNode.isChunk()) { // DOT_DOT_DOT
                Object arg = eval(fnArgNode.children.get(1), context);
                if (arg instanceof List) {
                    argsList.addAll((List<Object>) arg);
                } else if (arg instanceof ArrayLike) {
                    ArrayLike arrayLike = (ArrayLike) arg;
                    argsList.addAll(arrayLike.toList());
                }
            } else {
                Object arg = eval(argNode, context);
                argsList.add(arg);
            }
        }
        Object[] args = argsList.toArray();
        Object thisObject;
        JsFunction jsFunction;
        if (invokable instanceof JsFunction) {
            jsFunction = (JsFunction) invokable;
            jsFunction.invokeContext = context;
        } else {
            jsFunction = null;
        }
        if (context.newInstance != null) { // new keyword, call constructor
            if (invokable instanceof JsFunction) {
                context.newInstance.setPrototype(((JsFunction) invokable).getPrototype());
            }
            thisObject = context.newInstance;
            context.newInstance = null;
            if (jsFunction != null) {
                jsFunction.thisObject = thisObject;
            }
            Object result = invokable.invoke(args);
            return Terms.isPrimitive(result) ? thisObject : result;
        } else { // normal function call
            thisObject = prop.object == null ? invokable : prop.object;
            if (jsFunction != null) {
                jsFunction.thisObject = thisObject;
            }
            return invokable.invoke(args);
        }
    }

    private static Object evalFnExpr(Node node, Context context) {
        if (node.children.get(1).chunk.token == Token.IDENT) {
            NodeFunction nodeFunction = new NodeFunction(false, argNames(node.children.get(3)), node.children.get(5), context);
            context.declare(node.children.get(1).getText(), nodeFunction);
            return nodeFunction;
        } else {
            return new NodeFunction(false, argNames(node.children.get(2)), node.children.get(4), context);
        }
    }

    private static Object evalFnArrowExpr(Node node, Context context) {
        if (node.children.get(0).chunk.token == Token.IDENT) {
            String argName = node.children.get(0).getText();
            return new NodeFunction(true, Collections.singletonList(argName), node.children.get(2), context);
        } else {
            return new NodeFunction(true, argNames(node.children.get(1)), node.children.get(4), context);
        }
    }

    private static Object evalForStmt(Node node, Context context) {
        Context forContext = new Context(context);
        Node forBody = node.children.get(node.children.size() - 1);
        Object forResult = null;
        if (node.children.get(2).chunk.token == Token.SEMI) {

        } else if (node.children.get(3).chunk.token == Token.SEMI) {
            eval(node.children.get(2), forContext);
            if (node.children.get(4).chunk.token == Token.SEMI) {

            } else {
                Node forAfter = node.children.get(6).chunk.token == Token.R_PAREN ? null : node.children.get(6);
                while (true) {
                    Object forCondition = eval(node.children.get(4), forContext);
                    if (!Terms.isTruthy(forCondition)) {
                        break;
                    }
                    forResult = eval(forBody, forContext);
                    if (forContext.isStopped()) {
                        context.updateFrom(forContext);
                        break;
                    }
                    if (forAfter != null) {
                        eval(forAfter, forContext);
                    }
                }
            }
        } else { // for in / of
            boolean in = node.children.get(3).chunk.token == Token.IN;
            Object forObject = eval(node.children.get(4), forContext);
            Iterable<KeyValue> iterable = JsCommon.toIterable(forObject);
            if (iterable != null) {
                String varName;
                if (node.children.get(2).type == Type.VAR_STMT) {
                    varName = node.children.get(2).children.get(1).getText();
                } else {
                    varName = node.children.get(2).getText();
                }
                for (KeyValue kv : iterable) {
                    if (in) {
                        forContext.declare(varName, kv.key);
                    } else {
                        forContext.declare(varName, kv.value);
                    }
                    forResult = eval(forBody, forContext);
                }
            }
        }
        return forResult;
    }

    private static Object evalIfStmt(Node node, Context context) {
        if (Terms.isTruthy(eval(node.children.get(2), context))) {
            return eval(node.children.get(4), context);
        } else {
            if (node.children.size() > 5) {
                return eval(node.children.get(6), context);
            }
            return null;
        }
    }

    private static Object evalInstanceOfExpr(Node node, Context context) {
        return JsCommon.instanceOf(eval(node.children.get(0), context), eval(node.children.get(2), context));
    }

    private static Object evalLitArray(Node node, Context context) {
        int last = node.children.size() - 1;
        List<Object> list = new ArrayList<>();
        for (int i = 1; i < last; i++) {
            Node elem = node.children.get(i);
            Node exprNode = elem.children.get(0);
            if (exprNode.chunk.token == Token.COMMA) { // sparse
                list.add(null);
            } else {
                Object value = eval(exprNode, context);
                list.add(value);
            }
        }
        return list;
    }

    private static Object evalLitObject(Node node, Context context) {
        int last = node.children.size() - 1;
        Map<String, Object> map = new LinkedHashMap<>(last - 1);
        for (int i = 1; i < last; i++) {
            Node elem = node.children.get(i);
            Node keyNode = elem.children.get(0);
            Token token = keyNode.chunk.token;
            String key;
            if (token == Token.S_STRING || token == Token.D_STRING) {
                key = (String) eval(keyNode, context);
            } else { // IDENT, NUMBER
                key = keyNode.getText();
            }
            Node exprNode = elem.children.get(2);
            Object value = eval(exprNode, context);
            map.put(key, value);
        }
        return map;
    }

    private static String evalLitTemplate(Node node, Context context) {
        StringBuilder sb = new StringBuilder();
        for (Node child : node.children) {
            if (child.chunk.token == Token.T_STRING) {
                sb.append(child.chunk.text);
            } else if (child.type == Type.EXPR) {
                sb.append(eval(child, context));
            }
        }
        return sb.toString();
    }

    private static Object evalLogicBitExpr(Node node, Context context) {
        switch (node.children.get(1).chunk.token) {
            case AMP:
                return terms(node, context).bitAnd();
            case PIPE:
                return terms(node, context).bitOr();
            case CARET:
                return terms(node, context).bitXor();
            case GT_GT:
                return terms(node, context).bitShiftRight();
            case LT_LT:
                return terms(node, context).bitShiftLeft();
            case GT_GT_GT:
                return terms(node, context).bitShiftRightUnsigned();
            default:
                throw new RuntimeException("unexpected operator: " + node.children.get(1));
        }
    }

    private static boolean evalLogicExpr(Node node, Context context) {
        Object lhs = eval(node.children.get(0), context);
        Object rhs = eval(node.children.get(2), context);
        Token logicOp = node.children.get(1).chunk.token;
        if (Undefined.NAN.equals(lhs) || Undefined.NAN.equals(rhs)) {
            if (Undefined.NAN.equals(lhs) && Undefined.NAN.equals(rhs)) {
                return logicOp == Token.NOT_EQ || logicOp == Token.NOT_EQ_EQ;
            }
            return false;
        }
        switch (logicOp) {
            case EQ_EQ:
                return Terms.eq(lhs, rhs, false);
            case EQ_EQ_EQ:
                return Terms.eq(lhs, rhs, true);
            case NOT_EQ:
                return !Terms.eq(lhs, rhs, false);
            case NOT_EQ_EQ:
                return !Terms.eq(lhs, rhs, true);
            case LT:
                return Terms.lt(lhs, rhs);
            case GT:
                return Terms.gt(lhs, rhs);
            case LT_EQ:
                return Terms.ltEq(lhs, rhs);
            case GT_EQ:
                return Terms.gtEq(lhs, rhs);
            default:
                throw new RuntimeException("unexpected operator: " + node.children.get(1));
        }
    }

    private static Object evalLogicAndExpr(Node node, Context context) {
        Object andOrLhs = eval(node.children.get(0), context);
        Object andOrRhs = eval(node.children.get(2), context);
        switch (node.children.get(1).chunk.token) {
            case AMP_AMP:
                return Terms.and(andOrLhs, andOrRhs);
            case PIPE_PIPE:
                return Terms.or(andOrLhs, andOrRhs);
            default:
                throw new RuntimeException("unexpected operator: " + node.children.get(1));
        }
    }

    private static Object evalLogicTernExpr(Node node, Context context) {
        if (Terms.isTruthy(eval(node.children.get(0), context))) {
            return eval(node.children.get(2), context);
        } else {
            return eval(node.children.get(4), context);
        }
    }

    private static Object evalMathAddExpr(Node node, Context context) {
        switch (node.children.get(1).chunk.token) {
            case PLUS:
                return Terms.add(eval(node.children.get(0), context), eval(node.children.get(2), context));
            case MINUS:
                return terms(node, context).min();
            default:
                throw new RuntimeException("unexpected operator: " + node.children.get(1));
        }
    }

    private static Object evalMathMulExpr(Node node, Context context) {
        switch (node.children.get(1).chunk.token) {
            case STAR:
                return terms(node, context).mul();
            case SLASH:
                return terms(node, context).div();
            case PERCENT:
                return terms(node, context).mod();
            default:
                throw new RuntimeException("unexpected operator: " + node.children.get(1));
        }
    }

    private static Object evalMathPostExpr(Node node, Context context) {
        JsProperty postProp = new JsProperty(node.children.get(0), context);
        Object postValue = postProp.get();
        switch (node.children.get(1).chunk.token) {
            case PLUS_PLUS:
                postProp.set(Terms.add(postValue, 1));
                break;
            case MINUS_MINUS:
                postProp.set(terms(postValue, 1).min());
                break;
            default:
                throw new RuntimeException("unexpected operator: " + node.children.get(1));
        }
        return postValue;
    }

    private static Object evalMathPreExpr(Node node, Context context) {
        JsProperty preProp = new JsProperty(node.children.get(1), context);
        Object preValue = preProp.get();
        switch (node.children.get(0).chunk.token) {
            case PLUS_PLUS:
                preProp.set(Terms.add(preValue, 1));
                return preProp.get();
            case MINUS_MINUS:
                preProp.set(terms(preValue, 1).min());
                return preProp.get();
            case MINUS:
                return terms(preValue, -1).mul();
            case PLUS:
                return Terms.toNumber(preValue);
            default:
                throw new RuntimeException("unexpected operator: " + node.children.get(0));
        }
    }

    private static Object evalNewExpr(Node node, Context context) {
        context.newInstance = new JsObject();
        return eval(node.children.get(1), context);
    }

    private static Object evalProgram(Node node, Context context) {
        Object progResult = null;
        for (Node child : node.children) {
            progResult = eval(child, context);
            if (context.isError()) {
                Object errorThrown = context.getErrorThrown();
                String errorMessage = null;
                if (errorThrown instanceof JsObject) {
                    JsObject error = (JsObject) errorThrown;
                    if (error.hasKey("message")) {
                        errorMessage = (String) error.get("message");
                    }
                }
                String message = child.toStringError(errorMessage == null ? errorThrown.toString() : errorMessage);
                throw new EvalError(message);
            }
        }
        return progResult;
    }

    private static Object evalReturnStmt(Node node, Context context) {
        if (node.children.size() > 1) {
            return context.stopAndReturn(eval(node.children.get(1), context));
        } else {
            return context.stopAndReturn(null);
        }
    }

    private static Object evalStatement(Node node, Context context) {
        try {
            Object statementResult = eval(node.children.get(0), context);
            if (logger.isTraceEnabled() || Engine.DEBUG) {
                Type childType = node.children.get(0).type;
                if (childType != Type.EXPR && childType != Type.BLOCK) {
                    Chunk first = node.getFirstChunk();
                    logger.trace("{}{} {} | {}", first.source, first.getPosition(), statementResult, node);
                    if (Engine.DEBUG) {
                        System.out.println(first.source + first.getPosition() + " " + statementResult + " | " + node);
                    }
                }
            }
            return statementResult;
        } catch (Exception e) {
            Chunk first = node.getFirstChunk();
            String message = "js failed:\n==========\n" + first.getLine() + "\n==========\n"
                    + first.source + first.getPosition() + " " + e.getMessage();
            System.err.println(message);
            throw new RuntimeException(message, e);
        }
    }

    private static Object evalSwitchStmt(Node node, Context context) {
        Object switchValue = eval(node.children.get(2), context);
        List<Node> caseNodes = node.findChildrenOfType(Type.CASE_BLOCK);
        for (Node caseNode : caseNodes) {
            Object caseValue = eval(caseNode.children.get(1), context);
            if (Terms.eq(switchValue, caseValue, true)) {
                Object caseResult = evalBlock(caseNode, context);
                if (context.isStopped()) {
                    return caseResult;
                }
            }
        }
        List<Node> defaultNodes = node.findChildrenOfType(Type.DEFAULT_BLOCK);
        if (!defaultNodes.isEmpty()) {
            return evalBlock(defaultNodes.get(0), context);
        }
        return null;
    }

    private static Object evalTryStmt(Node node, Context context) {
        Object tryValue = eval(node.children.get(1), context);
        Node finallyBlock = null;
        if (node.children.get(2).chunk.token == Token.CATCH) {
            if (node.children.size() > 7) {
                finallyBlock = node.children.get(8);
            }
            if (context.isError()) {
                String errorName = node.children.get(4).getText();
                Context catchContext = new Context(context);
                catchContext.declare(errorName, context.getErrorThrown());
                tryValue = eval(node.children.get(6), catchContext);
                if (catchContext.isError()) { // catch threw error,
                    tryValue = null;
                }
                context.updateFrom(catchContext);
            }
        } else if (node.children.get(2).chunk.token == Token.FINALLY) {
            finallyBlock = node.children.get(3);
        }
        if (finallyBlock != null) {
            Context finallyContext = new Context(context);
            eval(finallyBlock, finallyContext);
            if (finallyContext.isError()) {
                throw new EvalError("finally block threw error: " + finallyContext.getErrorThrown());
            }
        }
        return tryValue;
    }

    private static Object evalUnaryExpr(Node node, Context context) {
        Object unaryValue = eval(node.children.get(1), context);
        switch (node.children.get(0).chunk.token) {
            case NOT:
                return !Terms.isTruthy(unaryValue);
            case TILDE:
                return Terms.bitNot(unaryValue);
            default:
                throw new RuntimeException("unexpected operator: " + node.children.get(0));
        }
    }

    private static Object evalVarStmt(Node node, Context context) {
        Object varValue;
        if (node.children.size() > 3) {
            varValue = eval(node.children.get(3), context);
        } else {
            varValue = Undefined.INSTANCE;
        }
        List<Node> varNames = node.children.get(1).findAll(Token.IDENT);
        // TODO let & const
        for (Node varName : varNames) {
            context.declare(varName.getText(), varValue);
        }
        return varValue;
    }

    private static Object evalWhileStmt(Node node, Context context) {
        Context whileContext = new Context(context);
        Node whileBody = node.children.get(node.children.size() - 1);
        Node whileExpr = node.children.get(2);
        Object whileResult = null;
        while (true) {
            Object whileCondition = eval(whileExpr, whileContext);
            if (!Terms.isTruthy(whileCondition)) {
                break;
            }
            whileResult = eval(whileBody, whileContext);
            if (whileContext.isStopped()) {
                context.updateFrom(whileContext);
                break;
            }
        }
        return whileResult;
    }

    private static Object evalDoWhileStmt(Node node, Context context) {
        Context doContext = new Context(context);
        Node doBody = node.children.get(1);
        Node doExpr = node.children.get(4);
        Object doResult = null;
        while (true) {
            doResult = eval(doBody, doContext);
            if (doContext.isStopped()) {
                context.updateFrom(doContext);
                break;
            }
            Object doCondition = eval(doExpr, doContext);
            if (!Terms.isTruthy(doCondition)) {
                break;
            }
        }
        return doResult;
    }

    public static Object eval(Node node, Context context) {
        context.currentNode = node;
        switch (node.type) {
            case _CHUNK:
                return evalChunk(node, context);
            case ASSIGN_EXPR:
                return evalAssignExpr(node, context);
            case BLOCK:
                return evalBlock(node, context);
            case BREAK_STMT:
                return context.stopAndReturn(null);
            case DELETE_STMT:
                return evalDeleteStmt(node, context);
            case EXPR:
            case LIT_EXPR:
                return eval(node.children.get(0), context);
            case FN_EXPR:
                return evalFnExpr(node, context);
            case FN_ARROW_EXPR:
                return evalFnArrowExpr(node, context);
            case FN_CALL_EXPR:
                return evalFnCall(node, context);
            case FOR_STMT:
                return evalForStmt(node, context);
            case IF_STMT:
                return evalIfStmt(node, context);
            case INSTANCEOF_EXPR:
                return evalInstanceOfExpr(node, context);
            case LIT_ARRAY:
                return evalLitArray(node, context);
            case LIT_OBJECT:
                return evalLitObject(node, context);
            case LIT_TEMPLATE:
                return evalLitTemplate(node, context);
            case LOGIC_EXPR:
                return evalLogicExpr(node, context);
            case LOGIC_AND_EXPR:
                return evalLogicAndExpr(node, context);
            case LOGIC_BIT_EXPR:
                return evalLogicBitExpr(node, context);
            case LOGIC_TERN_EXPR:
                return evalLogicTernExpr(node, context);
            case MATH_ADD_EXPR:
                return evalMathAddExpr(node, context);
            case MATH_EXP_EXPR:
                return terms(node, context).exp();
            case MATH_MUL_EXPR:
                return evalMathMulExpr(node, context);
            case MATH_POST_EXPR:
                return evalMathPostExpr(node, context);
            case MATH_PRE_EXPR:
                return evalMathPreExpr(node, context);
            case NEW_EXPR:
                return evalNewExpr(node, context);
            case PAREN_EXPR:
                return eval(node.children.get(1), context);
            case PROGRAM:
                return evalProgram(node, context);
            case REF_EXPR:
                return context.get(node.getText());
            case REF_BRACKET_EXPR:
                return new JsProperty(node, context).get();
            case REF_DOT_EXPR:
                return evalDotExpr(node, context);
            case RETURN_STMT:
                return evalReturnStmt(node, context);
            case STATEMENT:
                return evalStatement(node, context);
            case SWITCH_STMT:
                return evalSwitchStmt(node, context);
            case THROW_STMT:
                return context.stopAndThrow(eval(node.children.get(1), context));
            case TRY_STMT:
                return evalTryStmt(node, context);
            case TYPEOF_EXPR:
                return Terms.typeOf(eval(node.children.get(1), context));
            case UNARY_EXPR:
                return evalUnaryExpr(node, context);
            case VAR_STMT:
                return evalVarStmt(node, context);
            case WHILE_STMT:
                return evalWhileStmt(node, context);
            case DO_WHILE_STMT:
                return evalDoWhileStmt(node, context);
            default:
                throw new RuntimeException(node.toStringError("eval - unexpected node"));
        }
    }

}
