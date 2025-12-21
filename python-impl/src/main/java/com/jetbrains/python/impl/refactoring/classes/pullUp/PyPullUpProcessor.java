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

import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMembersRefactoringBaseProcessor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * @author Ilya.Kazakevich
 */
class PyPullUpProcessor extends PyMembersRefactoringBaseProcessor {
    PyPullUpProcessor(
        @Nonnull PyClass from,
        @Nonnull PyClass to,
        @Nonnull Collection<PyMemberInfo<PyElement>> membersToMove
    ) {
        super(from.getProject(), membersToMove, from, to);
    }

    @Nonnull
    @Override
    protected LocalizeValue getCommandName() {
        return PyPullUpHandler.REFACTORING_NAME;
    }

    @Override
    public String getProcessedElementsHeader() {
        return PyLocalize.refactoringPullUpDialogMoveMembersToClass().get();
    }

    @Override
    public String getCodeReferencesText(int usagesCount, int filesCount) {
        return PyLocalize.refactoringPullUpDialogMembersToBeMoved().get();
    }

    @Nullable
    @Override
    public String getCommentReferencesText(int usagesCount, int filesCount) {
        return getCodeReferencesText(usagesCount, filesCount);
    }

    @Nullable
    @Override
    protected String getRefactoringId() {
        return "refactoring.python.pull.up";
    }
}
