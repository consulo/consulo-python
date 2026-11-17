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
package com.jetbrains.python.rest.inspections;

import consulo.reStructuredText.localize.RestLocalize;
import consulo.language.editor.inspection.CustomSuppressableInspectionTool;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.intention.SuppressIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

/**
 * @author catherine
 */
public abstract class RestInspection extends LocalInspectionTool implements CustomSuppressableInspectionTool {
    @Override
    public LocalizeValue getGroupDisplayName() {
        return RestLocalize.inspGroupRest();
    }

    @Override
    public String getShortName() {
        return getClass().getSimpleName();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public SuppressIntentionAction[] getSuppressActions(@Nullable PsiElement element) {
        return null;
    }

    @Override
    public boolean isSuppressedFor(PsiElement element) {
        return false;
    }
}
