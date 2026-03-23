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

import java.io.File;
import java.nio.file.Files;

public class Engine {

    public static JavaBridge JAVA_BRIDGE = new JavaBridge() {
        // non-final default that you can over-ride
    };

    public static boolean DEBUG = false;

    public final Context context;
    public Source source;

    private Engine(Context context) {
        this.context = context;
    }

    public Engine() {
        this(Context.root());
    }

    public Object eval(Source source) {
        return evalInternal(source);
    }

    public Object eval(File file) {
        return evalInternal(new Source(file, toString(file)));
    }

    public Object eval(String text) {
        return evalInternal(new Source(null, text));
    }

    public static boolean isUndefined(Object o) {
        return o == Undefined.INSTANCE || Undefined.NAN.equals(o);
    }

    private Object evalInternal(Source source) {
        this.source = source;
        try {
            Parser parser = new Parser(source);
            Node node = parser.parse();
            return Interpreter.eval(node, context);
        } catch (Throwable e) {
            String message = e.getMessage();
            if (message == null) {
                message = e + "";
            }
            message = message + "\n" + source.getStringForLog();
            throw new EvalError(message);
        }
    }

    public Engine copy() {
        return new Engine(context.copy());
    }

    public static Object exec(File file) {
        Engine engine = new Engine();
        return engine.eval(file);
    }

    public static Object exec(String text) {
        Engine engine = new Engine();
        return engine.eval(text);
    }

    private static String toString(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Source toSource(File file) {
        return new Source(file, toString(file));
    }

}
