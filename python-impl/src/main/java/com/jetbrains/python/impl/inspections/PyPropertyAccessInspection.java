// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.PyCreatePropertyQuickFix;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Maybe;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

/**
 * Checks that properties are accessed correctly.
 */
@ExtensionImpl
public final class PyPropertyAccessInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNamePropertyAccess();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonProperties();
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
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            super.visitPyReferenceExpression(node);
            checkPropertyExpression(node);
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            super.visitPyTargetExpression(node);
            checkPropertyExpression(node);
        }

        private void checkPropertyExpression(PyQualifiedExpression node) {
            PyExpression qualifier = node.getQualifier();
            if (qualifier != null) {
                PyType type = myTypeEvalContext.getType(qualifier);
                if (type instanceof PyClassType) {
                    PyClass cls = ((PyClassType)type).getPyClass();
                    String name = node.getName();
                    if (name != null) {
                        Property property = cls.findProperty(name, true, myTypeEvalContext);
                        if (property != null) {
                            AccessDirection dir = AccessDirection.of(node);
                            checkAccessor(node, name, dir, property);
                            if (dir == AccessDirection.READ) {
                                PsiElement parent = node.getParent();
                                if (parent instanceof PyAugAssignmentStatement && ((PyAugAssignmentStatement)parent).getTarget() == node) {
                                    checkAccessor(node, name, AccessDirection.WRITE, property);
                                }
                            }
                        }
                    }
                }
            }
        }

        private void checkAccessor(PyExpression node,
                                   String name,
                                   AccessDirection dir,
                                   Property property) {
            Maybe<PyCallable> accessor = property.getByDirection(dir);
            if (accessor.isDefined() && accessor.value() == null) {
                PyInspectionMessages.ProblemMessage message;
                if (dir == AccessDirection.WRITE) {
                    message = PyInspectionMessages.problemMessage(a -> PyLocalize.inspPropertyCannotBeSet(a[0]), name);
                }
                else if (dir == AccessDirection.DELETE) {
                    message = PyInspectionMessages.problemMessage(a -> PyLocalize.inspPropertyCannotBeDeleted(a[0]), name);
                }
                else {
                    message = PyInspectionMessages.problemMessage(a -> PyLocalize.inspPropertyCannotBeRead(a[0]), name);
                }
                registerProblem(node, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new PyCreatePropertyQuickFix(dir));
            }
        }
    }
}
