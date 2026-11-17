// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.PyAddExceptionSuperClassQuickFix;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ExtensionImpl
public final class PyExceptionInheritInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameExceptionNotInherit();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonExceptions();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyRaiseStatement(PyRaiseStatement node) {
            PyExpression[] expressions = node.getExpressions();
            if (expressions.length == 0) {
                return;
            }
            PyExpression expression = expressions[0];
            if (expression instanceof PyCallExpression) {
                PyExpression callee = ((PyCallExpression)expression).getCallee();
                if (callee instanceof PyReferenceExpression) {
                    PsiPolyVariantReference reference = ((PyReferenceExpression)callee).getReference(getResolveContext());
                    PsiElement psiElement = reference.resolve();
                    if (psiElement instanceof PyClass aClass) {
                        List<PyClassLikeType> selfAndAncestorTypes = ContainerUtil.prepend(aClass.getAncestorTypes(myTypeEvalContext),
                                                                                           aClass.getType(myTypeEvalContext));
                        for (PyClassLikeType type : selfAndAncestorTypes) {
                            if (type == null) {
                                return;
                            }
                            String name = type.getName();
                            if (name == null || "BaseException".equals(name)) {
                                return;
                            }
                        }
                        registerProblem(expression,
                                        PyLocalize.inspExceptionInheritanceExceptionDoesNotInheritFromBaseExceptionClass().get(),
                                        new PyAddExceptionSuperClassQuickFix());
                    }
                }
            }
        }
    }
}
