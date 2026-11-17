// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.introduce;

import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.refactoring.introduce.inplace.InplaceVariableIntroducer;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

import static com.jetbrains.python.impl.refactoring.introduce.IntroduceHandler.findOccurrenceUnderCaret;

public final class PyIntroduceHandlerUi {

    @RequiredUIAccess
    public static void performInplaceIntroduceVariable(IntroduceOperation operation, PsiElement statement) {
        if (statement instanceof PyAssignmentStatement) {
            PyTargetExpression target = (PyTargetExpression) ((PyAssignmentStatement) statement).getTargets()[0];
            List<PsiElement> occurrences = operation.getOccurrences();
            PsiElement occurrence = findOccurrenceUnderCaret(occurrences, operation.getEditor());
            PsiElement elementForCaret = occurrence != null ? occurrence : target;
            operation.getEditor().getCaretModel().moveToOffset(elementForCaret.getTextRange().getStartOffset());
            InplaceVariableIntroducer<PsiElement> introducer =
                new PyInplaceVariableIntroducer(target, operation, occurrences);
            introducer.performInplaceRefactoring(new LinkedHashSet<>(operation.getSuggestedNames()));
        }
    }

    @RequiredUIAccess
    public static void performIntroduceWithDialog(
        IntroduceOperation operation,
        LocalizeValue dialogTitle,
        IntroduceValidator validator,
        String id,
        Consumer<? super IntroduceOperation> performRefactoringCallback
    ) {
        Project project = operation.getProject();
        if (operation.getName() == null) {
            PyIntroduceDialog dialog = new PyIntroduceDialog(project, dialogTitle.get(), validator, id, operation);
            if (!dialog.showAndGet()) {
                return;
            }
            operation.setName(dialog.getName());
            operation.setReplaceAll(dialog.doReplaceAllOccurrences());
            operation.setInitPlace(dialog.getInitPlace());
        }

        performRefactoringCallback.accept(operation);
    }

    private static class PyInplaceVariableIntroducer extends InplaceVariableIntroducer<PsiElement> {
        private final PyTargetExpression myTarget;

        @RequiredUIAccess
        PyInplaceVariableIntroducer(
            PyTargetExpression target,
            IntroduceOperation operation,
            List<PsiElement> occurrences
        ) {
            // TODO consulo: JB uses PyRefactoringBundle "python.introduce.variable.refactoring.name"; no localize key yet
            super(target, operation.getEditor(), operation.getProject(), "Introduce Variable",
                occurrences.toArray(PsiElement.EMPTY_ARRAY), null);
            myTarget = target;
        }

        @Override
        @RequiredReadAction
        protected PsiElement checkLocalScope() {
            return myTarget.getContainingFile();
        }
    }
}
