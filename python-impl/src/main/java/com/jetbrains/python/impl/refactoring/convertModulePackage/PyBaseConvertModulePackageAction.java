package com.jetbrains.python.impl.refactoring.convertModulePackage;

import com.jetbrains.python.impl.refactoring.PyBaseRefactoringAction;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.virtualFileSystem.VirtualFile;

/**
 * @author Mikhail Golubev
 */
public abstract class PyBaseConvertModulePackageAction extends PyBaseRefactoringAction {
    protected PyBaseConvertModulePackageAction(LocalizeValue text, LocalizeValue description) {
        super(text, description);
    }

    @Override
    protected final boolean isAvailableInEditorOnly() {
        return false;
    }

    /**
     * Show standard error dialog containing message about unexpected presense of given file or directory.
     *
     * @param file    file or directory to warn about
     * @param id      ID of refactoring as {@link CommonRefactoringUtil#showErrorMessage} requires
     * @param project active project
     */
    protected static void showFileExistsErrorMessage(VirtualFile file, String id, Project project) {
        LocalizeValue message;
        if (file.isDirectory()) {
            message = PyLocalize.refactoringErrorDirectoryExists(file.getName());
        }
        else {
            message = PyLocalize.refactoringErrorFileExists(file.getName());
        }
        CommonRefactoringUtil.showErrorMessage(RefactoringLocalize.errorTitle(), message, id, project);
    }

    @Override
    protected boolean isEnabledOnElementInsideEditor(PsiElement element, Editor editor, PsiFile file, DataContext context) {
        return false;
    }
}
