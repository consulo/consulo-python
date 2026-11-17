// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.fstrings;

/**
 * One recognized piece of a format spec, with its offset inside the parsed text.
 */
public final class PyFormatSpecComponent {
    private final PyFormatSpecComponentKind myKind;
    private final String myText;
    private final int myStartOffset;

    public PyFormatSpecComponent(PyFormatSpecComponentKind kind, String text, int startOffset) {
        myKind = kind;
        myText = text;
        myStartOffset = startOffset;
    }

    public PyFormatSpecComponentKind getKind() {
        return myKind;
    }

    public String getText() {
        return myText;
    }

    public int getStartOffset() {
        return myStartOffset;
    }

    public int getEndOffset() {
        return myStartOffset + myText.length();
    }

    @Override
    public String toString() {
        return myKind + "('" + myText + "'@" + myStartOffset + ")";
    }
}
