/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import javax.annotation.Nonnull;

import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;

/**
 * User: catherine
 * <p/>
 * QuickFix to add self to unresolved reference
 */
public class UnresolvedReferenceAddSelfQuickFix implements LocalQuickFix, HighPriorityAction
{
	private PyReferenceExpression myElement;
	private String myQualifier;

	public UnresolvedReferenceAddSelfQuickFix(@Nonnull final PyReferenceExpression element, @Nonnull final String qualifier)
	{
		myElement = element;
		myQualifier = qualifier;
	}

	@Nonnull
	public String getName()
	{
		return PyBundle.message("QFIX.unresolved.reference", myElement.getText(), myQualifier);
	}

	@Nonnull
	public String getFamilyName()
	{
		return "Add qualifier";
	}

	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		if(!FileModificationService.getInstance().preparePsiElementForWrite(myElement))
		{
			return;
		}
		PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
		PyExpression expression = elementGenerator.createExpressionFromText(LanguageLevel.forElement(myElement), myQualifier + "." + myElement.getText());
		myElement.replace(expression);
	}
}
