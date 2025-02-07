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

import jakarta.annotation.Nonnull;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;

public class PyReplaceTupleWithListQuickFix implements LocalQuickFix
{
	@Nonnull
	@Override
	public String getFamilyName()
	{
		return PyBundle.message("QFIX.NAME.make.list");
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		PsiElement element = descriptor.getPsiElement();
		assert element instanceof PyAssignmentStatement;
		PyExpression[] targets = ((PyAssignmentStatement) element).getTargets();
		if(targets.length == 1 && targets[0] instanceof PySubscriptionExpression)
		{
			PySubscriptionExpression subscriptionExpression = (PySubscriptionExpression) targets[0];
			if(subscriptionExpression.getOperand() instanceof PyReferenceExpression)
			{
				PyReferenceExpression referenceExpression = (PyReferenceExpression) subscriptionExpression.getOperand();
				final TypeEvalContext context = TypeEvalContext.userInitiated(project, element.getContainingFile());
				final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
				element = referenceExpression.followAssignmentsChain(resolveContext).getElement();
				if(element instanceof PyParenthesizedExpression)
				{
					final PyExpression expression = ((PyParenthesizedExpression) element).getContainedExpression();
					replaceWithListLiteral(element, (PyTupleExpression) expression);
				}
				else if(element instanceof PyTupleExpression)
				{
					replaceWithListLiteral(element, (PyTupleExpression) element);
				}
			}
		}
	}

	private static void replaceWithListLiteral(PsiElement element, PyTupleExpression expression)
	{
		final String expressionText = expression.isEmpty() ? "" : expression.getText();
		final PyExpression literal = PyElementGenerator.getInstance(element.getProject()).
				createExpressionFromText(LanguageLevel.forElement(element), "[" + expressionText + "]");
		element.replace(literal);
	}
}
