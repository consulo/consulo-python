package com.jetbrains.python.findUsages;

import javax.annotation.Nonnull;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.jetbrains.python.psi.PyTargetExpression;

/**
 * @author Mikhail Golubev
 */
public class PyTargetExpressionFindUsagesHandler extends FindUsagesHandler
{
	public PyTargetExpressionFindUsagesHandler(@Nonnull PyTargetExpression psiElement)
	{
		super(psiElement);
	}
}
