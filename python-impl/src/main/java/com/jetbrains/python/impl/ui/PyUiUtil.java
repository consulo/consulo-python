// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.ui;

import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.EditorFactory;
import consulo.codeEditor.EditorHighlighter;
import consulo.language.editor.highlight.EditorHighlighterFactory;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

/**
 * Assorted UI-related utility methods for Python.
 *
 * @see com.jetbrains.python.impl.psi.PyUtil for Python code insight utilities.
 */
public final class PyUiUtil {

    /**
     * Force re-highlighting in all open editors that belong to specified project.
     */
    public static void rehighlightOpenEditors(final Project project) {
        Application.get().runWriteAction(() -> {

            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
                if (editor instanceof EditorEx && editor.getProject() == project) {
                    final VirtualFile vFile = ((EditorEx) editor).getVirtualFile();
                    if (vFile != null) {
                        final EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, vFile);
                        ((EditorEx) editor).setHighlighter(highlighter);
                    }
                }
            }
        });
    }
}
