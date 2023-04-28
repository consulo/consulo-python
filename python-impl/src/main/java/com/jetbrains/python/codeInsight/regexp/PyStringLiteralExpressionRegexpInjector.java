package com.jetbrains.python.codeInsight.regexp;

import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 27/04/2023
 */
@ExtensionImpl
public class PyStringLiteralExpressionRegexpInjector extends PythonRegexpInjector
{
	@Nonnull
	@Override
	public Class<? extends PsiElement> getElementClass()
	{
		return PyStringLiteralExpression.class;
	}
}
