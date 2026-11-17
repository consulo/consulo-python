// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.PythonRuntimeService;
import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import consulo.language.Language;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import org.intellij.lang.annotations.Pattern;
import org.jspecify.annotations.Nullable;

public abstract class PyInspection extends LocalInspectionTool {
    @Pattern(VALID_ID_PATTERN)
    @Override
    public String getID() {
        //noinspection PatternValidation
        return getShortName(super.getID());
    }

    @Override
    public @Nullable Language getLanguage() {
        return PythonLanguage.getInstance();
    }

    /**
     * JB registers the group path as {@code INSP.GROUP.python} + groupKey; in Consulo the language display name ("Python")
     * is the first element of the group path, so subclasses return their JB groupKey here.
     */
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.inspectionGeneralToolsGroupName();
    }

    @Override
    public String getShortName() {
        return getClass().getSimpleName();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    @Override
    public boolean isSuppressedFor(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file instanceof PyFileImpl && !((PyFileImpl) file).isAcceptedFor(this.getClass())) {
            return true;
        }
        if (file instanceof PyFileImpl) {
            InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(element.getProject());
            if (isInjectedIntoNonPythonHost(injectedLanguageManager, file) && !hasPythonRuntime(injectedLanguageManager, file)) {
                return true;
            }
        }
        return isSuppressForCodeFragment(element) || super.isSuppressedFor(element);
    }

    private boolean isSuppressForCodeFragment(@Nullable PsiElement element) {
        return isSuppressForCodeFragment() && PsiTreeUtil.getParentOfType(element, PyExpressionCodeFragment.class) != null;
    }

    protected boolean isSuppressForCodeFragment() {
        return false;
    }

    private static boolean hasPythonRuntime(InjectedLanguageManager injectedLanguageManager, PsiFile containingFile) {
        PsiFile topLevelFile = injectedLanguageManager.getTopLevelFile(containingFile);
        // An inspection cannot suspend, so it peeks at the snapshot. Each new snapshot restarts the analysis.
        // TODO consulo: EvoPyProjectModelKt.findEvoPyProjectIfReady / EvoPyProject.getInterpreter() missing
        //final EvoPyProject evoPyProject = EvoPyProjectModelKt.findEvoPyProjectIfReady(topLevelFile, false);
        //if (evoPyProject != null && evoPyProject.getInterpreter() != null) {
        //  return true;
        //}

        PythonRuntimeService pythonRuntimeService = PythonRuntimeService.getInstance();
        return pythonRuntimeService.isInScratchFile(topLevelFile) || pythonRuntimeService.isExternallyIndexedFile(topLevelFile);
    }

    private static boolean isInjectedIntoNonPythonHost(InjectedLanguageManager injectedLanguageManager,
                                                       PsiFile containingFile) {
        PsiElement injectionHost = injectedLanguageManager.getInjectionHost(containingFile);
        if (injectionHost == null) {
            return false;
        }

        PsiFile topLevelFile = injectedLanguageManager.getTopLevelFile(containingFile);
        if (topLevelFile == containingFile) {
            return false;
        }

        return getBaseLanguageOrLanguage(topLevelFile) != getBaseLanguageOrLanguage(containingFile);
    }

    private static Language getBaseLanguageOrLanguage(PsiFile file) {
        Language language = file.getLanguage();
        Language baseLanguage = language.getBaseLanguage();
        return baseLanguage != null ? baseLanguage : language;
    }
}
