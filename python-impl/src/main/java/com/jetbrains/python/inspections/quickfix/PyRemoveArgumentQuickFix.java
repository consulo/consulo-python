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
package com.jetbrains.python.inspections.quickfix;

import javax.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyExpression;

//TODO: Remove pydoc aswell
public class PyRemoveArgumentQuickFix implements LocalQuickFix
{

	@Nonnull
	public String getFamilyName()
	{
		return PyBundle.message("QFIX.NAME.remove.argument");
	}

	public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor)
	{
		final PsiElement element = descriptor.getPsiElement();
		if(!(element instanceof PyExpression))
		{
			return;
		}
		final PyExpression expression = (PyExpression) element;
		final PsiElement nextSibling = PsiTreeUtil.skipSiblingsForward(expression, PsiWhiteSpace.class);
		final PsiElement prevSibling = PsiTreeUtil.skipSiblingsBackward(expression, PsiWhiteSpace.class);
		expression.delete();
		if(nextSibling != null && nextSibling.getNode().getElementType().equals(PyTokenTypes.COMMA))
		{
			nextSibling.delete();
			return;
		}
		if(prevSibling != null && prevSibling.getNode().getElementType().equals(PyTokenTypes.COMMA))
		{
			prevSibling.delete();
		}
	}
}
