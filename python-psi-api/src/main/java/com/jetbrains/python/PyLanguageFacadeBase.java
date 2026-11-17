package com.jetbrains.python;

import com.jetbrains.python.ast.PyAstFile;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.language.file.inject.VirtualFileWindow;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

public abstract class PyLanguageFacadeBase extends PyLanguageFacade {
    @Override
    public final LanguageLevel getEffectiveLanguageLevel(PsiElement psiElement) {
        if (psiElement instanceof PsiDirectory directory) {
            return getEffectiveLanguageLevel(directory.getProject(), directory.getVirtualFile());
        }

        PsiFile containingFile = psiElement.getContainingFile();
        if (containingFile instanceof PyAstFile pyAstFile) {
            return pyAstFile.getLanguageLevel();
        }

        return LanguageLevel.getDefault();
    }

    @Override
    public final LanguageLevel getEffectiveLanguageLevel(Project project, VirtualFile virtualFile) {
        VirtualFile file = virtualFile;
        if (file instanceof VirtualFileWindow fileWindow) {
            file = fileWindow.getDelegate();
        }
        // TODO consulo: BackedVirtualFile.getOriginFileIfBacked missing
        return doGetEffectiveLanguageLevel(project, file);
    }

    protected abstract LanguageLevel doGetEffectiveLanguageLevel(Project project, VirtualFile virtualFile);
}
