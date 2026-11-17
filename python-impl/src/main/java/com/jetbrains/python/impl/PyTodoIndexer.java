// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.PythonFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.lexer.Lexer;
import consulo.language.psi.stub.OccurrenceConsumer;
import consulo.language.psi.stub.todo.LexerBasedTodoIndexer;
import consulo.virtualFileSystem.fileType.FileType;

@ExtensionImpl
public final class PyTodoIndexer extends LexerBasedTodoIndexer {
    @Override
    public Lexer createLexer(OccurrenceConsumer consumer) {
        return PyIdIndexerKt.createPyIndexingLexer(consumer);
    }

    @Override
    public FileType getFileType() {
        return PythonFileType.INSTANCE;
    }
}
