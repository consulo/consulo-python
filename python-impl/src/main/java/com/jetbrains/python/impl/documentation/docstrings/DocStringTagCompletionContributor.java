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
package com.jetbrains.python.impl.documentation.docstrings;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.PsiFile;
import consulo.language.util.ProcessingContext;

import jakarta.annotation.Nonnull;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * @author yole
 */
@ExtensionImpl
public class DocStringTagCompletionContributor extends CompletionContributor
{
	public static final PsiElementPattern.Capture<PyStringLiteralExpression> DOCSTRING_PATTERN = psiElement(PyStringLiteralExpression.class).withParent(psiElement(PyExpressionStatement.class).inside
			(PyDocStringOwner.class));

	public DocStringTagCompletionContributor()
	{
		extend(CompletionType.BASIC, psiElement().withParent(DOCSTRING_PATTERN), new CompletionProvider()
		{
			@Override
			public void addCompletions(@Nonnull CompletionParameters parameters, ProcessingContext context, @Nonnull CompletionResultSet result)
			{
				final PsiFile file = parameters.getOriginalFile();
				DocStringFormat format = DocStringUtil.getConfiguredDocStringFormat(file);
				if(format == DocStringFormat.EPYTEXT || format == DocStringFormat.REST)
				{
					int offset = parameters.getOffset();
					final String text = file.getText();
					char prefix = format == DocStringFormat.EPYTEXT ? '@' : ':';
					if(offset > 0)
					{
						offset--;
					}
					StringBuilder prefixBuilder = new StringBuilder();
					while(offset > 0 && (Character.isLetterOrDigit(text.charAt(offset)) || text.charAt(offset) == prefix))
					{
						prefixBuilder.insert(0, text.charAt(offset));
						if(text.charAt(offset) == prefix)
						{
							offset--;
							break;
						}
						offset--;
					}
					while(offset > 0)
					{
						offset--;
						if(text.charAt(offset) == '\n' || text.charAt(offset) == '\"' || text.charAt(offset) == '\'')
						{
							break;
						}
						if(!Character.isWhitespace(text.charAt(offset)))
						{
							return;
						}
					}
					String[] allTags = format == DocStringFormat.EPYTEXT ? EpydocString.ALL_TAGS : SphinxDocString.ALL_TAGS;
					if(prefixBuilder.length() > 0)
					{
						result = result.withPrefixMatcher(prefixBuilder.toString());
					}
					for(String tag : allTags)
					{
						result.addElement(LookupElementBuilder.create(tag));
					}
				}
			}
		});
	}

	@Nonnull
	@Override
	public Language getLanguage()
	{
		return PythonLanguage.INSTANCE;
	}
}
