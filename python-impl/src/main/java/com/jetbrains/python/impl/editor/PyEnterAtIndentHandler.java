/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.editor;

import com.jetbrains.python.psi.PyFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.dataContext.DataContext;
import consulo.language.editor.action.EditorBackspaceUtil;
import consulo.language.editor.action.EnterHandlerDelegateAdapter;
import consulo.language.editor.inject.EditorWindow;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiFile;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class PyEnterAtIndentHandler extends EnterHandlerDelegateAdapter {
    @Override
    public Result preprocessEnter(
        @Nonnull PsiFile file,
        @Nonnull Editor editor,
        @Nonnull SimpleReference<Integer> caretOffset,
        @Nonnull SimpleReference<Integer> caretAdvance,
        @Nonnull DataContext dataContext,
        EditorActionHandler originalHandler
    ) {
        int offset = caretOffset.get();
        if (editor instanceof EditorWindow) {
            file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
            editor = EditorWindow.getTopLevelEditor(editor);
            offset = editor.getCaretModel().getOffset();
        }
        if (!(file instanceof PyFile)) {
            return Result.Continue;
        }

        // honor dedent (PY-3009)
        if (EditorBackspaceUtil.isWhitespaceBeforeCaret(editor)) {
            return Result.DefaultSkipIndent;
        }
        return Result.Continue;
    }
}
