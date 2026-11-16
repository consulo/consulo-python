package com.jetbrains.python;

import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

@ServiceAPI(ComponentScope.APPLICATION)
public abstract class PyLanguageFacade {
    public abstract LanguageLevel getEffectiveLanguageLevel(PsiElement psiElement);

    public abstract LanguageLevel getEffectiveLanguageLevel(Project project, VirtualFile virtualFile);

    public abstract void setEffectiveLanguageLevel(VirtualFile virtualFile, @Nullable LanguageLevel languageLevel);

    public static PyLanguageFacade getInstance() {
        return Application.get().getInstance(PyLanguageFacade.class);
    }
}
