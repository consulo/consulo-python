package com.jetbrains.python.impl.findUsages;


import consulo.find.FindUsagesHandler;
import com.jetbrains.python.psi.PyTargetExpression;

/**
 * @author Mikhail Golubev
 */
public class PyTargetExpressionFindUsagesHandler extends FindUsagesHandler
{
	public PyTargetExpressionFindUsagesHandler(PyTargetExpression psiElement)
	{
		super(psiElement);
	}
}
