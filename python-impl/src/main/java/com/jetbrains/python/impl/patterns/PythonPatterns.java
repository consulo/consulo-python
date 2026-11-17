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
package com.jetbrains.python.impl.patterns;

import java.util.Collections;
import java.util.List;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import one.util.streamex.StreamEx;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.pattern.InitialPatternCondition;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.io.FileUtil;

import org.jspecify.annotations.Nullable;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class PythonPatterns extends PlatformPatterns
{

	private static final int STRING_LITERAL_LIMIT = 10000;

	public static PyElementPattern.Capture<PyLiteralExpression> pyLiteralExpression()
	{
		return new PyElementPattern.Capture<>(new InitialPatternCondition<PyLiteralExpression>(PyLiteralExpression.class)
		{
			public boolean accepts(@Nullable Object o, ProcessingContext context)
			{
				return o instanceof PyLiteralExpression;
			}
		});
	}

	public static PyElementPattern.Capture<PyStringLiteralExpression> pyStringLiteralMatches(String regexp)
	{
		final Pattern pattern = Pattern.compile(regexp, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
		return new PyElementPattern.Capture<>(new InitialPatternCondition<PyStringLiteralExpression>(PyStringLiteralExpression.class)
		{
			@Override
			public boolean accepts(@Nullable Object o, ProcessingContext context)
			{
				if(o instanceof PyStringLiteralExpression)
				{
					PyStringLiteralExpression expr = (PyStringLiteralExpression) o;
					if(!DocStringUtil.isDocStringExpression(expr) && expr.getTextLength() < STRING_LITERAL_LIMIT)
					{
						String value = expr.getStringValue();
						return pattern.matcher(value).find();
					}
				}
				return false;
			}
		});
	}

	public static PyElementPattern.Capture<PyExpression> pyArgument(final String functionName, final int index)
	{
		return new PyElementPattern.Capture<>(new InitialPatternCondition<PyExpression>(PyExpression.class)
		{
			public boolean accepts(@Nullable Object o, ProcessingContext context)
			{
				return isCallArgument(o, functionName, index);
			}
		});
	}

	public static PyElementPattern.Capture<PyExpression> pyModuleFunctionArgument(@Nullable String functionName,
	                                                                               int index,
	                                                                               String moduleName)
	{
		return new PyElementPattern.Capture<>(new InitialPatternCondition<>(PyExpression.class)
		{
			@Override
			public boolean accepts(@Nullable Object o, ProcessingContext context)
			{
				return StreamEx
					.of(multiResolveCalledFunction(o, functionName, index))
					.select(PyFunction.class)
					.map(ScopeUtil::getScopeOwner)
					.select(PyFile.class)
					.anyMatch(file -> moduleName.equals(FileUtil.getNameWithoutExtension(file.getName())));
			}
		});
	}

	public static PyElementPattern.Capture<PyExpression> pyMethodArgument(@Nullable String functionName,
	                                                                       int index,
	                                                                       String classQualifiedName)
	{
		return new PyElementPattern.Capture<>(new InitialPatternCondition<>(PyExpression.class)
		{
			@Override
			public boolean accepts(@Nullable Object o, ProcessingContext context)
			{
				return StreamEx
					.of(multiResolveCalledFunction(o, functionName, index))
					.select(PyFunction.class)
					.map(ScopeUtil::getScopeOwner)
					.select(PyClass.class)
					.anyMatch(cls -> classQualifiedName.equals(cls.getQualifiedName()));
			}
		});
	}

	private static List<PyCallable> multiResolveCalledFunction(@Nullable Object expression, @Nullable String functionName, int index)
	{
		if(!isCallArgument(expression, functionName, index))
		{
			return Collections.emptyList();
		}

		final PyCallExpression call = (PyCallExpression) ((PyExpression) expression).getParent().getParent();

		// TODO is it better or worse to allow implicits here?
		final TypeEvalContext context = TypeEvalContext.codeAnalysis(call.getProject(), call.getContainingFile());

		return call.multiResolveCalleeFunction(PyResolveContext.defaultContext(context));
	}

	private static boolean isCallArgument(Object o, String functionName, int index)
	{
		if(!(o instanceof PyExpression))
		{
			return false;
		}
		PsiElement parent = ((PyExpression) o).getParent();
		if(!(parent instanceof PyArgumentList))
		{
			return false;
		}
		PsiElement parent1 = parent.getParent();
		if(!(parent1 instanceof PyCallExpression))
		{
			return false;
		}
		PyExpression methodExpression = ((PyCallExpression) parent1).getCallee();
		if(!(methodExpression instanceof PyReferenceExpression))
		{
			return false;
		}
		String referencedName = ((PyReferenceExpression) methodExpression).getReferencedName();
		if(referencedName == null || !referencedName.equals(functionName))
		{
			return false;
		}
		int i = 0;
		for(PsiElement child : parent.getChildren())
		{
			if(i == index)
			{
				return child == o;
			}
			i++;
		}
		return false;
	}
}
