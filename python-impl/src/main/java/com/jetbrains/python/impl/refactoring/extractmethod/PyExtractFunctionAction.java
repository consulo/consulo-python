// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.extractmethod;

import com.jetbrains.python.impl.refactoring.PyRefactoringProvider;
import com.jetbrains.python.psi.PyElement;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.language.editor.refactoring.RefactoringSupportProvider;
import consulo.language.editor.refactoring.action.BasePlatformRefactoringAction;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.psi.PsiElement;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.action.AnActionEvent;
import org.jspecify.annotations.Nullable;

/**
 * Python flavor of the platform "Extract Method" action. In Python the extracted code becomes a function, so the action
 * is labeled "Extract Function" (see {@link PyRefactoringProvider} which no longer exposes the platform extract-method handler).
 */
@ActionImpl(
    id = "PyExtractFunction",
    parents = {
        @ActionParentRef(
            value = @ActionRef(id = "IntroduceActionsGroup"),
            anchor = ActionRefAnchor.AFTER,
            relatedToAction = @ActionRef(id = "ExtractMethod")
        ),
        @ActionParentRef(value = @ActionRef(id = "Floating.CodeToolbar.Extract"), anchor = ActionRefAnchor.FIRST)
    },
    shortcutFrom = @ActionRef(id = "ExtractMethod")
)
public class PyExtractFunctionAction extends BasePlatformRefactoringAction {
    public PyExtractFunctionAction() {
        super(PyLocalize.actionPyextractfunctionText(), PyLocalize.actionPyextractfunctionDescription());
    }

    @Override
    protected @Nullable RefactoringActionHandler getRefactoringHandler(RefactoringSupportProvider provider) {
        return provider instanceof PyRefactoringProvider ? new PyExtractMethodHandler() : null;
    }

    @Override
    protected boolean isAvailableInEditorOnly() {
        return true;
    }

    @Override
    @RequiredReadAction
    protected boolean isEnabledOnElements(PsiElement[] elements) {
        for (PsiElement element : elements) {
            if (!(element instanceof PyElement)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @RequiredReadAction
    protected void updateInRead(AnActionEvent e) {
        super.updateInRead(e);
        // TODO consulo: ExtractSuperActionBase.removeFirstWordInMainMenu(this, e) missing
    }
}
