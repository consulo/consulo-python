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
package com.jetbrains.python.impl.refactoring.classes.ui;

import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import consulo.application.AllIcons;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.classMember.MemberInfoModel;
import consulo.language.editor.refactoring.ui.AbstractMemberSelectionTable;
import consulo.language.psi.PsiElement;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class PyMemberSelectionTable extends AbstractMemberSelectionTable<PyElement, PyMemberInfo<PyElement>>
{
	private static final String ABSTRACT_TITLE = RefactoringBundle.message("make.abstract");
	private final boolean mySupportAbstract;

	public PyMemberSelectionTable(@Nonnull final List<PyMemberInfo<PyElement>> memberInfos, @Nullable final MemberInfoModel<PyElement, PyMemberInfo<PyElement>> model, final boolean supportAbstract)
	{
		super(memberInfos, model, (supportAbstract ? ABSTRACT_TITLE : null));
		mySupportAbstract = supportAbstract;
	}

	@Nullable
	@Override
	protected Object getAbstractColumnValue(final PyMemberInfo<PyElement> memberInfo)
	{
		//TODO: Too many logic, move to presenters
		return (mySupportAbstract && memberInfo.isChecked() && myMemberInfoModel.isAbstractEnabled(memberInfo)) ? memberInfo.isToAbstract() : null;
	}

	@Override
	protected boolean isAbstractColumnEditable(final int rowIndex)
	{
		return mySupportAbstract && myMemberInfoModel.isAbstractEnabled(myMemberInfos.get(rowIndex));
	}

	@Override
	protected Image getOverrideIcon(PyMemberInfo<PyElement> memberInfo)
	{
		final PsiElement member = memberInfo.getMember();
		Image overrideIcon = EMPTY_OVERRIDE_ICON;
		if(member instanceof PyFunction && memberInfo.getOverrides() != null && memberInfo.getOverrides())
		{
			overrideIcon = AllIcons.General.OverridingMethod;
		}
		return overrideIcon;
	}
}
