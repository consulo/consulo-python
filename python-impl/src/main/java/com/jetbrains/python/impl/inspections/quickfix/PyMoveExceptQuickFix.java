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

import java.util.ArrayList;

import jakarta.annotation.Nonnull;
import com.google.common.collect.Lists;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTryExceptStatement;
import com.jetbrains.python.psi.resolve.PyResolveContext;

public class PyMoveExceptQuickFix implements LocalQuickFix
{

	@Nonnull
	public String getFamilyName()
	{
		return PyBundle.message("QFIX.NAME.move.except.up");
	}

	public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor)
	{
		final PsiElement element = descriptor.getPsiElement();
		final PyExceptPart part = PsiTreeUtil.getParentOfType(element, PyExceptPart.class);
		if(part == null)
		{
			return;
		}
		final PyExpression exceptClassExpression = part.getExceptClass();
		if(exceptClassExpression == null)
		{
			return;
		}

		final PsiElement exceptClass = ((PyReferenceExpression) exceptClassExpression).followAssignmentsChain(PyResolveContext.noImplicits()).getElement();
		if(exceptClass instanceof PyClass)
		{
			final PyTryExceptStatement statement = PsiTreeUtil.getParentOfType(part, PyTryExceptStatement.class);
			if(statement == null)
			{
				return;
			}

			PyExceptPart prevExceptPart = PsiTreeUtil.getPrevSiblingOfType(part, PyExceptPart.class);
			final ArrayList<PyClass> superClasses = Lists.newArrayList(((PyClass) exceptClass).getSuperClasses(null));
			while(prevExceptPart != null)
			{
				final PyExpression classExpression = prevExceptPart.getExceptClass();
				if(classExpression == null)
				{
					return;
				}
				final PsiElement aClass = ((PyReferenceExpression) classExpression).followAssignmentsChain(PyResolveContext.noImplicits()).getElement();
				if(aClass instanceof PyClass)
				{
					if(superClasses.contains(aClass))
					{
						statement.addBefore(part, prevExceptPart);
						part.delete();
						return;
					}
				}
				prevExceptPart = PsiTreeUtil.getPrevSiblingOfType(prevExceptPart, PyExceptPart.class);
			}
		}
	}
}
