// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.python.impl.codeInsight.intentions;


import consulo.codeEditor.Editor;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

public abstract class PyBaseIntentionAction extends BaseIntentionAction {

    @Override
    public final void invoke(Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
        doInvoke(project, editor, psiFile);
    }

    public abstract void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException;
}
