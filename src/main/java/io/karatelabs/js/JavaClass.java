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

public class JavaClass implements Constructable, JavaMethods, JavaFields {

    private final String className;

    public JavaClass(Class<?> clazz) {
        className = clazz.getName();
    }

    public JavaClass(String className) {
        this.className = className;
    }

    @Override
    public Object construct(Object[] args) {
        return Engine.JAVA_BRIDGE.construct(className, args);
    }

    @Override
    public Object call(String name, Object[] args) {
        return JavaBridge.convertIfArray(Engine.JAVA_BRIDGE.invokeStatic(className, name, args));
    }

    @Override
    public Object read(String name) {
        return JavaBridge.convertIfArray(Engine.JAVA_BRIDGE.getStatic(className, name));
    }

    @Override
    public void update(String name, Object value) {
        Engine.JAVA_BRIDGE.setStatic(className, name, value);
    }

}
