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

import java.util.Map;

import jakarta.annotation.Nonnull;

import com.jetbrains.python.impl.psi.PyUtil;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.*;

public class PyAddPropertyForFieldQuickFix implements LocalQuickFix
{
	private String myName = PyBundle.message("QFIX.add.property");

	public PyAddPropertyForFieldQuickFix(String name)
	{
		myName = name;
	}

	@Nonnull
	public String getFamilyName()
	{
		return myName;
	}

	public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor)
	{
		final PsiElement element = descriptor.getPsiElement();
		if(element instanceof PyReferenceExpression)
		{
			final PsiReference reference = element.getReference();
			if(reference == null)
			{
				return;
			}
			final PsiElement resolved = reference.resolve();
			if(resolved instanceof PyTargetExpression)
			{
				PyTargetExpression target = (PyTargetExpression) resolved;
				final PyClass containingClass = target.getContainingClass();
				if(containingClass != null)
				{
					final String name = target.getName();
					if(name == null)
					{
						return;
					}
					String propertyName = StringUtil.trimStart(name, "_");
					final Map<String, Property> properties = containingClass.getProperties();
					final PyElementGenerator generator = PyElementGenerator.getInstance(project);
					if(!properties.containsKey(propertyName))
					{
						final PyFunction property = generator.createProperty(LanguageLevel.forElement(containingClass), propertyName, name, AccessDirection.READ);
						PyUtil.addElementToStatementList(property, containingClass.getStatementList(), false);
					}
					final PyExpression qualifier = ((PyReferenceExpression) element).getQualifier();
					if(qualifier != null)
					{
						String newElementText = qualifier.getText() + "." + propertyName;
						final PyExpression newElement = generator.createExpressionFromText(LanguageLevel.forElement(containingClass), newElementText);
						element.replace(newElement);
					}
				}
			}
		}
	}
}
