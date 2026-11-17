// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.lexer.PythonLexer;
import consulo.language.lexer.Lexer;
import consulo.language.psi.stub.OccurrenceConsumer;

/**
 * Top-level declarations of {@code PyIdIndexer.kt}.
 */
public final class PyIdIndexerKt {
    private PyIdIndexerKt() {
    }

    public static Lexer createPyIndexingLexer(OccurrenceConsumer consumer) {
        return new PyFilterLexer(new PythonLexer(), consumer);
    }
}
