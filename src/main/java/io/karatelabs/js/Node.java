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

import static com.rankweis.uppercut.karate.lexer.karatelabs.KarateLexerAdapter.getType;

import com.intellij.lang.PsiBuilder;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Node {

    static final Logger logger = LoggerFactory.getLogger(Node.class);

    final Type type;
    final Chunk chunk;
    final List<Node> children = new ArrayList<>();
    PsiBuilder.Marker marker;

    public Node(Type type) {
        this.type = type;
        chunk = Chunk._NODE;
    }

    public Node(Type type, PsiBuilder builder) {
        this.type = type;
        this.marker = builder == null ? null : builder.mark();
        chunk = Chunk._NODE;
    }

    public Node(Chunk chunk) {
        this.chunk = chunk;
        type = Type._CHUNK;
    }

    public void done() {
        if (marker != null) {
            marker.done(getType(type));
        }
    }

    public boolean isChunk() {
        return type == Type._CHUNK;
    }

    public Chunk getFirstChunk() {
        if (isChunk()) {
            return chunk;
        }
        if (children.isEmpty()) {
            return Chunk._NODE;
        }
        return children.get(0).getFirstChunk();
    }

    public String toStringError(String message) {
        Chunk first = getFirstChunk();
        return first.getPosition() + " " + type + "\n" + first.source.getStringForLog() + "\n" + message;
    }

    @Override
    public String toString() {
        if (isChunk()) {
            return chunk.text;
        }
        StringBuilder sb = new StringBuilder();
        for (Node child : children) {
            if (sb.length() != 0) {
                sb.append(' ');
            }
            sb.append(child.toString());
        }
        return sb.toString();
    }

    public Node findFirst(Type type) {
        for (Node child : children) {
            if (child.type == type) {
                return child;
            }
            Node temp = child.findFirst(type);
            if (temp != null) {
                return temp;
            }
        }
        return null;
    }

    public Node findFirst(Token token) {
        for (Node child : children) {
            if (child.chunk.token == token) {
                return child;
            }
            Node temp = child.findFirst(token);
            if (temp != null) {
                return temp;
            }
        }
        return null;
    }

    public List<Node> findChildrenOfType(Type type) {
        List<Node> results = new ArrayList<>();
        for (Node child : children) {
            if (child.type == type) {
                results.add(child);
            }
        }
        return results;
    }

    public List<Node> findAll(Token token) {
        List<Node> results = new ArrayList<>();
        findAll(token, results);
        return results;
    }

    private void findAll(Token token, List<Node> results) {
        for (Node child : children) {
            if (!child.isChunk()) {
                child.findAll(token, results);
            } else if (child.chunk.token == token) {
                results.add(child);
            }
        }
    }

    public String getText() {
        if (isChunk()) {
            return chunk.text;
        }
        StringBuilder sb = new StringBuilder();
        for (Node child : children) {
            sb.append(child.getText());
        }
        return sb.toString();
    }

}
