// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.actions;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.pyi.PyiFileType;
import consulo.application.dumb.DumbAware;
import consulo.ide.action.CreateFileFromTemplateAction;
import consulo.ide.action.CreateFileFromTemplateDialog;
import consulo.language.psi.PsiDirectory;
import consulo.localize.LocalizeValue;
import consulo.module.extension.ModuleExtension;
import consulo.project.Project;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.python.module.extension.PyModuleExtension;
import org.jspecify.annotations.Nullable;

// TODO consulo: NewFileActionWithCategory missing (getCategory() = "Python")
public final class CreatePythonFileAction extends CreateFileFromTemplateAction implements DumbAware {
    public CreatePythonFileAction() {
        super(
            PyCodeInsightLocalize.actionCreatePythonFileTitle(),
            PyCodeInsightLocalize.actionCreatePythonFileDescription(),
            PythonFileType.INSTANCE.getIcon()
        );
    }

    @Override
    protected void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder) {
        builder
            .setTitle(PyCodeInsightLocalize.createPythonFileActionNewPythonFile())
            .addKind(PyCodeInsightLocalize.createPythonFileActionPythonFile(), PythonFileType.INSTANCE.getIcon(), "Python Script")
            .addKind(PyCodeInsightLocalize.createPythonFileActionPythonUnitTest(), PythonFileType.INSTANCE.getIcon(), "Python Unit Test")
            .addKind(PyCodeInsightLocalize.createPythonFileActionPythonStub(), PyiFileType.INSTANCE.getIcon(), "Python Stub");
    }

    @Override
    protected LocalizeValue getActionName(PsiDirectory directory, String newName, String templateName) {
        return PyCodeInsightLocalize.createPythonFileScriptAction(newName);
    }

    @Override
    protected @Nullable Class<? extends ModuleExtension> getModuleExtensionClass() {
        return PyModuleExtension.class;
    }
}
