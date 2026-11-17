// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PySuperArgumentsInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameWrongSuperArguments();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonInheritance();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        if (context.getUsesExternalTypeEngine()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new Visitor(holder, context);
    }

    private static class Visitor extends PyInspectionVisitor {

        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
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
                                      PyInspectionMessages.problemMessage(a -> PyLocalize.inspClassIsNotSubtypeOfClass(a[0], a[1]), CodifiedParam.ofReference(secondClass), CodifiedParam.ofReference(firstClass))
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }

        private @Nullable PyClass findClassOf(PyExpression argument) {
            PsiElement firstElement = ((PyReferenceExpression)argument).followAssignmentsChain(getResolveContext()).getElement();
            PyClass firstClass = null;
            if (firstElement instanceof PyClass) {
                firstClass = (PyClass)firstElement;
            }
            else if (firstElement instanceof PyExpression) {
                PyType first_type = myTypeEvalContext.getType((PyExpression)firstElement);
                if (first_type instanceof PyClassType) {
                    firstClass = ((PyClassType)first_type).getPyClass();
                }
            }
            return firstClass;
        }
    }
}
