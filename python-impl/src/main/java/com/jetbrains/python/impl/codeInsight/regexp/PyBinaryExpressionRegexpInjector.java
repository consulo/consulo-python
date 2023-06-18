package com.jetbrains.python.impl.codeInsight.regexp;

import com.jetbrains.python.psi.PyBinaryExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 27/04/2023
 */
@ExtensionImpl
public class PyBinaryExpressionRegexpInjector extends PythonRegexpInjector
{
	@Nonnull
	@Override
	public Class<? extends PsiElement> getElementClass()
	{
		return PyBinaryExpression.class;
	}
}
