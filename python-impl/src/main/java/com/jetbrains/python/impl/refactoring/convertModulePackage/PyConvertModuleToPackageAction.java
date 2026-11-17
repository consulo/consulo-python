// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.convertModulePackage;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyFile;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.IdeActions;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * @author Mikhail Golubev
 */
@ActionImpl(
    id = "PyConvertModuleToPackage",
    parents = @ActionParentRef(value = @ActionRef(id = IdeActions.GROUP_REFACTOR), anchor = ActionRefAnchor.LAST)
)
public class PyConvertModuleToPackageAction extends PyBaseConvertModulePackageAction {
    public static final String ID = "py.refactoring.convert.module.to.package";
    private static final Logger LOG = Logger.getInstance(PyConvertModuleToPackageAction.class);

    public PyConvertModuleToPackageAction() {
        super(PyLocalize.actionPyconvertmoduletopackageText(), PyLocalize.actionPyconvertmoduletopackageDescription());
    }

    @Override
    protected boolean isEnabledOnElementsOutsideEditor(PsiElement[] elements) {
        if (elements.length == 1) {
            return elements[0] instanceof PyFile pyFile && !PyUtil.isPackage(pyFile);
        }
        return false;
    }

    @Override
    protected @Nullable RefactoringActionHandler getHandler(DataContext dataContext) {
        return new RefactoringActionHandler() {
            @Override
            @RequiredUIAccess
            public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
                createPackageFromModule((PyFile) file);
            }

            @Override
            @RequiredUIAccess
            public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
                createPackageFromModule(((PyFile) elements[0]));
            }
        };
    }

    public void createPackageFromModule(PyFile file) {
        VirtualFile vFile = file.getVirtualFile();
        VirtualFile parentDir = vFile.getParent();
        String newPackageName = vFile.getNameWithoutExtension();
        VirtualFile existing = parentDir.findChild(newPackageName);
        if (existing != null) {
            showFileExistsErrorMessage(existing, ID, file.getProject());
            return;
        }
        WriteCommandAction.runWriteCommandAction(file.getProject(), () -> {
            try {
                VirtualFile packageDir = parentDir.createChildDirectory(this, newPackageName);
                vFile.move(this, packageDir);
                vFile.rename(this, PyNames.INIT_DOT_PY);
            }
            catch (IOException e) {
                LOG.error(e);
            }
        });
    }
}
