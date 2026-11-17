// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.file.FileViewProvider;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.virtualFileSystem.fileType.FileType;

public class PyDocstringCodeBlockFile extends PyFileImpl implements PyExpressionCodeFragment {
    public PyDocstringCodeBlockFile(FileViewProvider viewProvider) {
        super(viewProvider, PyDocstringCodeBlockLanguageDialect.getInstance());
    }

    @Override
    public FileType getFileType() {
        return PyDocstringCodeBlockFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "DocstringCodeBlockFile:" + getName();
    }

    @Override
    @RequiredReadAction
    public LanguageLevel getLanguageLevel() {
        InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(getProject());
        PsiLanguageInjectionHost host = languageManager.getInjectionHost(this);
        if (host != null) {
            return LanguageLevel.forElement(host.getContainingFile());
        }
        return PyExpressionCodeFragment.super.getLanguageLevel();
    }
}
