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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.quickfix.MoveFromFutureImportQuickFix;
import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PyFromFutureImportInspection extends PyInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameFromFutureImport();
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
        public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
            super(holder, session);
        }

        @Override
        public void visitPyFromImportStatement(PyFromImportStatement node) {
            PyReferenceExpression importSource = node.getImportSource();
            if (importSource != null && PyNames.FUTURE_MODULE.equals(importSource.getName())) {
                PsiFile file = importSource.getContainingFile();
                if (file instanceof PyFile) {
                    List<PyStatement> statementList = ((PyFile) file).getStatements();
                    boolean skippedDocString = false;
                    for (PyStatement statement : statementList) {
                        if (statement instanceof PyExpressionStatement &&
                            ((PyExpressionStatement) statement).getExpression() instanceof PyStringLiteralExpression &&
                            !skippedDocString) {
                            skippedDocString = true;
                            continue;
                        }
                        if (statement instanceof PyFromImportStatement) {
                            if (statement == node) {
                                return;
                            }
                            PyReferenceExpression source = ((PyFromImportStatement) statement).getImportSource();
                            if (source != null && PyNames.FUTURE_MODULE.equals(source.getName())) {
                                continue;
                            }
                        }
                        registerProblem(node, "from __future__ imports must occur at the beginning of the file",
                            new MoveFromFutureImportQuickFix()
                        );
                        return;
                    }
                }
            }
        }
    }
}
