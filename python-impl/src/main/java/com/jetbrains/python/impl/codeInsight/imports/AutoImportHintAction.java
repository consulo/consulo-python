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

package com.jetbrains.python.impl.codeInsight.imports;

import consulo.codeEditor.Editor;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.HintAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;

/**
 * @author yole
 */
public class AutoImportHintAction implements LocalQuickFix, HintAction, HighPriorityAction {
    private final AutoImportQuickFix myDelegate;

    public AutoImportHintAction(AutoImportQuickFix delegate) {
        myDelegate = delegate;
    }

    @Override
    public boolean showHint(Editor editor) {
        return myDelegate.showHint(editor);
    }

    @Override
    public LocalizeValue getText() {
        return myDelegate.getText();
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
        return myDelegate.isAvailable();
    }

    @Override
    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        myDelegate.invoke(file);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public LocalizeValue getName() {
        return myDelegate.getName();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        myDelegate.applyFix(project, descriptor);
    }
}
