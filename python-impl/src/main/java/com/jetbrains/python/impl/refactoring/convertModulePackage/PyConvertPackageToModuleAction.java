// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.convertModulePackage;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.psi.PyFile;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.IdeActions;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
@ActionImpl(
    id = "PyConvertPackageToModuleAction",
    parents = @ActionParentRef(value = @ActionRef(id = IdeActions.GROUP_REFACTOR), anchor = ActionRefAnchor.LAST)
)
public class PyConvertPackageToModuleAction extends PyBaseConvertModulePackageAction {
    private static final Logger LOG = Logger.getInstance(PyConvertPackageToModuleAction.class);
    private static final String ID = "py.refactoring.convert.package.to.module";

    public PyConvertPackageToModuleAction() {
        super(PyLocalize.actionPyconvertpackagetomoduleactionText(), PyLocalize.actionPyconvertpackagetomoduleactionDescription());
    }

    @Override
    protected boolean isEnabledOnElementsOutsideEditor(PsiElement[] elements) {
        if (elements.length == 1) {
            PsiDirectory pyPackage = getPackageDir(elements[0]);
            return pyPackage != null && !isSpecialDirectory(pyPackage);

        }
        return false;
    }

    private static @Nullable PsiDirectory getPackageDir(PsiElement elem) {
        if (elem instanceof PsiDirectory directory && PyUtil.isPackage(directory, null)) {
            return directory;
        }
        else if (elem instanceof PsiFile file && PyUtil.isPackage(file)) {
            return file.getParent();
        }
        return null;
    }

    private static boolean isSpecialDirectory(PsiDirectory element) {
        Module module = ModuleUtilCore.findModuleForPsiElement(element);
        return module == null || (PyUtil.getSourceRoots(module).contains(element.getVirtualFile()));
    }

    @Override
    protected @Nullable RefactoringActionHandler getHandler(DataContext dataContext) {
        return new RefactoringActionHandler() {
            @Override
            @RequiredUIAccess
            public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
                PsiDirectory pyPackage = getPackageDir(file);
                if (pyPackage != null) {
                    createModuleFromPackage(pyPackage);
                }
            }

            @Override
            @RequiredUIAccess
            public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
                if (elements.length == 1) {
                    PsiDirectory pyPackage = getPackageDir(elements[0]);
                    if (pyPackage != null) {
                        createModuleFromPackage(pyPackage);
                    }
                }
            }
        };
    }

    public void createModuleFromPackage(PsiDirectory pyPackage) {
        if (pyPackage.getParent() == null) {
            return;
        }

        String packageName = pyPackage.getName();
        if (!isEmptyPackage(pyPackage)) {
            CommonRefactoringUtil.showErrorMessage(
                RefactoringLocalize.errorTitle(),
                PyLocalize.refactoringConvertPackageToModuleErrorNotEmptyPackage(packageName),
                ID,
                pyPackage.getProject()
            );
            return;
        }
        VirtualFile parentDirVFile = pyPackage.getParent().getVirtualFile();
        String moduleName = packageName + PyNames.DOT_PY;
        VirtualFile existing = parentDirVFile.findChild(moduleName);
        if (existing != null) {
            showFileExistsErrorMessage(existing, ID, pyPackage.getProject());
            return;
        }
        PsiFile initPy = pyPackage.findFile(PyNames.INIT_DOT_PY);
        WriteCommandAction.runWriteCommandAction(pyPackage.getProject(), () -> {
            try {
                if (initPy != null) {
                    VirtualFile initPyVFile = initPy.getVirtualFile();
                    initPyVFile.rename(this, moduleName);
                    initPyVFile.move(this, parentDirVFile);
                }
                else {
                    PyClassRefactoringUtil.getOrCreateFile(parentDirVFile.getPath() + "/" + moduleName, pyPackage.getProject(), false);
                }
                pyPackage.getVirtualFile().delete(this);
            }
            catch (IOException e) {
                LOG.error(e);
            }
        });
    }

    private static boolean isEmptyPackage(PsiDirectory pyPackage) {
        PsiElement[] children = pyPackage.getChildren();
        if (children.length == 1) {
            PyFile onlyFile = as(children[0], PyFile.class);
            return onlyFile != null && onlyFile.getName().equals(PyNames.INIT_DOT_PY);
        }
        return children.length == 0;
    }
}
