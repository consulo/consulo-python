// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.PyRemoveStatementQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Checks that no value is returned from __init__().
 */
@ExtensionImpl
public final class PyReturnFromInitInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameInitReturn();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonClassStructure();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    public static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyFunction(PyFunction function) {
            if (PyUtil.isInitMethod(function)) {
                Collection<PsiElement> offenders = new ArrayList<>();
                findReturnValueInside(function, offenders);
                for (PsiElement offender : offenders) {
                    registerProblem(offender, PyLocalize.inspCantReturnValueFromInit().get(), new PyRemoveStatementQuickFix());
                }
            }
        }

        private static void findReturnValueInside(PsiElement node, Collection<PsiElement> offenders) {
            for (PsiElement child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof PyFunction || child instanceof PyClass) {
                    continue; // ignore possible inner functions and classes
                }
                if (child instanceof PyReturnStatement) {
                    if (((PyReturnStatement)child).getExpression() != null) {
                        offenders.add(child);
                    }
                }
                findReturnValueInside(child, offenders);
            }
        }
    }
}
