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
package com.jetbrains.python.impl.codeInsight.intentions;

import jakarta.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.util.IncorrectOperationException;

/**
 * User: catherine
 * Intention to convert dict constructor to dict literal expression with only explicit keyword arguments
 * For instance,
 * dict() -> {}
 * dict(a=3, b=5) -> {'a': 3, 'b': 5}
 * dict(foo) -> no transformation
 * dict(**foo) -> no transformation
 */
public class PyDictConstructorToLiteralFormIntention extends PyBaseIntentionAction
{
	@Nonnull
	public String getFamilyName()
	{
		return PyBundle.message("INTN.convert.dict.constructor.to.dict.literal");
	}

	@Nonnull
	public String getText()
	{
		return PyBundle.message("INTN.convert.dict.constructor.to.dict.literal");
	}

	public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
	{
		if(!(file instanceof PyFile))
		{
			return false;
		}

		PyCallExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyCallExpression.class);

		if(expression != null && expression.isCalleeText("dict"))
		{
			final TypeEvalContext context = TypeEvalContext.codeAnalysis(file.getProject(), file);
			PyType type = context.getType(expression);
			if(type != null && type.isBuiltin())
			{
				PyExpression[] argumentList = expression.getArguments();
				for(PyExpression argument : argumentList)
				{
					if(!(argument instanceof PyKeywordArgument))
					{
						return false;
					}
				}
				return true;
			}
		}
		return false;
	}

	public void doInvoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException
	{
		PyCallExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyCallExpression.class);
		PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
		if(expression != null)
		{
			replaceDictConstructor(expression, elementGenerator);
		}
	}

	private static void replaceDictConstructor(PyCallExpression expression, PyElementGenerator elementGenerator)
	{
		PyExpression[] argumentList = expression.getArguments();
		StringBuilder stringBuilder = new StringBuilder();

		int size = argumentList.length;

		for(int i = 0; i != size; ++i)
		{
			PyExpression argument = argumentList[i];
			if(argument instanceof PyKeywordArgument)
			{
				stringBuilder.append("'");
				stringBuilder.append(((PyKeywordArgument) argument).getKeyword());
				stringBuilder.append("' : ");
				stringBuilder.append(((PyKeywordArgument) argument).getValueExpression().getText());
				if(i != size - 1)
				{
					stringBuilder.append(",");
				}
			}

		}
		PyDictLiteralExpression dict = (PyDictLiteralExpression) elementGenerator.createFromText(LanguageLevel.forElement(expression), PyExpressionStatement.class, "{" + stringBuilder.toString() +
				"}").getExpression();
		expression.replace(dict);
	}
}
