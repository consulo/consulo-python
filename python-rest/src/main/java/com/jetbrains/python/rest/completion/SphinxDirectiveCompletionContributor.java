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
package com.jetbrains.python.rest.completion;

import com.jetbrains.rest.RestLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.util.ModuleUtilCore;
import consulo.content.bundle.Sdk;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import com.jetbrains.rest.RestTokenTypes;
import com.jetbrains.rest.RestUtil;
import com.jetbrains.rest.psi.RestReferenceTarget;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionContributor;
import consulo.python.module.extension.PyModuleExtension;

import jakarta.annotation.Nonnull;

import static consulo.language.pattern.PlatformPatterns.psiElement;
import static consulo.language.pattern.StandardPatterns.or;

/**
 * User : ktisha
 */
@ExtensionImpl
public class SphinxDirectiveCompletionContributor extends CompletionContributor
{
	public static final PsiElementPattern.Capture<PsiElement> DIRECTIVE_PATTERN = psiElement().afterSibling(or(psiElement().
			withElementType(RestTokenTypes.WHITESPACE).afterSibling(psiElement(RestReferenceTarget.class)), psiElement().withElementType(RestTokenTypes.EXPLISIT_MARKUP_START)));

	public SphinxDirectiveCompletionContributor()
	{
		extend(CompletionType.BASIC, DIRECTIVE_PATTERN, new CompletionProvider()
		{
			@RequiredReadAction
			@Override
			public void addCompletions(@Nonnull CompletionParameters parameters, ProcessingContext context, @Nonnull CompletionResultSet result)
			{
				Sdk sdk = ModuleUtilCore.getSdk(parameters.getPosition(), PyModuleExtension.class);
				if(sdk != null)
				{
					for(String tag : RestUtil.SPHINX_DIRECTIVES)
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
		return RestLanguage.INSTANCE;
	}
}
