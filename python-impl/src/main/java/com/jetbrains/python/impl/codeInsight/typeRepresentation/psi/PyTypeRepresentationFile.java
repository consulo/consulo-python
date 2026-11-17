// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeRepresentation.psi;

import com.jetbrains.python.impl.codeInsight.typeRepresentation.PyTypeRepresentationDialect;
import com.jetbrains.python.impl.codeInsight.typeRepresentation.PyTypeRepresentationFileType;
import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import consulo.language.file.FileViewProvider;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.impl.file.SingleRootFileViewProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;

public class PyTypeRepresentationFile extends PyFileImpl implements PyExpressionCodeFragment {
    private final @Nullable PsiElement myContext;

    public PyTypeRepresentationFile(String text, PsiElement context) {
        // Consulo: PsiManagerEx#getFileManager is internal API, view provider is created directly
        super(new SingleRootFileViewProvider(PsiManager.getInstance(context.getProject()),
            new LightVirtualFile("foo.bar", PyTypeRepresentationFileType.INSTANCE, text),
            false));
        myContext = context;
    }

    public PyTypeRepresentationFile(FileViewProvider viewProvider) {
        super(viewProvider, PyTypeRepresentationDialect.INSTANCE);
        myContext = null;
    }

    @Override
    public FileType getFileType() {
        return PyTypeRepresentationFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "TypeRepresentation:" + getName();
    }

    @Override
    public LanguageLevel getLanguageLevel() {
        // The same as for .pyi files
        return LanguageLevel.getLatest();
    }

    @Override
    public @Nullable PsiElement getContext() {
        return myContext != null && myContext.isValid() ? myContext : super.getContext();
    }

    public @Nullable PyExpression getType() {
        return findChildByClass(PyExpression.class);
    }
}
