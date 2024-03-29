/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.impl.inspections.quickfix;

import consulo.language.editor.inspection.AbstractBatchSuppressByNoInspectionCommentFix;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyElement;

/**
 * @author yole
 */
public class PySuppressInspectionFix extends AbstractBatchSuppressByNoInspectionCommentFix
{
	private final Class<? extends PyElement> myContainerClass;

	public PySuppressInspectionFix(final String ID, final String text, final Class<? extends PyElement> containerClass)
	{
		super(ID, false);
		setText(text);
		myContainerClass = containerClass;
	}

	@Override
	public PsiElement getContainer(PsiElement context)
	{
		return PsiTreeUtil.getParentOfType(context, myContainerClass);
	}
}
