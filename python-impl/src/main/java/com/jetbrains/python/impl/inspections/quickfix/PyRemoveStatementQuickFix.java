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
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyStatement;

public class PyRemoveStatementQuickFix implements LocalQuickFix
{
	@Nonnull
	@Override
	public String getFamilyName()
	{
		return PyBundle.message("QFIX.NAME.remove.statement");
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		final PyStatement statement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyStatement.class, false);
		if(statement != null)
		{
			statement.delete();
		}
	}
}
