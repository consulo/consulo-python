// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types.functionalParser;


public final class ParserException extends Exception {
    private final FunctionalParserBase.State myState;

    public ParserException(String message, FunctionalParserBase.State state) {
        super(message);
        myState = state;
    }

    FunctionalParserBase.State getState() {
        return myState;
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
