package com.jetbrains.python.impl.codeInsight.regexp;

import com.jetbrains.python.psi.PyParenthesizedExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 27/04/2023
 */
@ExtensionImpl
public class PyParenthesizedExpressionRegExpInjector extends PythonRegexpInjector
{
	@Nonnull
	@Override
	public Class<? extends PsiElement> getElementClass()
	{
		return PyParenthesizedExpression.class;
	}
}
