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
package com.jetbrains.python.impl.debugger;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import consulo.application.ApplicationManager;
import consulo.document.Document;
import consulo.project.Project;
import consulo.application.util.function.Computable;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;


public class PyDebugSupportUtils
{

	private PyDebugSupportUtils()
	{
	}

	// can expression be evaluated, or should be executed
	public static boolean isExpression(final Project project, final String expression)
	{
		return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>()
		{
			public Boolean compute()
			{

				final PsiFile file = PyElementGenerator.getInstance(project).createDummyFile(LanguageLevel.getDefault(), expression);
				return file.getFirstChild() instanceof PyExpressionStatement && file.getFirstChild() == file.getLastChild();
			}
		});
	}

	@Nullable
	public static TextRange getExpressionRangeAtOffset(final Project project, final Document document, final int offset)
	{
		return ApplicationManager.getApplication().runReadAction(new Computable<TextRange>()
		{
			@Nullable
			public TextRange compute()
			{

				final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
				if(psiFile != null)
				{
					PsiElement element = psiFile.findElementAt(offset);
					if(!(element instanceof PyExpression) || element instanceof PyLiteralExpression)
					{
						element = PsiTreeUtil.getParentOfType(element, PyExpression.class);
					}
					if(element != null && element instanceof PyLiteralExpression)
					{
						return null;
					}
					if(element != null && isSimpleEnough(element) && isExpression(project, document.getText(element.getTextRange())))
					{
						return element.getTextRange();
					}
				}
				return null;
			}
		});
	}

	// is expression suitable to quick evaluate/display tooltip
	private static boolean isSimpleEnough(final PsiElement element)
	{
		return element instanceof PyLiteralExpression ||
				element instanceof PyQualifiedExpression ||
				element instanceof PyBinaryExpression ||
				element instanceof PyPrefixExpression ||
				element instanceof PySliceExpression ||
				element instanceof PyNamedParameter;
	}

	// is expression a variable reference and can be evaluated
	// todo: use patterns (?)
	public static boolean canSaveToTemp(final Project project, final String expression)
	{
		return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>()
		{
			public Boolean compute()
			{

				final PsiFile file = PyElementGenerator.getInstance(project).createDummyFile(LanguageLevel.getDefault(), expression);
				final PsiElement root = file.getFirstChild();
				return !isVariable(root) && (root instanceof PyExpressionStatement);
			}
		});
	}

	private static Boolean isVariable(PsiElement root)
	{
		return root instanceof PyExpressionStatement &&
				root.getFirstChild() instanceof PyReferenceExpression &&
				root.getFirstChild() == root.getLastChild() &&
				root.getFirstChild().getFirstChild() != null &&
				root.getFirstChild().getFirstChild().getNode().getElementType() == PyTokenTypes.IDENTIFIER &&
				root.getFirstChild().getFirstChild() == root.getFirstChild().getLastChild() &&
				root.getFirstChild().getFirstChild().getFirstChild() == null;
	}

	@Nullable
	private static String getLineText(@Nonnull Document document, int line)
	{
		if(line > 0 && line < document.getLineCount())
		{
			return document.getText(TextRange.create(document.getLineStartOffset(line), document.getLineEndOffset(line)));
		}
		return null;
	}

	public static boolean isContinuationLine(@Nonnull Document document, int line)
	{
		String text = getLineText(document, line);
		if(text != null && text.trim().endsWith("\\"))
		{
			return true;
		}

		return false;
	}
}
