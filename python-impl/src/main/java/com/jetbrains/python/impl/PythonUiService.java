// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditor;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.psi.PsiElement;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.ex.InputValidator;
import consulo.ui.image.Image;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.awt.Component;
import java.util.List;
import java.util.function.Consumer;

@ServiceAPI(ComponentScope.APPLICATION)
public class PythonUiService {

    public void showBalloonInfo(Project project, String message) {
    }

    public void showBalloonWarning(Project project, String message) {
    }

    public void showBalloonError(Project project, String message) {
    }

    public @Nullable FileEditor getSelectedEditor(Project project, VirtualFile virtualFile) {
        return null;
    }

    public @Nullable Editor openTextEditor(Project project, PsiElement anchor) {
        return null;
    }

    public @Nullable Editor openTextEditor(Project project, VirtualFile virtualFile) {
        return null;
    }

    public @Nullable Editor openTextEditor(Project project, VirtualFile virtualFile, int offset) {
        return null;
    }

    public boolean showYesDialog(Project project, String title, String message) {
        return false;
    }

    public void runRenameProcessor(Project project,
                                   PsiElement element,
                                   String newName,
                                   boolean searchInComments,
                                   boolean searchTextOccurrences) {
    }

    public @Nullable LocalQuickFix createPyChangeSignatureQuickFixForMismatchingMethods(PyFunction function, PyFunction method) {
        return null;
    }

    public @Nullable LocalQuickFix createPyChangeSignatureQuickFixForMismatchedCall(PyCallExpression.PyArgumentsMapping mapping) {
        return null;
    }


    public @Nullable LocalQuickFix createPyImplementMethodsQuickFix(PyClass aClass, List<PyFunction> toImplement) {
        return null;
    }

    public void annotateTypesIntention(Editor editor, PyFunction function) {
    }

    public void showErrorHint(Editor editor, String message) {
    }

    public static PythonUiService getInstance() {
        return Application.get().getInstance(PythonUiService.class);
    }

    public @Nullable LocalQuickFix createPyRenameElementQuickFix(PsiElement element) {
        return null;
    }

    public @Nullable LocalQuickFix createInterpreterSettingsQuickFix(@Nullable Module module) {
        return null;
    }

    public void showPopup(Project project, List<String> items, String title, Consumer<String> callback) {
    }

    /**
     * Shows a panel with name redefinition conflicts, if needed.
     *
     * @param conflicts what {@link #findDefinitions} would return
     * @param obscured  name or its topmost qualifier that is obscured, used at top of pane.
     * @param name      full name (maybe qualified) to show as obscured and display as qualifier in "would be" chunks.
     * @return true iff conflicts is not empty and the panel is shown.
     */
    public boolean showConflicts(Project project,
                                 List<? extends Pair<PsiElement, PsiElement>> conflicts,
                                 String obscured,
                                 @Nullable String name) {
        return false;
    }

    public @Nullable String showInputDialog(@Nullable Project project,
                                            String message,
                                            String title,
                                            @Nullable String initialValue,
                                            @Nullable InputValidator validator) {
        return null;
    }


    public int showChooseDialog(@Nullable Project project,
                                @Nullable Component parentComponent,
                                String message,
                                String title,
                                String[] values,
                                String initialValue,
                                @Nullable Image icon) {
        return -1;
    }
}
