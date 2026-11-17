// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types.functionalParser;

import consulo.util.lang.Pair;
import java.util.function.Function;

import java.util.List;

public interface FunctionalParser<R, T> {
    R parse(List<Token<T>> tokens) throws ParserException;

    Pair<R, FunctionalParserBase.State> parse(List<Token<T>> tokens,
                                              FunctionalParserBase.State state) throws ParserException;

    <R2> FunctionalParser<Pair<R, R2>, T> then(FunctionalParser<R2, T> parser);

    <R2> FunctionalParser<R2, T> skipThen(FunctionalParser<R2, T> parser);

    <R2> FunctionalParser<R, T> thenSkip(FunctionalParser<R2, T> parser);

    FunctionalParser<R, T> or(FunctionalParser<R, T> parser);

    <R2> FunctionalParser<R2, T> map(Function<R, R2> f);

    FunctionalParser<R, T> endOfInput();

    FunctionalParser<R, T> named(String name);

    FunctionalParser<R, T> cached();

    class State {
        private final int myPos;
        private final int myMax;
        private final Object myKey;

        State() {
            myKey = new Object();
            myPos = 0;
            myMax = 0;
        }

        State(State state, int pos, int max) {
            myKey = state.myKey;
            myPos = pos;
            myMax = max;
        }

        public int getPos() {
            return myPos;
        }

        public int getMax() {
            return myMax;
        }

        public Object getKey() {
            return myKey;
        }
    }
}
