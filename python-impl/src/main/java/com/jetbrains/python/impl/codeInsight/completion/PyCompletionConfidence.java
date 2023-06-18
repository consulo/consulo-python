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

package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.editor.completion.CompletionConfidence;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.util.lang.ThreeState;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl
public class PyCompletionConfidence extends CompletionConfidence
{
	@Nonnull
	@Override
	public ThreeState shouldSkipAutopopup(@Nonnull PsiElement contextElement, @Nonnull PsiFile psiFile, int offset)
	{
		ASTNode node = contextElement.getNode();
		if(node != null)
		{
			if(node.getElementType() == PyTokenTypes.FLOAT_LITERAL)
			{
				return ThreeState.YES;
			}
			if(PyTokenTypes.STRING_NODES.contains(node.getElementType()))
			{
				final PsiElement parent = contextElement.getParent();
				if(parent instanceof PyStringLiteralExpression)
				{
					final List<TextRange> ranges = ((PyStringLiteralExpression) parent).getStringValueTextRanges();
					int relativeOffset = offset - parent.getTextRange().getStartOffset();
					if(ranges.size() > 0 && relativeOffset < ranges.get(0).getStartOffset())
					{
						return ThreeState.YES;
					}
				}
			}
		}
		return super.shouldSkipAutopopup(contextElement, psiFile, offset);
	}

	@Nonnull
	@Override
	public Language getLanguage()
	{
		return PythonLanguage.INSTANCE;
	}
}
