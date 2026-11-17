// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console.completion;

import com.jetbrains.python.impl.console.PyConsoleUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.action.TypedHandlerDelegate;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.util.dataholder.Key;

/**
 * @author oleg
 */
// TODO consulo: JB registers this handler with order="first, before completionAutoPopup"
@ExtensionImpl(id = "pydevBlockAutoPopup")
public final class PythonConsoleAutopopupBlockingHandler extends TypedHandlerDelegate {

    public static final Key<Object> REPL_KEY = new Key<>("python.repl.console.editor");

    @Override
    public Result checkAutoPopup(char charTyped, Project project, Editor editor, PsiFile file) {
        if (PyConsoleUtil.isCodeCompletionSuppressed(file) || editor.getUserData(REPL_KEY) != null) {
            return Result.DEFAULT;
        }
        return Result.CONTINUE;
    }
}
