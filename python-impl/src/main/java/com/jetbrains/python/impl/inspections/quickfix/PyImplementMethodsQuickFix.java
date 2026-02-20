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

import com.jetbrains.python.impl.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorFactory;
import consulo.document.Document;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Set;

/**
 * @author ktisha
 */
public class PyImplementMethodsQuickFix implements LocalQuickFix {
    private final PyClass myClass;
    private final Set<PyFunction> myToImplement;

    public PyImplementMethodsQuickFix(PyClass aClass, Set<PyFunction> toBeImplemented) {
        myClass = aClass;
        myToImplement = toBeImplemented;
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameImplementMethods();
    }

    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        Editor editor = getEditor(project, descriptor.getPsiElement().getContainingFile());
        if (editor != null) {
            PyOverrideImplementUtil.chooseAndOverrideOrImplementMethods(
                project,
                editor,
                myClass,
                myToImplement,
                "Select Methods to Implement",
                true
            );
        }
    }

    @Nullable
    private static Editor getEditor(Project project, PsiFile file) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document != null) {
            EditorFactory instance = EditorFactory.getInstance();
            if (instance == null) {
                return null;
            }
            Editor[] editors = instance.getEditors(document);
            if (editors.length > 0) {
                return editors[0];
            }
        }
        return null;
    }
}
