// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest;

import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.rest.RestElementType;
import com.jetbrains.rest.RestLanguage;
import consulo.language.impl.psi.template.TemplateDataElementType;
import consulo.language.lexer.Lexer;
import consulo.language.psi.PsiFile;
import consulo.language.template.TemplateLanguageFileViewProvider;

/**
 * User : catherine
 */
public class RestPythonTemplateType extends TemplateDataElementType
{
	public RestPythonTemplateType(String python_block_data, RestLanguage instance, RestElementType line, RestElementType restInjection)
	{
		super(python_block_data, instance, line, restInjection);
	}

	@Override
	protected Lexer createBaseLexer(PsiFile file, TemplateLanguageFileViewProvider viewProvider)
	{
		return new PythonHighlightingLexer(LanguageLevel.getDefault());
	}
}
