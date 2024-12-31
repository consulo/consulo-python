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
package com.jetbrains.python.impl.psi.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Nonnull;

import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.PyIndentUtil;
import com.jetbrains.python.psi.PyParameter;

/**
 * @author yole
 */
public class PyFunctionBuilder
{
	private final String myName;
	private final List<String> myParameters = new ArrayList<>();
	private final List<String> myStatements = new ArrayList<>();
	private final List<String> myDecorators = new ArrayList<>();
	private String myAnnotation = null;
	@Nonnull
	private final Map<String, String> myDecoratorValues = new HashMap<>();
	private boolean myAsync = false;
	private PyDocstringGenerator myDocStringGenerator;

	/**
	 * Creates builder copying signature and doc from another one.
	 *
	 * @param source                  what to copy
	 * @param decoratorsToCopyIfExist list of decorator names to be copied to new function.
	 * @return builder configured by this function
	 */
	@Nonnull
	public static PyFunctionBuilder copySignature(@Nonnull final PyFunction source, @Nonnull final String... decoratorsToCopyIfExist)
	{
		final String name = source.getName();
		final PyFunctionBuilder functionBuilder = new PyFunctionBuilder((name != null) ? name : "", source);
		for(final PyParameter parameter : source.getParameterList().getParameters())
		{
			final String parameterName = parameter.getName();
			if(parameterName != null)
			{
				functionBuilder.parameter(parameterName);
			}
		}
		final PyDecoratorList decoratorList = source.getDecoratorList();
		if(decoratorList != null)
		{
			for(final PyDecorator decorator : decoratorList.getDecorators())
			{
				final String decoratorName = decorator.getName();
				if(decoratorName != null)
				{
					if(ArrayUtil.contains(decoratorName, decoratorsToCopyIfExist))
					{
						functionBuilder.decorate(decoratorName);
					}
				}
			}
		}
		functionBuilder.myDocStringGenerator = PyDocstringGenerator.forDocStringOwner(source);
		return functionBuilder;
	}

	@Deprecated
	public PyFunctionBuilder(@Nonnull String name)
	{
		myName = name;
		myDocStringGenerator = null;
	}

	/**
	 * @param settingsAnchor any PSI element, presumably in the same file/module where generated function is going to be inserted.
	 *                       It's needed to detect configured docstring format and Python indentation size and, as result,
	 *                       generate properly formatted docstring.
	 */
	public PyFunctionBuilder(@Nonnull String name, @Nonnull PsiElement settingsAnchor)
	{
		myName = name;
		myDocStringGenerator = PyDocstringGenerator.create(DocStringUtil.getConfiguredDocStringFormat(settingsAnchor), PyIndentUtil.getIndentFromSettings(settingsAnchor.getProject()),
				settingsAnchor);
	}

	/**
	 * Adds param and its type to doc
	 *
	 * @param format what docstyle to use to doc param type
	 * @param name   param name
	 * @param type   param type
	 */
	@Nonnull
	public PyFunctionBuilder parameterWithType(@Nonnull String name, @Nonnull String type)
	{
		parameter(name);
		myDocStringGenerator.withParamTypedByName(name, type);
		return this;
	}

	@Nonnull
	@Deprecated
	public PyFunctionBuilder parameterWithType(@Nonnull final String name, @Nonnull final String type, @Nonnull final DocStringFormat format)
	{
		parameter(name);
		myDocStringGenerator.withParamTypedByName(name, type);
		return this;
	}

	public PyFunctionBuilder parameter(String baseName)
	{
		String name = baseName;
		int uniqueIndex = 0;
		while(myParameters.contains(name))
		{
			uniqueIndex++;
			name = baseName + uniqueIndex;
		}
		myParameters.add(name);
		return this;
	}

	public PyFunctionBuilder annotation(String text)
	{
		myAnnotation = text;
		return this;
	}

	public PyFunctionBuilder makeAsync()
	{
		myAsync = true;
		return this;
	}

	public PyFunctionBuilder statement(String text)
	{
		myStatements.add(text);
		return this;
	}

	public PyFunction addFunction(PsiElement target, final LanguageLevel languageLevel)
	{
		return (PyFunction) target.add(buildFunction(target.getProject(), languageLevel));
	}

	public PyFunction addFunctionAfter(PsiElement target, PsiElement anchor, final LanguageLevel languageLevel)
	{
		return (PyFunction) target.addAfter(buildFunction(target.getProject(), languageLevel), anchor);
	}

	public PyFunction buildFunction(Project project, final LanguageLevel languageLevel)
	{
		PyElementGenerator generator = PyElementGenerator.getInstance(project);
		String text = buildText(project, generator, languageLevel);
		return generator.createFromText(languageLevel, PyFunction.class, text);
	}

	private String buildText(Project project, PyElementGenerator generator, LanguageLevel languageLevel)
	{
		StringBuilder builder = new StringBuilder();
		for(String decorator : myDecorators)
		{
			final StringBuilder decoratorAppender = builder.append('@' + decorator);
			if(myDecoratorValues.containsKey(decorator))
			{
				final PyCallExpression fakeCall = generator.createCallExpression(languageLevel, "fakeFunction");
				fakeCall.getArgumentList().addArgument(generator.createStringLiteralFromString(myDecoratorValues.get(decorator)));
				decoratorAppender.append(fakeCall.getArgumentList().getText());
			}
			decoratorAppender.append("\n");
		}
		if(myAsync)
		{
			builder.append("async ");
		}
		builder.append("def ");
		builder.append(myName).append("(");
		builder.append(StringUtil.join(myParameters, ", "));
		builder.append(")");
		if(myAnnotation != null)
		{
			builder.append(myAnnotation);
		}
		builder.append(":");
		List<String> statements = myStatements.isEmpty() ? Collections.singletonList(PyNames.PASS) : myStatements;

		final String indent = PyIndentUtil.getIndentFromSettings(project);
		// There was original docstring or some parameters were added via parameterWithType()
		if(!myDocStringGenerator.isNewMode() || myDocStringGenerator.hasParametersToAdd())
		{
			final String docstring = PyIndentUtil.changeIndent(myDocStringGenerator.buildDocString(), true, indent);
			builder.append('\n').append(indent).append(docstring);
		}
		for(String statement : statements)
		{
			builder.append('\n').append(indent).append(statement);
		}
		return builder.toString();
	}

	/**
	 * Adds decorator with argument
	 *
	 * @param decoratorName decorator name
	 * @param value         its argument
	 */
	public void decorate(@Nonnull final String decoratorName, @Nonnull final String value)
	{
		decorate(decoratorName);
		myDecoratorValues.put(decoratorName, value);
	}

	public void decorate(String decoratorName)
	{
		myDecorators.add(decoratorName);
	}
}
