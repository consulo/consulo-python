package com.jetbrains.python.psi.impl;

import javax.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySequenceExpression;

/**
 * @author Mikhail Golubev
 */
public abstract class PySequenceExpressionImpl extends PyElementImpl implements PySequenceExpression
{
	public PySequenceExpressionImpl(ASTNode astNode)
	{
		super(astNode);
	}

	@Override
	public void deleteChildInternal(@Nonnull ASTNode child)
	{
		if(ArrayUtil.contains(child.getPsi(), getElements()))
		{
			PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
		}
		super.deleteChildInternal(child);
	}

	@Nonnull
	public PyExpression[] getElements()
	{
		return childrenToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), PyExpression.EMPTY_ARRAY);
	}

	@Override
	public boolean isEmpty()
	{
		return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens()) == null;
	}
}
