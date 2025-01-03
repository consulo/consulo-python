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

import com.google.common.collect.Lists;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author traff
 */
@ExtensionImpl
public class PySuperClassAttributesCompletionContributor extends CompletionContributor
{
	public PySuperClassAttributesCompletionContributor()
	{
		extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParents(PyReferenceExpression.class, PyExpressionStatement.class, PyStatementList.class, PyClass.class), new
				CompletionProvider()
		{
			@Override
			public void addCompletions(@Nonnull CompletionParameters parameters, ProcessingContext context, @Nonnull CompletionResultSet result)
			{
				PsiElement position = parameters.getOriginalPosition();
				PyClass containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);

				if(containingClass == null)
				{
					return;
				}
				for(PyTargetExpression expr : getSuperClassAttributes(containingClass))
				{
					result.addElement(LookupElementBuilder.createWithSmartPointer(expr.getName() + " = ", expr));
				}
			}
		});
	}

	public static List<PyTargetExpression> getSuperClassAttributes(@Nonnull PyClass cls)
	{
		List<PyTargetExpression> attrs = Lists.newArrayList();
		List<String> seenNames = Lists.newArrayList();
		for(PyTargetExpression expr : cls.getClassAttributes())
		{
			seenNames.add(expr.getName());
		}
		for(PyClass ancestor : cls.getAncestorClasses(null))
		{
			for(PyTargetExpression expr : ancestor.getClassAttributes())
			{
				if(!seenNames.contains(expr.getName()))
				{
					seenNames.add(expr.getName());
					attrs.add(expr);
				}
			}
		}
		return attrs;
	}

	@Nonnull
	@Override
	public Language getLanguage()
	{
		return PythonLanguage.INSTANCE;
	}
}
