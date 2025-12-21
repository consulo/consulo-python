/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.refactoring.classes;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyClass;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.language.editor.refactoring.ElementsHandler;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Dennis.Ushakov
 */
public abstract class PyClassRefactoringHandler implements RefactoringActionHandler, ElementsHandler {
    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        PsiElement element1 = null;
        PsiElement element2 = null;
        SelectionModel selectionModel = editor.getSelectionModel();
        if (selectionModel.hasSelection()) {
            element1 = file.findElementAt(selectionModel.getSelectionStart());
            element2 = file.findElementAt(selectionModel.getSelectionEnd() - 1);
        }
        else {
            CaretModel caretModel = editor.getCaretModel();
            Document document = editor.getDocument();
            int lineNumber = document.getLineNumber(caretModel.getOffset());
            if (lineNumber >= 0 && lineNumber < document.getLineCount()) {
                element1 = file.findElementAt(document.getLineStartOffset(lineNumber));
                element2 = file.findElementAt(document.getLineEndOffset(lineNumber) - 1);
            }
        }
        if (element1 == null || element2 == null) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                PyLocalize.refactoringIntroduceSelectionError(),
                getTitle(),
                "members.pull.up"
            );
            return;
        }
        doRefactor(project, element1, element2, editor, file);
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        PsiFile file = dataContext.getRequiredData(PsiFile.KEY);
        Editor editor = dataContext.getData(Editor.KEY);
        doRefactor(project, elements[0], elements[elements.length - 1], editor, file);
    }

    @RequiredUIAccess
    private void doRefactor(
        @Nonnull Project project,
        PsiElement element1,
        PsiElement element2,
        Editor editor,
        @Nonnull PsiFile file
    ) {
        CommonRefactoringUtil.checkReadOnlyStatus(project, file);

        PyClass clazz = PyUtil.getContainingClassOrSelf(element1);
        if (!inClass(clazz, project, editor, PyLocalize.refactoringPullUpErrorCannotPerformRefactoringNotInsideClass())) {
            return;
        }
        assert clazz != null;

        PyMemberInfoStorage infoStorage = PyMembersRefactoringSupport.getSelectedMemberInfos(clazz, element1, element2);

        doRefactorImpl(project, clazz, infoStorage, editor);
    }

    protected abstract void doRefactorImpl(
        @Nonnull Project project,
        @Nonnull PyClass classUnderRefactoring,
        @Nonnull PyMemberInfoStorage infoStorage,
        @Nonnull Editor editor
    );

    @RequiredUIAccess
    protected boolean inClass(@Nullable PyClass clazz, @Nonnull Project project, Editor editor, LocalizeValue errorMessage) {
        if (clazz == null) {
            CommonRefactoringUtil.showErrorHint(project, editor, errorMessage, getTitle(), getHelpId());
            return false;
        }
        return true;
    }

    @Nonnull
    protected abstract LocalizeValue getTitle();

    protected abstract String getHelpId();

    @Override
    public boolean isEnabledOnElements(PsiElement[] elements) {
        return elements.length == 1 && elements[0] instanceof PyClass;
    }
}
