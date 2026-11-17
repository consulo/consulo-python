// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.codeInsight.override.PyMethodMember;
import com.jetbrains.python.impl.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;

import java.util.Collection;

public class PyImplementMethodsQuickFix extends LocalQuickFixOnPsiElement {

    private final Collection<PyFunction> myToImplement;

    public PyImplementMethodsQuickFix(PyClass cls, Collection<PyFunction> toImplement) {
        super(cls);
        myToImplement = toImplement;
    }

    @Override
    public LocalizeValue getText() {
        return PyLocalize.qfixNameImplementMethods();
    }

    @Override
    public void invoke(Project project, PsiFile psiFile, PsiElement startElement, PsiElement endElement) {
        Editor editor = PyQuickFixUtil.getEditor(psiFile);

        if (editor != null && startElement instanceof PyClass) {
            if (ApplicationManager.getApplication().isUnitTestMode()) {
                PyOverrideImplementUtil.overrideMethods(editor, (PyClass) startElement, ContainerUtil.map(myToImplement, PyMethodMember::new), true);
            }
            else {
                PyOverrideImplementUtil.chooseAndImplementMethods(project, editor, (PyClass) startElement, myToImplement);
            }
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
