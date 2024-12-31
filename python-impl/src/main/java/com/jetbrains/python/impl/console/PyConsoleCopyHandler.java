/*
 * Copyright 2013-2016 must-be.org
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

package com.jetbrains.python.impl.console;

import consulo.codeEditor.Caret;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.execution.ui.console.ConsoleViewUtil;
import consulo.ide.impl.idea.openapi.editor.richcopy.settings.RichCopySettings;
import consulo.ui.ex.awt.CopyPasteManager;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nullable;
import java.awt.datatransfer.StringSelection;

/**
 * @author VISTALL
 * @since 08-Nov-16
 */
public class PyConsoleCopyHandler extends EditorActionHandler {
  public static final Key<Integer> PROMPT_LENGTH_MARKER = Key.create("PROMPT_LENGTH_MARKER");

  private EditorActionHandler myOriginalHandler;

  public PyConsoleCopyHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (!RichCopySettings.getInstance().isEnabled()) {
      myOriginalHandler.execute(editor, null, dataContext);
      return;
    }
    if (editor.getUserData(ConsoleViewUtil.EDITOR_IS_CONSOLE_HISTORY_VIEW) != Boolean.TRUE || editor.getCaretModel()
                                                                                                    .getAllCarets()
                                                                                                    .size() != 1) {
      myOriginalHandler.execute(editor, null, dataContext);
      return;
    }
    doCopyWithoutPrompt((EditorEx)editor);
  }

  private void doCopyWithoutPrompt(EditorEx editor) {
    int start = editor.getSelectionModel().getSelectionStart();
    int end = editor.getSelectionModel().getSelectionEnd();
    Document document = editor.getDocument();
    int beginLine = document.getLineNumber(start);
    int endLine = document.getLineNumber(end);
    StringBuilder sb = new StringBuilder();
    for (int i = beginLine; i <= endLine; i++) {
      int lineStart = document.getLineStartOffset(i);
      Ref<Integer> r = Ref.create();
      editor.getMarkupModel().processRangeHighlightersOverlappingWith(lineStart, lineStart, rangeHighlighterEx -> {
        Integer data = rangeHighlighterEx.getUserData(PROMPT_LENGTH_MARKER);
        if (data == null) {
          return true;
        }
        r.set(data);
        return false;
      });

      if (!r.isNull()) {
        lineStart += r.get();
      }
      int rangeStart = Math.max(lineStart, start);
      int rangeEnd = Math.min(document.getLineEndOffset(i), end);
      if (rangeStart < rangeEnd) {
        sb.append(document.getText(new TextRange(rangeStart, rangeEnd)));
        sb.append("\n");
      }
    }

    if (sb.length() != 0) {
      CopyPasteManager.getInstance().setContents(new StringSelection(sb.toString()));
    }
  }
}
