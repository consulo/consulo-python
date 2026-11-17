/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.impl.codeInsight.implement;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.language.editor.generation.ImplementMethodHandler;
import consulo.language.psi.PsiFile;
import consulo.project.Project;

@ExtensionImpl
public class PyImplementMethodsHandler implements ImplementMethodHandler {

    @Override
    public boolean isValidFor(Editor editor, PsiFile file) {
        return editor != null && file instanceof PyFile && PyOverrideImplementUtil.getContextClass(editor, file) != null;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiFile psiFile) {
        PyClass cls = PyOverrideImplementUtil.getContextClass(editor, psiFile);
        if (cls != null) {
            PyOverrideImplementUtil.chooseAndImplementMethods(project, editor, cls, TypeEvalContext.codeCompletion(project, psiFile));
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
