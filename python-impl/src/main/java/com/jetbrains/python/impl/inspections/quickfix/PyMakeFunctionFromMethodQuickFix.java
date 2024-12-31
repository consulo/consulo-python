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

import java.util.Collections;
import java.util.List;

import jakarta.annotation.Nonnull;

import com.jetbrains.python.impl.psi.PyUtil;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.usage.UsageInfo;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.impl.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;

/**
 * User: ktisha
 */
public class PyMakeFunctionFromMethodQuickFix implements LocalQuickFix
{
	public PyMakeFunctionFromMethodQuickFix()
	{
	}

	@Nonnull
	public String getFamilyName()
	{
		return PyBundle.message("QFIX.NAME.make.function");
	}

	public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor)
	{
		final PsiElement element = descriptor.getPsiElement();
		final PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
		if(problemFunction == null)
		{
			return;
		}
		final PyClass containingClass = problemFunction.getContainingClass();
		if(containingClass == null)
		{
			return;
		}

		final List<UsageInfo> usages = PyRefactoringUtil.findUsages(problemFunction, false);
		final PyParameter[] parameters = problemFunction.getParameterList().getParameters();
		if(parameters.length > 0)
		{
			parameters[0].delete();
		}

		PsiElement copy = problemFunction.copy();
		problemFunction.delete();
		final PsiElement parent = containingClass.getParent();
		PyClass aClass = PsiTreeUtil.getTopmostParentOfType(containingClass, PyClass.class);
		if(aClass == null)
		{
			aClass = containingClass;
		}
		copy = parent.addBefore(copy, aClass);

		for(UsageInfo usage : usages)
		{
			final PsiElement usageElement = usage.getElement();
			if(usageElement instanceof PyReferenceExpression)
			{
				final PsiFile usageFile = usageElement.getContainingFile();
				updateUsage(copy, (PyReferenceExpression) usageElement, usageFile, !usageFile.equals(parent));
			}
		}
	}

	private static void updateUsage(@Nonnull final PsiElement finalElement, @Nonnull final PyReferenceExpression element, @Nonnull final PsiFile usageFile, boolean addImport)
	{
		final PyExpression qualifier = element.getQualifier();
		if(qualifier == null)
		{
			return;
		}
		if(qualifier.getText().equals(PyNames.CANONICAL_SELF))
		{
			PyUtil.removeQualifier(element);
			return;
		}
		if(qualifier instanceof PyCallExpression)
		{              // remove qualifier A().m()
			if(addImport)
			{
				AddImportHelper.addImport((PsiNamedElement) finalElement, usageFile, element);
			}

			PyUtil.removeQualifier(element);
			removeFormerImport(usageFile, addImport);
		}
		else
		{
			final PsiReference reference = qualifier.getReference();
			if(reference == null)
			{
				return;
			}

			final PsiElement resolved = reference.resolve();
			if(resolved instanceof PyTargetExpression)
			{  // qualifier came from assignment  a = A(); a.m()
				updateAssignment(element, resolved);
			}
			else if(resolved instanceof PyClass)
			{     //call with first instance argument A.m(A())
				PyUtil.removeQualifier(element);
				updateArgumentList(element);
			}
		}
	}

	private static void removeFormerImport(@Nonnull final PsiFile usageFile, boolean addImport)
	{
		if(usageFile instanceof PyFile && addImport)
		{
			final LocalInspectionToolSession session = new LocalInspectionToolSession(usageFile, 0, usageFile.getTextLength());
			final PyUnresolvedReferencesInspection.Visitor visitor = new PyUnresolvedReferencesInspection.Visitor(null, session, Collections.<String>emptyList());
			usageFile.accept(new PyRecursiveElementVisitor()
			{
				@Override
				public void visitPyElement(PyElement node)
				{
					super.visitPyElement(node);
					node.accept(visitor);
				}
			});

			visitor.optimizeImports();
		}
	}

	private static void updateAssignment(PyReferenceExpression element, @Nonnull final PsiElement resolved)
	{
		final PsiElement parent = resolved.getParent();
		if(parent instanceof PyAssignmentStatement)
		{
			final PyExpression value = ((PyAssignmentStatement) parent).getAssignedValue();
			if(value instanceof PyCallExpression)
			{
				final PyExpression callee = ((PyCallExpression) value).getCallee();
				if(callee instanceof PyReferenceExpression)
				{
					final PyExpression calleeQualifier = ((PyReferenceExpression) callee).getQualifier();
					if(calleeQualifier != null)
					{
						value.replace(calleeQualifier);
					}
					else
					{
						PyUtil.removeQualifier(element);
					}
				}
			}
		}
	}

	private static void updateArgumentList(@Nonnull final PyReferenceExpression element)
	{
		final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
		if(callExpression == null)
		{
			return;
		}
		final PyArgumentList argumentList = callExpression.getArgumentList();
		if(argumentList == null)
		{
			return;
		}
		final PyExpression[] arguments = argumentList.getArguments();
		if(arguments.length > 0)
		{
			arguments[0].delete();
		}
	}
}
