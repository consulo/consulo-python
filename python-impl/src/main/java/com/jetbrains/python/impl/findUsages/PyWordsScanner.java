// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.findUsages;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonLexer;
import consulo.language.ast.TokenSet;
import consulo.language.cacheBuilder.DefaultWordsScanner;

// TODO consulo: VersionedWordsScanner missing - JB extends VersionedWordsScanner, lazily creates the DefaultWordsScanner delegate
//  in processWords() and returns super.getVersion() + 1 from getVersion()
public class PyWordsScanner extends DefaultWordsScanner {
    public PyWordsScanner() {
        super(new PythonLexer(),
            TokenSet.create(PyTokenTypes.IDENTIFIER),
            TokenSet.create(PyTokenTypes.END_OF_LINE_COMMENT),
            PyTokenTypes.STRING_NODES);
    }
}
