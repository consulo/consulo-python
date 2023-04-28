package com.jetbrains.python.codeInsight;

import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.hint.DeclarationRangeHandler;

import javax.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class PyClassRangeHandler implements DeclarationRangeHandler<PyClass>
{
	@Nonnull
	@Override
	public Class<PyClass> getElementClass()
	{
		return PyClass.class;
	}

	@Nonnull
	@Override
	@RequiredReadAction
	public TextRange getDeclarationRange(@Nonnull PyClass container)
	{
		int start = container.getTextRange().getStartOffset();
		PyArgumentList argumentList = ((PyClass) container).getSuperClassExpressionList();
		if(argumentList != null)
		{
			return new TextRange(start, argumentList.getTextRange().getEndOffset());
		}
		ASTNode nameNode = ((PyClass) container).getNameNode();
		if(nameNode != null)
		{
			return new TextRange(start, nameNode.getStartOffset() + nameNode.getTextLength());
		}
		return container.getTextRange();
	}
}
