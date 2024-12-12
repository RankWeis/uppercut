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

public class Marker {

    final int position;
    final Marker caller;
    final Node node;
    final int depth;

    public Marker(int position, Marker caller, Node node, int depth) {
        this.position = position;
        this.caller = caller;
        this.node = node;
        this.depth = depth;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (caller != null) {
            if (caller.caller != null) {
                sb.append(caller.caller.node.type).append(" >> ");
            }
            sb.append(caller.node.type).append(" >> ");
        }
        sb.append('[');
        sb.append(node.type);
        sb.append(']');
        return sb.toString();
    }

}
