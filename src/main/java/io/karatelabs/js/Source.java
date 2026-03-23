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

public class Source {

    public final String text;
    public final File file;

    String[] lines;

    public Source(String text) {
        this.file = null;
        this.text = text;
    }

    public Source(File file, String text) {
        this.file = file;
        this.text = text;
    }

    public String getLine(int index) {
        if (lines == null) {
            lines = text.split("\\r?\\n");
        }
        return lines[index];
    }

    @Override
    public String toString() {
        return file == null ? "" : file.toString();
    }

    public String getStringForLog() {
        if (file != null) {
            try {
                return "file://" + file.getCanonicalFile();
            } catch (Exception ee) {
                return file.getPath();
            }
        } else {
            return "(inline)";
        }
    }

}
