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

import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * pylint W0601
 *
 * @author ktisha
 */
@ExtensionImpl
public class PyGlobalUndefinedInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameGlobalUndefined();
    }

    @Override
    public PsiElementVisitor buildVisitor(
        ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        Object state
    ) {
        return new Visitor(holder, session);
    }


    private static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, LocalInspectionToolSession session) {
            super(holder, session);
        }

        @Override
        public void visitPyGlobalStatement(PyGlobalStatement node) {
            PyTargetExpression[] globals = node.getGlobals();

            for (PyTargetExpression global : globals) {
                if (global.getReference().resolve() == global) {
                    registerProblem(global, PyLocalize.inspNameGlobal$0Undefined(global.getName()).get());
                }
            }
        }
    }
}
