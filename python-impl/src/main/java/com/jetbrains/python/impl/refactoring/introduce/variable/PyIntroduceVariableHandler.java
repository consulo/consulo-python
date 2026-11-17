// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.introduce.variable;

import com.jetbrains.python.impl.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.psi.PyStatement;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;

import java.util.List;

public class PyIntroduceVariableHandler extends IntroduceHandler
{
	public PyIntroduceVariableHandler()
	{
		super(new VariableValidator(), PyLocalize.refactoringIntroduceVariableDialogTitle());
	}

	@Override
    @RequiredWriteAction
	protected PsiElement addDeclaration(PsiElement expression, PsiElement declaration, IntroduceOperation operation)
	{
		return doIntroduceVariable(expression, declaration, operation.getOccurrences(), operation.isReplaceAll());
	}

	@RequiredWriteAction
    public static PsiElement doIntroduceVariable(PsiElement expression, PsiElement declaration, List<? extends PsiElement> occurrences, boolean replaceAll)
	{
		PsiElement anchor = replaceAll ? IntroduceHandler.findAnchor(occurrences) : PsiTreeUtil.getParentOfType(expression, PyStatement.class);
		assert anchor != null;
		PsiElement parent = anchor.getParent();
		return parent.addBefore(declaration, anchor);
	}

	@Override
	protected String getHelpId()
	{
		return "refactoring.introduceVariable";
	}

	@Override
	protected String getRefactoringId()
	{
		return "refactoring.python.introduce.variable";
	}
}
