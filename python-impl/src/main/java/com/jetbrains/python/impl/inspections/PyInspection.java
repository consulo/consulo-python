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
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import consulo.language.Language;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Pattern;

public abstract class PyInspection extends LocalInspectionTool {
    @Pattern(VALID_ID_PATTERN)
    @Nonnull
    @Override
    public String getID() {
        //noinspection PatternValidation
        return getShortName(super.getID());
    }

    @Nullable
    @Override
    public Language getLanguage() {
        return PythonLanguage.getInstance();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.inspectionGeneralToolsGroupName();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return getClass().getSimpleName();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    @Override
    public boolean isSuppressedFor(@Nonnull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file instanceof PyFileImpl && !((PyFileImpl) file).isAcceptedFor(this.getClass())) {
            return true;
        }
        return isSuppressForCodeFragment(element) || super.isSuppressedFor(element);
    }

    private boolean isSuppressForCodeFragment(@Nullable PsiElement element) {
        return isSuppressForCodeFragment() && PsiTreeUtil.getParentOfType(element, PyExpressionCodeFragment.class) != null;
    }

    protected boolean isSuppressForCodeFragment() {
        return false;
    }
}
