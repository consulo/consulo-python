// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.quickfix.MoveFromFutureImportQuickFix;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ExtensionImpl
public final class PyFromFutureImportInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameFromFutureImport();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
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
        public void visitPyFromImportStatement(PyFromImportStatement node) {
            PyReferenceExpression importSource = node.getImportSource();
            if (importSource != null && PyNames.FUTURE_MODULE.equals(importSource.getName())) {
                PsiFile file = importSource.getContainingFile();
                if (file instanceof PyFile) {
                    List<PyStatement> statementList = ((PyFile)file).getStatements();
                    boolean skippedDocString = false;
                    for (PyStatement statement : statementList) {
                        if (statement instanceof PyExpressionStatement &&
                            ((PyExpressionStatement)statement).getExpression() instanceof PyStringLiteralExpression &&
                            !skippedDocString) {
                            skippedDocString = true;
                            continue;
                        }
                        if (statement instanceof PyFromImportStatement) {
                            if (statement == node) {
                                return;
                            }
                            PyReferenceExpression source = ((PyFromImportStatement)statement).getImportSource();
                            if (source != null && PyNames.FUTURE_MODULE.equals(source.getName())) {
                                continue;
                            }
                        }
                        registerProblem(node, PyLocalize.inspFromFutureImportFromFutureImportsMustOccurAtBeginningFile().get(),
                                        new MoveFromFutureImportQuickFix());
                        return;
                    }
                }
            }
        }
    }
}
