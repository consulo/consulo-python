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
package com.jetbrains.python.impl.refactoring.introduce.parameter;

import java.util.LinkedHashSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.introduce.inplace.InplaceVariableIntroducer;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.refactoring.PyReplaceExpressionUtil;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.impl.refactoring.introduce.variable.VariableValidator;

/**
 * User: ktisha
 */
public class PyIntroduceParameterHandler extends IntroduceHandler
{
	public PyIntroduceParameterHandler()
	{
		super(new VariableValidator(), PyBundle.message("refactoring.introduce.parameter.dialog.title"));
	}

	@Override
	protected String getHelpId()
	{
		return "python.reference.introduceParameter";
	}

	@Nullable
	@Override
	protected PsiElement addDeclaration(@Nonnull PsiElement expression, @Nonnull PsiElement declaration, @Nonnull IntroduceOperation operation)
	{
		return doIntroduceParameter(expression, (PyAssignmentStatement) declaration);
	}


	public PsiElement doIntroduceParameter(PsiElement expression, PyAssignmentStatement declaration)
	{
		PyFunction function = PsiTreeUtil.getParentOfType(expression, PyFunction.class);
		if(function != null && declaration != null)
		{
			PyParameterList parameterList = function.getParameterList();
			parameterList.addParameter(PyElementGenerator.getInstance(function.getProject()).createParameter(declaration.getText()));
			CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);
			return parameterList.findParameterByName(declaration.getTargets()[0].getText());
		}
		return null;
	}

	@Nullable
	@Override
	protected PsiElement replaceExpression(PsiElement expression, PyExpression newExpression, IntroduceOperation operation)
	{
		return PyReplaceExpressionUtil.replaceExpression(expression, newExpression);
	}

	protected boolean isValidIntroduceContext(PsiElement element)
	{
		if(element != null)
		{
			if(!isValidPlace(element))
			{
				return false;
			}

			return isNotDeclared(element);
		}
		return false;
	}

	private static boolean isNotDeclared(PsiElement element)
	{
		final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(element);
		final boolean[] isValid = {true};

		if(scopeOwner != null)
		{
			final String name = element instanceof PsiNamedElement ? ((PsiNamedElement) element).getName() : element.getText();
			if(name != null && ControlFlowCache.getScope(scopeOwner).containsDeclaration(name))
			{
				return false;
			}
			new PyRecursiveElementVisitor()
			{
				@Override
				public void visitPyReferenceExpression(PyReferenceExpression node)
				{
					super.visitPyReferenceExpression(node);

					final String name = node.getName();
					if(name != null && ControlFlowCache.getScope(scopeOwner).containsDeclaration(name))
					{
						isValid[0] = false;
					}
				}
			}.visitElement(element);
		}
		return !isResolvedToParameter(element) && isValid[0];
	}

	private static boolean isValidPlace(PsiElement element)
	{
		final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
		final PyForPart forPart = PsiTreeUtil.getParentOfType(element, PyForPart.class);
		if(forPart != null)
		{
			final PyExpression target = forPart.getTarget();
			if(target instanceof PyTargetExpression && element.getText().equals(target.getName()))
			{
				return false;
			}
		}
		final PyStatement nonlocalStatement = PsiTreeUtil.getParentOfType(element, PyNonlocalStatement.class, PyGlobalStatement.class);
		final PyStatementList statementList = PsiTreeUtil.getParentOfType(element, PyStatementList.class);
		PyImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PyImportStatement.class);
		return nonlocalStatement == null && importStatement == null &&
				statementList != null && function != null;
	}

	private static boolean isResolvedToParameter(PsiElement element)
	{
		while(element instanceof PyReferenceExpression)
		{
			final PsiReference reference = element.getReference();
			if(reference != null && reference.resolve() instanceof PyNamedParameter)
			{
				return true;
			}
			element = ((PyReferenceExpression) element).getQualifier();
		}
		return false;
	}

	@Override
	protected void performInplaceIntroduce(IntroduceOperation operation)
	{
		final PsiElement statement = performRefactoring(operation);
		if(statement instanceof PyNamedParameter)
		{
			final List<PsiElement> occurrences = operation.getOccurrences();
			final PsiElement occurrence = findOccurrenceUnderCaret(occurrences, operation.getEditor());
			PsiElement elementForCaret = occurrence != null ? occurrence : statement;
			operation.getEditor().getCaretModel().moveToOffset(elementForCaret.getTextRange().getStartOffset());
			final InplaceVariableIntroducer<PsiElement> introducer = new PyInplaceParameterIntroducer((PyNamedParameter) statement, operation, occurrences);
			introducer.performInplaceRefactoring(new LinkedHashSet<>(operation.getSuggestedNames()));
		}
	}

	private static class PyInplaceParameterIntroducer extends InplaceVariableIntroducer<PsiElement>
	{
		private final PyNamedParameter myTarget;

		public PyInplaceParameterIntroducer(PyNamedParameter target, IntroduceOperation operation, List<PsiElement> occurrences)
		{
			super(target, operation.getEditor(), operation.getProject(), "Introduce Parameter", occurrences.toArray(new PsiElement[occurrences.size()]), null);
			myTarget = target;
		}

		@Override
		protected PsiElement checkLocalScope()
		{
			return myTarget.getContainingFile();
		}
	}

	@Override
	protected String getRefactoringId()
	{
		return "refactoring.python.introduce.parameter";
	}
}
