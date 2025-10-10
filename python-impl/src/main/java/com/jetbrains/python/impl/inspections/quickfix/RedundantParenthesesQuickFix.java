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
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

/**
 * QuickFix to remove redundant parentheses from if/while/except statement
 *
 * @author catherine
 */
public class RedundantParenthesesQuickFix implements LocalQuickFix {
    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixRedundantParentheses();
    }

    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PsiElement binaryExpression = ((PyParenthesizedExpression) element).getContainedExpression();
        PyBinaryExpression parent = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class);
        if (binaryExpression instanceof PyBinaryExpression && parent != null) {
            if (!replaceBinaryExpression((PyBinaryExpression) binaryExpression)) {
                element.replace(binaryExpression);
            }
        }
        else {
            while (element instanceof PyParenthesizedExpression) {
                PyExpression expression = ((PyParenthesizedExpression) element).getContainedExpression();
                if (expression != null) {
                    element = element.replace(expression);
                }
            }
        }
    }

    private static boolean replaceBinaryExpression(PyBinaryExpression element) {
        PyExpression left = element.getLeftExpression();
        PyExpression right = element.getRightExpression();
        if (left instanceof PyParenthesizedExpression &&
            right instanceof PyParenthesizedExpression) {
            PyExpression leftContained = ((PyParenthesizedExpression) left).getContainedExpression();
            PyExpression rightContained = ((PyParenthesizedExpression) right).getContainedExpression();
            if (leftContained != null && rightContained != null) {
                left.replace(leftContained);
                right.replace(rightContained);
                return true;
            }
        }
        return false;
    }
}
