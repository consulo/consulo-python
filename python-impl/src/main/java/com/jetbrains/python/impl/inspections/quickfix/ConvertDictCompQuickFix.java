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

import java.util.List;

import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyComprehensionComponent;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyComprehensionIfComponent;
import com.jetbrains.python.psi.PyDictCompExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyKeyValueExpression;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 20.02.2010
 * Time: 15:49:35
 */
public class ConvertDictCompQuickFix implements LocalQuickFix
{
	@Nonnull
	@Override
	public String getName()
	{
		return PyBundle.message("INTN.convert.dict.comp.to");
	}

	@Nonnull
	public String getFamilyName()
	{
		return PyBundle.message("INTN.Family.convert.dict.comp.expression");
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		PsiElement element = descriptor.getPsiElement();
		if(!LanguageLevel.forElement(element).isPy3K() && element instanceof PyDictCompExpression)
		{
			replaceComprehension(project, (PyDictCompExpression) element);
		}
	}

	private static void replaceComprehension(Project project, PyDictCompExpression expression)
	{
		if(expression.getResultExpression() instanceof PyKeyValueExpression)
		{
			final PyKeyValueExpression keyValueExpression = (PyKeyValueExpression) expression.getResultExpression();
			final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
			assert keyValueExpression.getValue() != null;

			final List<PyComprehensionComponent> components = expression.getComponents();
			final StringBuilder replacement = new StringBuilder("dict([(" + keyValueExpression.getKey().getText() + ", " +
					keyValueExpression.getValue().getText() + ")");
			int slashNum = 1;
			for(PyComprehensionComponent component : components)
			{
				if(component instanceof PyComprehensionForComponent)
				{
					replacement.append("for ");
					replacement.append(((PyComprehensionForComponent) component).getIteratorVariable().getText());
					replacement.append(" in ");
					replacement.append(((PyComprehensionForComponent) component).getIteratedList().getText());
					replacement.append(" ");
				}
				if(component instanceof PyComprehensionIfComponent)
				{
					final PyExpression test = ((PyComprehensionIfComponent) component).getTest();
					if(test != null)
					{
						replacement.append("if ");
						replacement.append(test.getText());
						replacement.append(" ");
					}
				}
				for(int i = 0; i != slashNum; ++i)
				{
					replacement.append("\t");
				}
				++slashNum;
			}
			replacement.append("])");

			expression.replace(elementGenerator.createFromText(LanguageLevel.getDefault(), PyExpressionStatement.class, replacement.toString()));
		}
	}

}
