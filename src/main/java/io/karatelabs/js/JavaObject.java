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

import java.util.Collection;
import java.util.Map;

public class JavaObject implements JavaMethods, JavaFields, ObjectLike {

    private final Object object;

    public JavaObject(Object object) {
        this.object = object;
    }

    @Override
    public Object call(String name, Object... args) {
        return JavaBridge.convertIfArray(Engine.JAVA_BRIDGE.invoke(object, name, args));
    }

    @Override
    public Object read(String name) {
        return JavaBridge.convertIfArray(Engine.JAVA_BRIDGE.getStatic(object.getClass().getName(), name));
    }

    @Override
    public void update(String name, Object value) {
        Engine.JAVA_BRIDGE.setStatic(object.getClass().getName(), name, value);
    }

    @Override
    public Object get(String name) {
        return JavaBridge.convertIfArray(Engine.JAVA_BRIDGE.get(object, name));
    }

    @Override
    public void put(String name, Object value) {
        Engine.JAVA_BRIDGE.set(object, name, value);
    }

    @Override
    public void putAll(Map<String, Object> values) {
        values.forEach(this::put);
    }

    @Override
    public boolean hasKey(String name) {
        return JavaBridge.findGetter(object, name) != null;
    }

    @Override
    public Collection<String> keys() {
        return JavaBridge.propertyNames(object);
    }

    @Override
    public void remove(String name) {
        throw new RuntimeException("remove not supported on java object: " + object.getClass());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> toMap() {
        return (Map<String, Object>) JavaBridge.toMapOrList(object);
    }

}

