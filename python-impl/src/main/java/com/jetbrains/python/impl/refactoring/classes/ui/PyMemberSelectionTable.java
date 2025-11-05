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
import consulo.language.editor.refactoring.classMember.MemberInfoModel;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.AbstractMemberSelectionTable;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class PyMemberSelectionTable extends AbstractMemberSelectionTable<PyElement, PyMemberInfo<PyElement>> {
    private final boolean mySupportAbstract;

    public PyMemberSelectionTable(
        @Nonnull List<PyMemberInfo<PyElement>> memberInfos,
        @Nullable MemberInfoModel<PyElement, PyMemberInfo<PyElement>> model,
        boolean supportAbstract
    ) {
        super(memberInfos, model, (supportAbstract ? RefactoringLocalize.makeAbstract().get() : null));
        mySupportAbstract = supportAbstract;
    }

    @Nullable
    @Override
    protected Object getAbstractColumnValue(PyMemberInfo<PyElement> memberInfo) {
        //TODO: Too many logic, move to presenters
        return (mySupportAbstract && memberInfo.isChecked() && myMemberInfoModel.isAbstractEnabled(memberInfo)) ? memberInfo.isToAbstract() : null;
    }

    @Override
    protected boolean isAbstractColumnEditable(int rowIndex) {
        return mySupportAbstract && myMemberInfoModel.isAbstractEnabled(myMemberInfos.get(rowIndex));
    }

    @Override
    protected Image getOverrideIcon(PyMemberInfo<PyElement> memberInfo) {
        Image overrideIcon = EMPTY_OVERRIDE_ICON;
        if (memberInfo.getMember() instanceof PyFunction && memberInfo.getOverrides() != null && memberInfo.getOverrides()) {
            overrideIcon = PlatformIconGroup.gutterOverridingmethod();
        }
        return overrideIcon;
    }
}
