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
package com.jetbrains.python.impl.codeInsight.completion;

import org.jspecify.annotations.Nullable;

import consulo.language.editor.AutoPopupController;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.ParenthesesInsertHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.resolve.PyResolveContext;

/**
 * @author yole
 */
public class PyFunctionInsertHandler extends ParenthesesInsertHandler<LookupElement>
{
	public static PyFunctionInsertHandler INSTANCE = new PyFunctionInsertHandler();

	@Override
	public void handleInsert(InsertionContext context, LookupElement item)
	{
		super.handleInsert(context, item);
		if(hasParams(context, item))
		{
			AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), getFunction(item));
		}
	}

	@Override
	protected boolean placeCaretInsideParentheses(InsertionContext context, LookupElement item)
	{
		return hasParams(context, item);
	}

	private static boolean hasParams(InsertionContext context, LookupElement item)
	{
		PyFunction function = getFunction(item);
		return function != null && hasParams(context, function);
	}

	public static boolean hasParams(InsertionContext context, PyFunction function)
	{
		PsiElement element = context.getFile().findElementAt(context.getStartOffset());
		PyReferenceExpression refExpr = PsiTreeUtil.getParentOfType(element, PyReferenceExpression.class);
		int implicitArgsCount = refExpr != null ? PyCallExpressionHelper.getImplicitArgumentCount(refExpr, function, PyResolveContext.noImplicits()) : 0;
		return function.getParameterList().getParameters().length > implicitArgsCount;
	}

	@Nullable
	private static PyFunction getFunction(LookupElement item)
	{
		return PyUtil.as(item.getPsiElement(), PyFunction.class);
	}
}
