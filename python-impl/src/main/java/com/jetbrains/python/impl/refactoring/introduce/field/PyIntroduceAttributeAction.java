// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.introduce.field;

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
 * Python flavor of the platform "Introduce Field" action. In Python class members are attributes, so the action is
 * labeled "Introduce Attribute" (see {@link PyRefactoringProvider} which no longer exposes the platform introduce-field handler).
 */
@ActionImpl(
    id = "PyIntroduceAttribute",
    parents = {
        @ActionParentRef(
            value = @ActionRef(id = "IntroduceActionsGroup"),
            anchor = ActionRefAnchor.AFTER,
            relatedToAction = @ActionRef(id = "IntroduceField")
        )
    },
    shortcutFrom = @ActionRef(id = "IntroduceField")
)
public class PyIntroduceAttributeAction extends BasePlatformRefactoringAction {
    public PyIntroduceAttributeAction() {
        super(PyLocalize.actionPyintroduceattributeText(), PyLocalize.actionPyintroduceattributeDescription());
    }

    @Override
    protected @Nullable RefactoringActionHandler getRefactoringHandler(RefactoringSupportProvider provider) {
        return provider instanceof PyRefactoringProvider ? new PyIntroduceFieldHandler() : null;
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
