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

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.inspections.quickfix.AugmentedAssignmentQuickFix;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.TokenSet;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Inspection to detect assignments that can be replaced with augmented assignments.
 *
 * @author catherine
 */
@ExtensionImpl
public class PyAugmentAssignmentInspection extends PyInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameAugmentAssignment();
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
        public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
            final PyExpression value = node.getAssignedValue();
            if (value instanceof PyBinaryExpression) {
                final PyExpression target = node.getLeftHandSideExpression();
                final PyBinaryExpression expression = (PyBinaryExpression) value;
                PyExpression leftExpression = expression.getLeftExpression();
                PyExpression rightExpression = expression.getRightExpression();

                if (rightExpression instanceof PyParenthesizedExpression) {
                    rightExpression = ((PyParenthesizedExpression) rightExpression).getContainedExpression();
                }
                if (rightExpression == null || target == null) {
                    return;
                }
                boolean changedParts = false;
                final String targetText = target.getText();
                final String rightText = rightExpression.getText();
                if (rightText.equals(targetText)) {
                    final PyExpression tmp = rightExpression;
                    rightExpression = leftExpression;
                    leftExpression = tmp;
                    changedParts = true;
                }

                final PyElementType op = expression.getOperator();
                final TokenSet operations = TokenSet.create(PyTokenTypes.PLUS, PyTokenTypes.MINUS, PyTokenTypes.MULT,
                    PyTokenTypes.FLOORDIV, PyTokenTypes.DIV, PyTokenTypes.PERC, PyTokenTypes.AND,
                    PyTokenTypes.OR, PyTokenTypes.XOR, PyTokenTypes.LTLT, PyTokenTypes.GTGT,
                    PyTokenTypes.EXP
                );
                final TokenSet commutativeOperations =
                    TokenSet.create(PyTokenTypes.PLUS, PyTokenTypes.MULT, PyTokenTypes.OR, PyTokenTypes.AND);
                if ((operations.contains(op) && !changedParts) || (changedParts && commutativeOperations.contains(op))) {
                    if (leftExpression.getText()
                        .equals(targetText) && (leftExpression instanceof PyReferenceExpression || leftExpression instanceof PySubscriptionExpression)) {
                        final PyType type = myTypeEvalContext.getType(rightExpression);
                        if (type != null && !PyTypeChecker.isUnknown(type)) {
                            final PyBuiltinCache cache = PyBuiltinCache.getInstance(rightExpression);
                            final LanguageLevel languageLevel = LanguageLevel.forElement(rightExpression);
                            if (isNumeric(type, cache) || (isString(type, cache, languageLevel) && !changedParts)) {
                                registerProblem(
                                    node,
                                    "Assignment can be replaced with augmented assignment",
                                    new AugmentedAssignmentQuickFix()
                                );
                            }
                        }
                    }
                }
            }
        }

        private boolean isString(PyType type, PyBuiltinCache cache, LanguageLevel level) {
            return PyTypeChecker.match(cache.getStringType(level), type, myTypeEvalContext);
        }

        private boolean isNumeric(PyType type, PyBuiltinCache cache) {
            return PyTypeChecker.match(cache.getComplexType(), type, myTypeEvalContext);
        }
    }
}
