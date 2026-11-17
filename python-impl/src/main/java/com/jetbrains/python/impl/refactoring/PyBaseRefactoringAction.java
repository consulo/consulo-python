/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.impl.refactoring;

import com.jetbrains.python.PythonLanguage;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.Language;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;

/**
 * @author Mikhail Golubev
 */
public abstract class PyBaseRefactoringAction extends BaseRefactoringAction {
    protected PyBaseRefactoringAction() {
    }

    protected PyBaseRefactoringAction(LocalizeValue text, LocalizeValue description) {
        super(text, description);
    }

    protected abstract boolean isEnabledOnElementInsideEditor(PsiElement element, Editor editor, PsiFile file, DataContext context);

    @Override
    protected final boolean isAvailableOnElementInEditorAndFile(PsiElement element, Editor editor, PsiFile file, DataContext context) {
        return isEnabledOnElementInsideEditor(element, editor, file, context);
    }

    protected abstract boolean isEnabledOnElementsOutsideEditor(PsiElement[] elements);

    @Override
    protected final boolean isEnabledOnElements(PsiElement[] elements) {
        return isEnabledOnElementsOutsideEditor(elements);
    }

    @Override
    protected final boolean isAvailableForLanguage(Language language) {
        return language.isKindOf(PythonLanguage.getInstance());
    }

    @Override
    protected boolean isAvailableForFile(PsiFile file) {
        return isAvailableForLanguage(file.getLanguage()) && !isLibraryFile(file);
    }

    private static boolean isLibraryFile(PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null && ProjectRootManager.getInstance(file.getProject()).getFileIndex().isInLibraryClasses(virtualFile)) {
            return true;
        }
        return false;
    }
}
