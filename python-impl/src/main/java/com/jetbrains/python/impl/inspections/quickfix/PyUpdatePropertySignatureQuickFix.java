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

import javax.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;

public class PyUpdatePropertySignatureQuickFix implements LocalQuickFix
{
	private final boolean myHasValue;

	public PyUpdatePropertySignatureQuickFix(boolean hasValue)
	{
		myHasValue = hasValue;
	}

	@Nonnull
	@Override
	public String getFamilyName()
	{
		return PyBundle.message("QFIX.NAME.update.parameters");
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		final PsiElement element = descriptor.getPsiElement();
		final PyCallable function = PsiTreeUtil.getParentOfType(element, PyCallable.class, false);
		assert function != null;
		final PyParameterList parameterList = function.getParameterList();
		final PyParameter[] parameters = parameterList.getParameters();
		final PyElementGenerator generator = PyElementGenerator.getInstance(project);
		String selfText = parameters.length != 0 ? parameters[0].getText() : PyNames.CANONICAL_SELF;
		final StringBuilder functionText = new StringBuilder("def foo(" + selfText);
		if(myHasValue)
		{
			String valueText = parameters.length > 1 ? parameters[1].getText() : "value";
			functionText.append(", ").append(valueText);
		}
		functionText.append("): pass");

		final PyParameterList list = generator.createFromText(LanguageLevel.forElement(element), PyFunction.class, functionText.toString()).getParameterList();
		parameterList.replace(list);
	}
}
