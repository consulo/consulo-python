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
package com.jetbrains.python.impl.refactoring.classes.pullUp;

import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringHandler;
import com.jetbrains.python.impl.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.impl.vp.Creator;
import com.jetbrains.python.impl.vp.ViewPresenterUtils;
import com.jetbrains.python.psi.PyClass;
import consulo.codeEditor.Editor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

/**
 * @author Dennis.Ushakov
 */
public class PyPullUpHandler extends PyClassRefactoringHandler {
    public static final LocalizeValue REFACTORING_NAME = PyLocalize.refactoringPullUpDialogTitle();

    @Override
    protected void doRefactorImpl(
        @Nonnull final Project project,
        @Nonnull final PyClass classUnderRefactoring,
        @Nonnull final PyMemberInfoStorage infoStorage,
        @Nonnull Editor editor
    ) {
        //TODO: Move to vp (presenter) as well
        final PyPullUpNothingToRefactorMessage nothingToRefactor =
            new PyPullUpNothingToRefactorMessage(project, editor, classUnderRefactoring);

        if (PyAncestorsUtils.getAncestorsUnderUserControl(classUnderRefactoring).isEmpty()) {
            nothingToRefactor.showNothingToRefactor();
            return;
        }


        ViewPresenterUtils.linkViewWithPresenterAndLaunch(
            PyPullUpPresenter.class,
            PyPullUpView.class,
            new Creator<>() {
                @Nonnull
                @Override
                public PyPullUpPresenter createPresenter(@Nonnull PyPullUpView view) {
                    return new PyPullUpPresenterImpl(view, infoStorage, classUnderRefactoring);
                }

                @Nonnull
                @Override
                public PyPullUpView createView(@Nonnull PyPullUpPresenter presenter) {
                    return new PyPullUpViewSwingImpl(project, presenter, classUnderRefactoring, nothingToRefactor);
                }
            }
        );
    }

    @Nonnull
    @Override
    protected LocalizeValue getTitle() {
        return REFACTORING_NAME;
    }

    @Override
    protected String getHelpId() {
        return "refactoring.pullMembersUp";
    }
}
