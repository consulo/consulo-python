// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.editor;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.TokenSet;
import consulo.language.editor.action.FileQuoteHandler;
import consulo.virtualFileSystem.fileType.FileType;

@ExtensionImpl
public class PythonQuoteHandler extends BaseQuoteHandler implements FileQuoteHandler {
    public PythonQuoteHandler() {
        super(PyTokenTypes.STRING_NODES, new char[]{'}', ']', ')', ',', ':', ';', ' ', '\t', '\n'});
    }

    @Override
    protected TokenSet getOpeningQuotesTokens() {
        return TokenSet.orSet(super.getOpeningQuotesTokens(), TokenSet.create(PyTokenTypes.FSTRING_START));
    }

    @Override
    protected TokenSet getClosingQuotesTokens() {
        return TokenSet.orSet(super.getClosingQuotesTokens(), TokenSet.create(PyTokenTypes.FSTRING_END));
    }

    @Override
    protected TokenSet getLiteralContentTokens() {
        return TokenSet.orSet(super.getLiteralContentTokens(), TokenSet.create(PyTokenTypes.FSTRING_TEXT));
    }

    @Override
    public FileType getFileType() {
        return PythonFileType.INSTANCE;
    }
}
