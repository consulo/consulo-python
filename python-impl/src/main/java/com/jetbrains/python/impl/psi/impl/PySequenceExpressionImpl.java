package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.ast.ASTNode;
import consulo.util.collection.ArrayUtil;


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
	public void deleteChildInternal(ASTNode child)
	{
		if(ArrayUtil.contains(child.getPsi(), getElements()))
		{
			PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
		}
		super.deleteChildInternal(child);
	}

	public PyExpression[] getElements()
	{
		return childrenToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), PyExpression.EMPTY_ARRAY);
	}

	@Override
	public boolean isEmpty()
	{
		return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens()) == null;
	}
}
