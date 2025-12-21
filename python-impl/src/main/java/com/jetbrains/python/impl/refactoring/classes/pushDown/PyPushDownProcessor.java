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
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMembersRefactoringBaseProcessor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.usage.localize.UsageLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownProcessor extends PyMembersRefactoringBaseProcessor {
    private static final LocalizeValue HEADER = RefactoringLocalize.pushDownMembersElementsHeader();

    public PyPushDownProcessor(
        @Nonnull Project project,
        @Nonnull Collection<PyMemberInfo<PyElement>> membersToMove,
        @Nonnull PyClass from
    ) {
        super(project, membersToMove, from, getChildren(from));
    }

    @Nonnull
    private static PyClass[] getChildren(@Nonnull PyClass from) {
        Collection<PyClass> all = getInheritors(from);
        return all.toArray(new PyClass[all.size()]);
    }

    /**
     * @param from class to check for inheritors
     * @return inheritors of class
     */
    @Nonnull
    static Collection<PyClass> getInheritors(@Nonnull PyClass from) {
        return PyClassInheritorsSearch.search(from, false).findAll();
    }

    @Override
    public String getProcessedElementsHeader() {
        return HEADER.get();
    }

    @Override
    public String getCodeReferencesText(int usagesCount, int filesCount) {
        return RefactoringLocalize.classesToPushDownMembersTo(
            " (" + UsageLocalize.occurenceInfoReference(usagesCount, filesCount) + ")"
        ).get();
    }

    @Nullable
    @Override
    public String getCommentReferencesText(int usagesCount, int filesCount) {
        return null;
    }

    @Nonnull
    @Override
    protected LocalizeValue getCommandName() {
        return PyPushDownHandler.REFACTORING_NAME;
    }

    @Nullable
    @Override
    protected String getRefactoringId() {
        return "refactoring.python.push.down";
    }
}
