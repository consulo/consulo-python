package com.jetbrains.python.findUsages;

import org.jetbrains.annotations.NotNull;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.jetbrains.python.psi.PyTargetExpression;

/**
 * @author Mikhail Golubev
 */
public class PyTargetExpressionFindUsagesHandler extends FindUsagesHandler
{
	public PyTargetExpressionFindUsagesHandler(@NotNull PyTargetExpression psiElement)
	{
		super(psiElement);
	}
}
