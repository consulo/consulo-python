// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.lexer.PythonEditorHighlighter;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.EditorHighlighter;
import consulo.colorScheme.EditorColorsScheme;
import consulo.language.editor.highlight.EditorHighlighterProvider;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyEditorHighlighterProvider implements EditorHighlighterProvider {
    @Override
    public EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                  FileType fileType, @Nullable VirtualFile virtualFile,
                                                  EditorColorsScheme colors) {
        return new PythonEditorHighlighter(colors, project, virtualFile);
    }

    @Override
    public FileType getFileType() {
        return PythonFileType.INSTANCE;
    }
}
