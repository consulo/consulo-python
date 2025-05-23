/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.refactoring.introduce.variable;

import java.util.List;

import jakarta.annotation.Nonnull;

import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceOperation;

/**
 * @author Alexey.Ivanov
 */
public class PyIntroduceVariableHandler extends IntroduceHandler
{
	public PyIntroduceVariableHandler()
	{
		super(new VariableValidator(), PyBundle.message("refactoring.introduce.variable.dialog.title"));
	}

	@Override
	protected PsiElement addDeclaration(@Nonnull final PsiElement expression, @Nonnull final PsiElement declaration, @Nonnull IntroduceOperation operation)
	{
		return doIntroduceVariable(expression, declaration, operation.getOccurrences(), operation.isReplaceAll());
	}

	public static PsiElement doIntroduceVariable(PsiElement expression, PsiElement declaration, List<PsiElement> occurrences, boolean replaceAll)
	{
		PsiElement anchor = replaceAll ? findAnchor(occurrences) : PsiTreeUtil.getParentOfType(expression, PyStatement.class);
		assert anchor != null;
		final PsiElement parent = anchor.getParent();
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
