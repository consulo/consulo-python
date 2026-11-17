// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;

public class PyMakePublicQuickFix implements LocalQuickFix {

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixMakePublic();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = PyQuickFixUtil.dereference(descriptor.getPsiElement());
        if (element == null) {
            return;
        }
        if (element instanceof PyTargetExpression) {
            String name = ((PyTargetExpression)element).getName();
            if (name == null) {
                return;
            }
            VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
            if (virtualFile != null) {
                String publicName = StringUtil.trimLeading(name, '_');
                PythonUiService.getInstance().runRenameProcessor(project, element, publicName, false, false);
            }
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
