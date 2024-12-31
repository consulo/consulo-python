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
import consulo.language.editor.refactoring.rename.RenameProcessor;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;

public class PyMakePublicQuickFix implements LocalQuickFix
{

	@Nonnull
	@Override
	public String getFamilyName()
	{
		return PyBundle.message("QFIX.make.public");
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		PsiElement element = descriptor.getPsiElement();
		if(element instanceof PyReferenceExpression)
		{
			final PsiReference reference = element.getReference();
			if(reference == null)
			{
				return;
			}
			element = reference.resolve();
		}
		if(element instanceof PyTargetExpression)
		{
			final String name = ((PyTargetExpression) element).getName();
			if(name == null)
			{
				return;
			}
			final VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
			if(virtualFile != null)
			{
				final String publicName = StringUtil.trimLeading(name, '_');
				new RenameProcessor(project, element, publicName, false, false).run();
			}
		}
	}

	//@Override
	public boolean startInWriteAction()
	{
		return false;
	}
}
