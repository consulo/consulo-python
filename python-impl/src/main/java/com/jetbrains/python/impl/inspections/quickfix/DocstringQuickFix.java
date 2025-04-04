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
import jakarta.annotation.Nullable;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.codeInsight.intentions.PyGenerateDocstringIntention;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.language.util.IncorrectOperationException;

/**
 * User : catherine
 */
public class DocstringQuickFix implements LocalQuickFix
{
	private final SmartPsiElementPointer<PyNamedParameter> myMissingParam;
	private final String myUnexpectedParamName;

	public DocstringQuickFix(@Nullable PyNamedParameter missing, @Nullable String unexpectedParamName)
	{
		if(missing != null)
		{
			myMissingParam = SmartPointerManager.getInstance(missing.getProject()).createSmartPsiElementPointer(missing);
		}
		else
		{
			myMissingParam = null;
		}
		myUnexpectedParamName = unexpectedParamName;
	}

	@Nonnull
	public String getName()
	{
		if(myMissingParam != null)
		{
			final PyNamedParameter param = myMissingParam.getElement();
			if(param == null)
			{
				throw new IncorrectOperationException("Parameter was invalidates before quickfix is called");
			}
			return PyBundle.message("QFIX.docstring.add.$0", param.getName());
		}
		else if(myUnexpectedParamName != null)
		{
			return PyBundle.message("QFIX.docstring.remove.$0", myUnexpectedParamName);
		}
		else
		{
			return PyBundle.message("QFIX.docstring.insert.stub");
		}
	}

	@Nonnull
	public String getFamilyName()
	{
		return "Fix docstring";
	}

	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
	{
		PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyDocStringOwner.class);
		if(docStringOwner == null)
		{
			return;
		}
		PyStringLiteralExpression docStringExpression = docStringOwner.getDocStringExpression();
		if(docStringExpression == null && myMissingParam == null && myUnexpectedParamName == null)
		{
			addEmptyDocstring(docStringOwner);
			return;
		}
		if(docStringExpression != null)
		{
			final PyDocstringGenerator generator = PyDocstringGenerator.forDocStringOwner(docStringOwner);
			if(myMissingParam != null)
			{
				final PyNamedParameter param = myMissingParam.getElement();
				if(param != null)
				{
					generator.withParam(param);
				}
			}
			else if(myUnexpectedParamName != null)
			{
				generator.withoutParam(myUnexpectedParamName.trim());
			}
			generator.buildAndInsert();
		}
	}

	private static void addEmptyDocstring(@Nonnull PyDocStringOwner docStringOwner)
	{
		if(docStringOwner instanceof PyFunction || docStringOwner instanceof PyClass && ((PyClass) docStringOwner).findInitOrNew(false, null) != null)
		{
			PyGenerateDocstringIntention.generateDocstring(docStringOwner, PyQuickFixUtil.getEditor(docStringOwner));
		}
	}
}
