/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.impl.inspections;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;

/**
 * @author oleg
 */
@ExtensionImpl
public class PyUnusedLocalInspection extends PyInspection {
    private static Key<PyUnusedLocalInspectionVisitor> KEY = Key.create("PyUnusedLocal.Visitor");

    @Nonnull
    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyUnusedLocalInspectionState();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameUnused();
    }

    @Override
    @Nonnull
    public PsiElementVisitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        final boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        Object state
    ) {
        PyUnusedLocalInspectionState inspectionState = (PyUnusedLocalInspectionState) state;

        final PyUnusedLocalInspectionVisitor visitor = new PyUnusedLocalInspectionVisitor(
            holder,
            session,
            inspectionState.ignoreTupleUnpacking,
            inspectionState.ignoreLambdaParameters,
            inspectionState.ignoreLoopIterationVariables
        );
        // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
        final PyUnusedLocalInspectionVisitor existingVisitor = session.getUserData(KEY);
        if (existingVisitor == null) {
            session.putUserData(KEY, visitor);
        }
        return visitor;
    }

    @Override
    public void inspectionFinished(@Nonnull LocalInspectionToolSession session, @Nonnull ProblemsHolder holder, Object state) {
        final PyUnusedLocalInspectionVisitor visitor = session.getUserData(KEY);
        if (visitor != null) {
            visitor.registerProblems();
            session.putUserData(KEY, null);
        }
    }
}
