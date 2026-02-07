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
import java.util.List;
import java.util.Map;

public class JsArray extends Prototype implements ArrayLike {

    final List<Object> list;

    public JsArray(List<Object> list) {
        this.list = list;
    }

    public JsArray() {
        this(new ArrayList<>());
    }

    @Override
    Map<String, Object> initPrototype() {
        return JsCommon.getBaseArrayPrototype(this);
    }

    @Override
    public Object get(int index) {
        return list.get(index);
    }

    @Override
    public void set(int index, Object value) {
        list.set(index, value);
    }

    @Override
    public void add(Object value) {
        list.add(value);
    }

    @Override
    public void remove(int index) {
        list.remove(index);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public List<Object> toList() {
        return list;
    }

    @Override
    public Object get(String name) {
        if ("prototype".equals(name)) {
            return getPrototype();
        }
        Object result = getPrototype().get(name);
        if (result instanceof Property) {
            return ((Property) result).get();
        }
        return result;
    }

}
