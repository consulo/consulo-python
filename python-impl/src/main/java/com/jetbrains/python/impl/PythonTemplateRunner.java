// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.TextEditor;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

public final class PythonTemplateRunner {

    public static void runTemplate(PsiFile file, TemplateBuilder builder) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
            return;
        }
        Editor editor = PythonUiService.getInstance().openTextEditor(file.getProject(), virtualFile);
        if (editor != null) {
            builder.run(editor, false);
        }
        else {
            // TODO consulo: TemplateBuilder.runNonInteractively missing
            // builder.runNonInteractively(false);
        }
    }

    public static void runTemplateInSelectedEditor(Project project, PsiElement anchor, TemplateBuilder builder) {
        VirtualFile virtualFile = anchor.getContainingFile().getVirtualFile();
        if (virtualFile == null) {
            return;
        }
        FileEditor editor = PythonUiService.getInstance().getSelectedEditor(project, virtualFile);
        if (editor instanceof TextEditor te) {
            builder.run(te.getEditor(), false);
        }
        else {
            // TODO consulo: TemplateBuilder.runNonInteractively missing
            // builder.runNonInteractively(false);
        }
    }

    public static void runInlineTemplate(Project project,
                                         PsiElement element,
                                         TemplateBuilder builder, int offset) {
        VirtualFile virtualFile = element.getContainingFile().getVirtualFile();
        if (virtualFile == null) {
            return;
        }
        Editor editor = PythonUiService.getInstance().openTextEditor(project, virtualFile, offset);
        if (editor != null) {
            Template template = builder.buildInlineTemplate();
            TemplateManager.getInstance(project).startTemplate(editor, template);
        }
        else {
            // TODO consulo: TemplateBuilder.runNonInteractively missing
            // builder.runNonInteractively(true);
        }
    }
}
