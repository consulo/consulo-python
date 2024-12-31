/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.psi.AbstractElementManipulator;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;

/**
 * @author oleg
 */
@ExtensionImpl
public class PyReferenceExpressionManipulator extends AbstractElementManipulator<PyReferenceExpression>
{
	public PyReferenceExpression handleContentChange(final PyReferenceExpression element, final TextRange range, final String newContent)
			throws IncorrectOperationException
	{
		return null;
	}

	@Override
	public TextRange getRangeInElement(final PyReferenceExpression element)
	{
		final ASTNode nameElement = element.getNameElement();
		final int startOffset = nameElement != null ? nameElement.getStartOffset() : element.getTextRange().getEndOffset();
		return new TextRange(startOffset - element.getTextOffset(), element.getTextLength());
	}

	@Nonnull
	@Override
	public Class<PyReferenceExpression> getElementClass()
	{
		return PyReferenceExpression.class;
	}
}
