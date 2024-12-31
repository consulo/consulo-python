/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nls;
import jakarta.annotation.Nonnull;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyNumericLiteralExpression;

public class PyRemoveUnderscoresInNumericLiteralsQuickFix implements LocalQuickFix
{

	@Nls
	@Nonnull
	@Override
	public String getFamilyName()
	{
		return PyBundle.message("QFIX.NAME.remove.underscores.in.numeric");
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		final PsiElement element = descriptor.getPsiElement();
		if(element instanceof PyNumericLiteralExpression)
		{
			final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
			final String text = element.getText();

			element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), text.replaceAll("_", "")));
		}
	}
}
