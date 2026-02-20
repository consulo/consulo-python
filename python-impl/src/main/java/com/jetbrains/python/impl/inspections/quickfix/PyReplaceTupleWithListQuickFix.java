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
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

public class PyReplaceTupleWithListQuickFix implements LocalQuickFix {
    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameMakeList();
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        assert element instanceof PyAssignmentStatement;
        PyExpression[] targets = ((PyAssignmentStatement) element).getTargets();
        if (targets.length == 1 && targets[0] instanceof PySubscriptionExpression) {
            PySubscriptionExpression subscriptionExpression = (PySubscriptionExpression) targets[0];
            if (subscriptionExpression.getOperand() instanceof PyReferenceExpression) {
                PyReferenceExpression referenceExpression = (PyReferenceExpression) subscriptionExpression.getOperand();
                TypeEvalContext context = TypeEvalContext.userInitiated(project, element.getContainingFile());
                PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
                element = referenceExpression.followAssignmentsChain(resolveContext).getElement();
                if (element instanceof PyParenthesizedExpression) {
                    PyExpression expression = ((PyParenthesizedExpression) element).getContainedExpression();
                    replaceWithListLiteral(element, (PyTupleExpression) expression);
                }
                else if (element instanceof PyTupleExpression) {
                    replaceWithListLiteral(element, (PyTupleExpression) element);
                }
            }
        }
    }

    private static void replaceWithListLiteral(PsiElement element, PyTupleExpression expression) {
        String expressionText = expression.isEmpty() ? "" : expression.getText();
        PyExpression literal = PyElementGenerator.getInstance(element.getProject()).
            createExpressionFromText(LanguageLevel.forElement(element), "[" + expressionText + "]");
        element.replace(literal);
    }
}
