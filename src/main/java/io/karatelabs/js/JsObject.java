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
import java.util.HashMap;
import java.util.Map;

public class JsObject extends Prototype implements ObjectLike {

    private final Map<String, Object> map;

    public JsObject(Map<String, Object> map) {
        this.map = map;
    }

    public JsObject() {
        this(new HashMap<>());
    }

    @Override
    Map<String, Object> initPrototype() {
        return JsCommon.getBaseObjectPrototype(this);
    }

    @Override
    public Object get(String name) {
        if (map.containsKey(name)) {
            return map.get(name);
        }
        if ("prototype".equals(name)) {
            return getPrototype();
        }
        Object result = getPrototype().get(name);
        if (result instanceof Property) {
            return ((Property) result).get();
        }
        return result;
    }

    @Override
    public void put(String name, Object value) {
        map.put(name, value);
    }

    @Override
    public void putAll(Map<String, Object> values) {
        map.putAll(values);
    }

    @Override
    public boolean hasKey(String name) {
        if (map.containsKey(name)) {
            return true;
        }
        return getPrototype().containsKey(name);
    }

    @Override
    public Collection<String> keys() {
        return map.keySet();
    }

    @Override
    public void remove(String name) {
        map.remove(name);
    }

    @Override
    public Map<String, Object> toMap() {
        return map;
    }


}
