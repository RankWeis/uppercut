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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class Terms {

    static final Number POSITIVE_INFINITY = Double.POSITIVE_INFINITY;
    static final Number NEGATIVE_INFINITY = Double.NEGATIVE_INFINITY;

    static final Number POSITIVE_ZERO = 0;
    static final Number NEGATIVE_ZERO = -0.0;

    final Number lhs;
    final Number rhs;

    Terms(Object lhsObject, Object rhsObject) {
        lhs = toNumber(lhsObject);
        rhs = toNumber(rhsObject);
    }

    Terms(Context context, List<Node> children) {
        this(Interpreter.eval(children.get(0), context), Interpreter.eval(children.get(2), context));
    }

    public static Number toNumber(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return (Number) value;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return narrow(Double.parseDouble(text));
        } catch (Exception e) {
            if (text.charAt(0) == '0') {
                char second = text.charAt(1);
                if (second == 'x' || second == 'X') { // hex
                    long longValue = Long.parseLong(text.substring(2), 16);
                    return narrow(longValue);
                }
            }
            return Undefined.NAN;
        }
    }

    static Object or(Object lhs, Object rhs) {
        return isTruthy(lhs) ? lhs : rhs;
    }

    static Object and(Object lhs, Object rhs) {
        return isTruthy(lhs) ? rhs : lhs;
    }

    static boolean eq(Object lhs, Object rhs, boolean strict) {
        if (lhs == null) {
            return rhs == null || !strict && rhs == Undefined.INSTANCE;
        }
        if (lhs == Undefined.INSTANCE) {
            return rhs == Undefined.INSTANCE || !strict && rhs == null;
        }
        if (lhs == rhs) { // instance equality !
            return true;
        }
        if (lhs instanceof List || lhs instanceof Map) {
            return false;
        }
        if (lhs.equals(rhs)) {
            return true;
        }
        if (strict) {
            if (lhs instanceof Number && rhs instanceof Number) {
                return ((Number) lhs).doubleValue() == ((Number) rhs).doubleValue();
            }
            return false;
        }
        if (lhs instanceof Number || rhs instanceof Number) { // coerce to number
            Terms terms = new Terms(lhs, rhs);
            return terms.lhs.equals(terms.rhs);
        }
        return false;
    }

    static boolean lt(Object lhs, Object rhs) {
        Terms terms = new Terms(lhs, rhs);
        return terms.lhs.doubleValue() < terms.rhs.doubleValue();
    }

    static boolean gt(Object lhs, Object rhs) {
        Terms terms = new Terms(lhs, rhs);
        return terms.lhs.doubleValue() > terms.rhs.doubleValue();
    }

    static boolean ltEq(Object lhs, Object rhs) {
        Terms terms = new Terms(lhs, rhs);
        return terms.lhs.doubleValue() <= terms.rhs.doubleValue();
    }

    static boolean gtEq(Object lhs, Object rhs) {
        Terms terms = new Terms(lhs, rhs);
        return terms.lhs.doubleValue() >= terms.rhs.doubleValue();
    }

    Object bitAnd() {
        return lhs.intValue() & rhs.intValue();
    }

    Object bitOr() {
        return lhs.intValue() | rhs.intValue();
    }

    Object bitXor() {
        return lhs.intValue() ^ rhs.intValue();
    }

    Object bitShiftRight() {
        return lhs.intValue() >> rhs.intValue();
    }

    Object bitShiftLeft() {
        return lhs.intValue() << rhs.intValue();
    }

    Object bitShiftRightUnsigned() {
        return narrow((lhs.intValue() & 0xFFFFFFFFL) >>> rhs.intValue());
    }

    static Object bitNot(Object value) {
        Number number = toNumber(value);
        return ~number.intValue();
    }

    Object mul() {
        double result = lhs.doubleValue() * rhs.doubleValue();
        return narrow(result);
    }

    Object div() {
        if (rhs.equals(POSITIVE_ZERO)) {
            return lhs.doubleValue() > 0 ? POSITIVE_INFINITY : NEGATIVE_INFINITY;
        }
        if (rhs.equals(NEGATIVE_ZERO)) {
            return lhs.doubleValue() < 0 ? POSITIVE_INFINITY : NEGATIVE_INFINITY;
        }
        if (rhs.equals(POSITIVE_INFINITY)) {
            return lhs.doubleValue() > 0 ? POSITIVE_ZERO : NEGATIVE_ZERO;
        }
        if (rhs.equals(NEGATIVE_INFINITY)) {
            return lhs.doubleValue() < 0 ? POSITIVE_ZERO : NEGATIVE_ZERO;
        }
        double result = lhs.doubleValue() / rhs.doubleValue();
        return narrow(result);
    }

    Object min() {
        double result = lhs.doubleValue() - rhs.doubleValue();
        return narrow(result);
    }

    Object mod() {
        double result = lhs.doubleValue() % rhs.doubleValue();
        return narrow(result);
    }

    Object exp() {
        double result = Math.pow(lhs.doubleValue(), rhs.doubleValue());
        return narrow(result);
    }

    static Object add(Object lhs, Object rhs) {
        if (!(lhs instanceof Number) || !(rhs instanceof Number)) {
            return lhs + "" + rhs;
        }
        Number lhsNum = (Number) lhs;
        Number rhsNum = (Number) rhs;
        double result = lhsNum.doubleValue() + rhsNum.doubleValue();
        return narrow(result);
    }

    public static Number narrow(double d) {
        if (NEGATIVE_ZERO.equals(d)) {
            return d;
        }
        if (d % 1 != 0) {
            return d;
        }
        if (d <= Integer.MAX_VALUE) {
            return (int) d;
        }
        if (d <= Long.MAX_VALUE) {
            return (long) d;
        }
        return d;
    }

    public static boolean isTruthy(Object value) {
        if (value == null || value.equals(Undefined.INSTANCE) || value.equals(Undefined.NAN)) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        if (value instanceof String) {
            return !((String) value).isEmpty();
        }
        return true;
    }

    public static boolean isPrimitive(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String
                || (value instanceof Number && !(value instanceof BigDecimal))
                || value instanceof Boolean) {
            return true;
        }
        return value == Undefined.INSTANCE;
    }

    public static String typeOf(Object value) {
        if (value instanceof Invokable) {
            return "function";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value == Undefined.INSTANCE) {
            return "undefined";
        }
        return "object";
    }

}
