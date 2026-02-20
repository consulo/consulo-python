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
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PySuperArgumentsInspection extends PyInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameWrongSuperArguments();
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        Object state
    ) {
        return new Visitor(holder, session);
    }

    private static class Visitor extends PyInspectionVisitor {

        public Visitor(ProblemsHolder holder, LocalInspectionToolSession session) {
            super(holder, session);
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            PyExpression callee = node.getCallee();
            if (callee != null) {
                if (PyNames.SUPER.equals(callee.getName())) {
                    PyExpression[] arguments = node.getArguments();
                    if (arguments.length == 2) {
                        if (arguments[0] instanceof PyReferenceExpression && arguments[1] instanceof PyReferenceExpression) {
                            PyClass firstClass = findClassOf(arguments[0]);
                            PyClass secondClass = findClassOf(arguments[1]);
                            if (firstClass != null && secondClass != null) {
                                if (!secondClass.isSubclass(firstClass, myTypeEvalContext)) {
                                    registerProblem(
                                        node.getArgumentList(),
                                        PyLocalize.insp$0IsNotSuperclassOf$1(secondClass.getName(), firstClass.getName()).get()
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }

        @Nullable
        private PyClass findClassOf(PyExpression argument) {
            PsiElement firstElement = ((PyReferenceExpression) argument).followAssignmentsChain(getResolveContext()).getElement();
            PyClass firstClass = null;
            if (firstElement instanceof PyClass) {
                firstClass = (PyClass) firstElement;
            }
            else if (firstElement instanceof PyExpression) {
                PyType first_type = myTypeEvalContext.getType((PyExpression) firstElement);
                if (first_type instanceof PyClassType) {
                    firstClass = ((PyClassType) first_type).getPyClass();
                }
            }
            return firstClass;
        }
    }
}
