// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types.functionalParser;

import consulo.document.util.TextRange;

public class Token<T> {
    private final CharSequence myText;
    private final TextRange myRange;
    private final T myType;

    public Token(T type, CharSequence text, TextRange range) {
        myText = text;
        myRange = range;
        myType = type;
    }

    public T getType() {
        return myType;
    }

    public CharSequence getText() {
        return myText;
    }

    public TextRange getRange() {
        return myRange;
    }

    @Override
    public String toString() {
        return String.format("Token(<%s>, \"%s\", %s)", myType, myText, myRange);
    }
}
