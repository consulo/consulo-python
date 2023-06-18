package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.ast.ASTNode;
import consulo.util.collection.ArrayUtil;

import javax.annotation.Nonnull;

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
