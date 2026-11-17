// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.PyReplaceTupleWithListQuickFix;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

@ExtensionImpl
public final class PyTupleItemAssignmentInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameTupleItemAssignment();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonAssignmentIssues();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {

        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyAssignmentStatement(PyAssignmentStatement node) {
            PyExpression[] targets = node.getTargets();
            if (targets.length == 1 && targets[0] instanceof PySubscriptionExpression subscriptionExpression) {
                if (subscriptionExpression.getOperand() instanceof PyReferenceExpression referenceExpression) {
                    PsiElement element = referenceExpression.followAssignmentsChain(getResolveContext()).getElement();
                    if (element instanceof PyExpression expression) {
                        PyType type = myTypeEvalContext.getType(expression);
                        if (type instanceof PyTupleType) {
                            registerProblem(node, PyLocalize.inspTuplesNeverAssignItems().get(), new PyReplaceTupleWithListQuickFix());
                        }
                    }
                }
            }
        }
    }
}
