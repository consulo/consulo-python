// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl;

import com.jetbrains.python.impl.codeInsight.intentions.DeclarationConflictChecker;
import com.jetbrains.python.impl.inspections.PyRenameElementQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyImplementMethodsQuickFix;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ServiceImpl;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.refactoring.rename.RenameProcessor;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.module.Module;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.NotificationType;
import consulo.ui.ex.InputValidator;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.awt.MessageDialogBuilder;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.popup.Balloon;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.image.Image;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JFrame;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

@ServiceImpl
@Singleton
public final class PythonUiServiceImpl extends PythonUiService {
    @Override
    public void showBalloonInfo(Project project, String message) {
        showBalloon(project, message, NotificationType.INFO);
    }

    @Override
    public void showBalloonWarning(Project project, String message) {
        showBalloon(project, message, NotificationType.WARNING);
    }


    @Override
    public void showBalloonError(Project project, String message) {
        showBalloon(project, message, NotificationType.ERROR);
    }

    @Override
    public @Nullable FileEditor getSelectedEditor(Project project, VirtualFile virtualFile) {
        return FileEditorManager.getInstance(project).getSelectedEditor(virtualFile);
    }

    @Override
    public @Nullable Editor openTextEditor(Project project, PsiElement anchor) {
        PsiFile file = InjectedLanguageManager.getInstance(project).getTopLevelFile(anchor);
        return openTextEditor(project, file.getVirtualFile());
    }

    @Override
    public @Nullable Editor openTextEditor(Project project, VirtualFile virtualFile) {
        return FileEditorManager.getInstance(project).openTextEditor(
            OpenFileDescriptorFactory.getInstance(project).builder(virtualFile).build(), true);
    }

    @Override
    public @Nullable Editor openTextEditor(Project project, VirtualFile virtualFile, int offset) {
        return FileEditorManager.getInstance(project).openTextEditor(
            OpenFileDescriptorFactory.getInstance(project).builder(virtualFile).offset(offset).build(), true);
    }

    @Override
    public boolean showYesDialog(Project project, String message, String title) {
        return MessageDialogBuilder.yesNo(title, message).project(project).isYes();
    }

    @Override
    public @Nullable LocalQuickFix createPyRenameElementQuickFix(PsiElement element) {
        // TODO consulo: PyRenameElementQuickFix(PsiElement) missing - the quick fix renames the element of the problem descriptor
        return new PyRenameElementQuickFix();
    }

    @Override
    public @Nullable LocalQuickFix createInterpreterSettingsQuickFix(@Nullable Module module) {
        // TODO consulo: InterpreterSettingsQuickFix missing - JB: return new InterpreterSettingsQuickFix(module);
        return null;
    }

    //TODO: find a better place or, even better, port it to analysis module
    @Override
    public void runRenameProcessor(
        Project project,
        PsiElement element,
        String newName,
        boolean searchInComments,
        boolean searchTextOccurrences
    ) {
        new RenameProcessor(project, element, newName, searchInComments, searchTextOccurrences).run();
    }

    @Override
    public @Nullable LocalQuickFix createPyChangeSignatureQuickFixForMismatchingMethods(PyFunction function, PyFunction method) {
        // TODO consulo: PyChangeSignatureQuickFix.forMismatchingMethods missing -
        //  JB: return PyChangeSignatureQuickFix.forMismatchingMethods(function, method);
        return null;
    }

    @Override
    public @Nullable LocalQuickFix createPyChangeSignatureQuickFixForMismatchedCall(PyCallExpression.PyArgumentsMapping mapping) {
        // TODO consulo: PyChangeSignatureQuickFix.forMismatchedCall missing - JB: return PyChangeSignatureQuickFix.forMismatchedCall(mapping);
        return null;
    }

    @Override
    public @Nullable LocalQuickFix createPyImplementMethodsQuickFix(PyClass aClass, List<PyFunction> toImplement) {
        // TODO consulo: PyImplementMethodsQuickFix(PyClass, List) missing - the Set constructor is used
        return new PyImplementMethodsQuickFix(aClass, new LinkedHashSet<>(toImplement));
    }

    @Override
    public void annotateTypesIntention(Editor editor, PyFunction function) {
        // TODO consulo: PyAnnotateTypesIntention missing - JB: PyAnnotateTypesIntention.annotateTypes(editor, function);
    }

    /**
     * Shows an information balloon in a reasonable place at the top right of the window.
     *
     * @param project          our project
     * @param message          the text, HTML markup allowed
     * @param notificationType message type, changes the icon and the background.
     */
    private static void showBalloon(Project project, String message, NotificationType notificationType) {
        // ripped from com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier
        JFrame frame = WindowManager.getInstance().getFrame(project.isDefault() ? null : project);
        if (frame == null) {
            return;
        }
        JComponent component = frame.getRootPane();
        if (component == null) {
            return;
        }
        Rectangle rect = component.getVisibleRect();
        Point p = new Point(rect.x + rect.width - 10, rect.y + 10);
        RelativePoint point = new RelativePoint(component, p);

        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, notificationType, null)
            .setShowCallout(false).setCloseButtonEnabled(true)
            .createBalloon().show(point, Balloon.Position.atLeft);
    }

    @Override
    public void showPopup(Project project, List<String> items, String title, Consumer<String> callback) {
        DataManager.getInstance().getDataContextFromFocus().doWhenDone((Consumer<DataContext>) dataContext ->
            JBPopupFactory.getInstance().createPopupChooserBuilder(items)
                .setTitle(title)
                .setItemChosenCallback(callback)
                .setNamerForFiltering(o -> o)
                .createPopup()
                .showInBestPositionFor(dataContext));
    }

    /**
     * Shows a panel with name redefinition conflicts, if needed.
     *
     * @param conflicts what {@link DeclarationConflictChecker#findDefinitions} would return
     * @param obscured  name or its topmost qualifier that is obscured, used at top of pane.
     * @param name      full name (maybe qualified) to show as obscured and display as qualifier in "would be" chunks.
     * @return true iff conflicts is not empty and the panel is shown.
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean showConflicts(
        Project project,
        List<? extends Pair<PsiElement, PsiElement>> conflicts,
        String obscured,
        @Nullable String name
    ) {
        // Consulo keeps the JB logic and its NameUsage in DeclarationConflictChecker
        return DeclarationConflictChecker.showConflicts(project, (List<Pair<PsiElement, PsiElement>>) conflicts, obscured, name);
    }

    @Override
    public @Nullable String showInputDialog(
        @Nullable Project project,
        String message,
        String title,
        @Nullable String initialValue,
        @Nullable InputValidator validator
    ) {
        return Messages.showInputDialog(project, message,
            title, Messages.getQuestionIcon(), "", validator);
    }

    @Override
    public void showErrorHint(Editor editor, String message) {
        HintManager.getInstance().showErrorHint(editor, message);
    }

    @Override
    public int showChooseDialog(
        @Nullable Project project,
        @Nullable Component parentComponent,
        String message,
        String title,
        String[] values,
        String initialValue,
        @Nullable Image icon
    ) {
        // TODO consulo: MessagesService.showChooseDialog(project, parentComponent, ...) missing - Messages.showChooseDialog is used
        return Messages.showChooseDialog(message, title, values, initialValue, icon);
    }
}
