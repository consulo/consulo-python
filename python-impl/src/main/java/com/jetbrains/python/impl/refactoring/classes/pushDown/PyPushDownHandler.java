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
package com.jetbrains.python.impl.refactoring.classes.pushDown;

import com.jetbrains.python.impl.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringHandler;
import com.jetbrains.python.impl.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.impl.vp.Creator;
import com.jetbrains.python.impl.vp.ViewPresenterUtils;
import com.jetbrains.python.psi.PyClass;
import consulo.application.util.query.Query;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownHandler extends PyClassRefactoringHandler {
    public static final LocalizeValue REFACTORING_NAME = RefactoringLocalize.pushMembersDownTitle();

    @Override
    @RequiredUIAccess
    protected void doRefactorImpl(
        final Project project,
        final PyClass classUnderRefactoring,
        final PyMemberInfoStorage infoStorage,
        Editor editor
    ) {
        //TODO: Move to presenter?
        Query<PyClass> query = PyClassInheritorsSearch.search(classUnderRefactoring, false);
        if (query.findFirst() == null) {
            LocalizeValue message = RefactoringLocalize.class0DoesNotHaveInheritors(classUnderRefactoring.getName());
            CommonRefactoringUtil.showErrorHint(project, editor, message, getTitle(), getHelpId());
            return;
        }

        ViewPresenterUtils.linkViewWithPresenterAndLaunch(
            PyPushDownPresenter.class,
            PyPushDownView.class,
            new Creator<>() {
                @Override
                public PyPushDownPresenter createPresenter(PyPushDownView view) {
                    return new PyPushDownPresenterImpl(project, view, classUnderRefactoring, infoStorage);
                }

                @Override
                public PyPushDownView createView(PyPushDownPresenter presenter) {
                    return new PyPushDownViewSwingImpl(classUnderRefactoring, project, presenter);
                }
            }
        );
    }

    @Override
    protected LocalizeValue getTitle() {
        return REFACTORING_NAME;
    }

    @Override
    protected String getHelpId() {
        return "members.push.down";
    }
}
