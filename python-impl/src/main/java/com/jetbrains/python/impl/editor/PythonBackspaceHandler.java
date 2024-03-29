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

import com.jetbrains.python.PythonFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.LogicalPosition;
import consulo.ide.impl.idea.openapi.editor.EditorModificationUtil;
import consulo.language.editor.action.BackspaceHandlerDelegate;
import consulo.language.editor.action.EditorBackspaceUtil;
import consulo.language.psi.PsiFile;

/**
 * @author yole
 */
@ExtensionImpl
public class PythonBackspaceHandler extends BackspaceHandlerDelegate {
  private LogicalPosition myTargetPosition;

  public void beforeCharDeleted(final char c, final PsiFile file, final Editor editor) {
    if (PythonFileType.INSTANCE != file.getFileType()) return;
    myTargetPosition = EditorBackspaceUtil.getBackspaceUnindentPosition(file, editor);
  }

  public boolean charDeleted(final char c, final PsiFile file, final Editor editor) {
    if (myTargetPosition != null) {
      // Remove all the following spaces before moving to targetPosition
      final int offset = editor.getCaretModel().getOffset();
      final int targetOffset = editor.logicalPositionToOffset(myTargetPosition);
      editor.getSelectionModel().setSelection(targetOffset, offset);
      EditorModificationUtil.deleteSelectedText(editor);
      editor.getCaretModel().moveToLogicalPosition(myTargetPosition);
      myTargetPosition = null;
      return true;
    }
    return false;
  }
}
