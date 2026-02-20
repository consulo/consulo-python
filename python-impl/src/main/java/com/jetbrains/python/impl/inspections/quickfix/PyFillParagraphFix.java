/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import consulo.codeEditor.Editor;
import consulo.ide.impl.idea.codeInsight.editorActions.fillParagraph.FillParagraphAction;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

public class PyFillParagraphFix extends BaseIntentionAction implements HighPriorityAction {
    public PyFillParagraphFix() {
        setText(LocalizeValue.localizeTODO("Fill paragraph"));
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        FillParagraphAction action = new consulo.ide.impl.idea.codeInsight.editorActions.fillParagraph.FillParagraphAction();
        action.actionPerformedImpl(project, editor);
    }
}
