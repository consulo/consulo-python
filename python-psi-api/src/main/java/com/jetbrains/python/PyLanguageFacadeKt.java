package com.jetbrains.python;

import com.jetbrains.python.psi.LanguageLevel;
import consulo.language.psi.PsiFile;
import consulo.virtualFileSystem.VirtualFile;

/**
 * Java counterpart of the top-level functions declared in JB {@code PyLanguageFacade.kt}.
 */
public final class PyLanguageFacadeKt {
    private PyLanguageFacadeKt() {
    }

    public static LanguageLevel getEffectiveLanguageLevel(PsiFile file) {
        PsiFile curFile = file;
        while (curFile != curFile.getOriginalFile()) {
            curFile = curFile.getOriginalFile();
        }
        VirtualFile virtualFile = curFile.getVirtualFile();
        if (virtualFile == null) {
            virtualFile = curFile.getViewProvider().getVirtualFile();
        }
        return PyLanguageFacade.getInstance().getEffectiveLanguageLevel(curFile.getProject(), virtualFile);
    }
}
