// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.virtualFileSystem.fileType.FileType;
import consulo.project.Project;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiElement;
import consulo.language.impl.file.SingleRootFileViewProvider;
import consulo.language.psi.PsiManager;
import consulo.language.impl.ast.FileElement;
import consulo.language.file.light.LightVirtualFile;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyExpressionCodeFragment;

/**
 * clone of JSExpressionCodeFragment
 */
public class PyExpressionCodeFragmentImpl extends PyFileImpl implements PyExpressionCodeFragment {
    private PsiElement myContext;
    private boolean myPhysical;
    private FileViewProvider myViewProvider;

    public PyExpressionCodeFragmentImpl(Project project, String name, CharSequence text, boolean isPhysical) {
        // Consulo: PsiManagerEx#getFileManager is internal API, view provider is created directly
        super(new SingleRootFileViewProvider(
            PsiManager.getInstance(project),
            new LightVirtualFile(name, getFileTypeByFileName(name), text),
            isPhysical
        ));
        myPhysical = isPhysical;
        ((SingleRootFileViewProvider)getViewProvider()).forceCachedPsi(this);
    }

    private static FileType getFileTypeByFileName(String name) {
        if (name.endsWith(".py")) {
            return PythonFileType.INSTANCE;
        }
        else {
            throw new IllegalArgumentException("unexpected file type");
        }
    }

    @Override
    protected PyExpressionCodeFragmentImpl clone() {
        PyExpressionCodeFragmentImpl clone = (PyExpressionCodeFragmentImpl)cloneImpl((FileElement)calcTreeElement().clone());
        clone.myPhysical = false;
        clone.myOriginalFile = this;
        SingleRootFileViewProvider cloneViewProvider =
            new SingleRootFileViewProvider(getManager(), new LightVirtualFile(getName(), getLanguage(), getText()), false);
        cloneViewProvider.forceCachedPsi(clone);
        clone.myViewProvider = cloneViewProvider;
        return clone;
    }

    @Override
    public PsiElement getContext() {
        return myContext != null && myContext.isValid() ? myContext : super.getContext();
    }

    @Override
    public FileViewProvider getViewProvider() {
        if (myViewProvider != null) {
            return myViewProvider;
        }
        return super.getViewProvider();
    }

    @Override
    public boolean isPhysical() {
        return myPhysical;
    }

    public void setContext(PsiElement context) {
        myContext = context;
    }
}
