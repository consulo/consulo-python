// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types.functionalParser;

import consulo.util.lang.Pair;

import java.util.List;

public class ForwardDeclaration<R, T> extends FunctionalParserBase<R, T> {
    private FunctionalParser<R, T> myParser = null;

    public static <R, T> ForwardDeclaration<R, T> create() {
        return new ForwardDeclaration<>();
    }

    public ForwardDeclaration<R, T> define(FunctionalParser<R, T> parser) {
        myParser = parser;
        return this;
    }

    @Override
    public Pair<R, State> parse(List<Token<T>> tokens, State state) throws ParserException {
        if (myParser != null) {
            return myParser.parse(tokens, state);
        }
        throw new IllegalStateException("Undefined forward parser declaration");
    }
}
