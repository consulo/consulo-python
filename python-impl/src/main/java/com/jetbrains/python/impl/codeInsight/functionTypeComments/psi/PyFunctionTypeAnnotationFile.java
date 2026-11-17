// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.functionTypeComments.psi;

import com.jetbrains.python.impl.codeInsight.functionTypeComments.PyFunctionTypeAnnotationDialect;
import com.jetbrains.python.impl.codeInsight.functionTypeComments.PyFunctionTypeAnnotationFileType;
import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import consulo.language.file.FileViewProvider;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.impl.file.SingleRootFileViewProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class PyFunctionTypeAnnotationFile extends PyFileImpl implements PyExpressionCodeFragment {
    private final @Nullable PsiElement myContext;

    public PyFunctionTypeAnnotationFile(FileViewProvider viewProvider) {
        super(viewProvider, PyFunctionTypeAnnotationDialect.INSTANCE);
        myContext = null;
    }

    public PyFunctionTypeAnnotationFile(String text, PsiElement context) {
        // Consulo: PsiManagerEx#getFileManager is internal API, view provider is created directly
        super(new SingleRootFileViewProvider(PsiManager.getInstance(context.getProject()),
            new LightVirtualFile("foo.bar", PyFunctionTypeAnnotationFileType.INSTANCE, text),
            false));
        myContext = context;
    }

    @Override
    public FileType getFileType() {
        return PyFunctionTypeAnnotationFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "FunctionTypeComment:" + getName();
    }

    @Override
    public LanguageLevel getLanguageLevel() {
        // The same as for .pyi files
        return LanguageLevel.getLatest();
    }

    @Override
    public PsiElement getContext() {
        return myContext != null && myContext.isValid() ? myContext : super.getContext();
    }

    public @Nullable PyFunctionTypeAnnotation getAnnotation() {
        return findChildByClass(PyFunctionTypeAnnotation.class);
    }
}
