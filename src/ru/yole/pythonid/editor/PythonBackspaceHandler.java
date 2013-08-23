/*
 * Copyright 2006 Dmitry Jemerov (yole)
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

package ru.yole.pythonid.editor;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import ru.yole.pythonid.PythonSupportLoader;

public class PythonBackspaceHandler extends EditorActionHandler {
	private EditorActionHandler _oldHandler;

	public PythonBackspaceHandler(EditorActionHandler oldHandler) {
		this._oldHandler = oldHandler;
	}

	@Override
	public void execute(Editor editor, DataContext dataContext) {
		if ((!handleSmartBackspace(editor, dataContext)) &&
				(this._oldHandler != null))
			this._oldHandler.execute(editor, dataContext);
	}

	private boolean handleSmartBackspace(Editor editor, DataContext dataContext) {
		Project project = (Project) dataContext.getData("project");
		VirtualFile vFile = (VirtualFile) dataContext.getData("virtualFile");
		if ((project == null) || (vFile == null) || (!PythonSupportLoader.PYTHON.equals(vFile.getFileType()))) {
			return false;
		}
		if ((editor.getSelectionModel().hasSelection()) || (editor.getSelectionModel().hasBlockSelection())) {
			return false;
		}
		LogicalPosition caretPos = editor.getCaretModel().getLogicalPosition();
		if ((caretPos.line == 1) || (caretPos.column == 0)) {
			return false;
		}
		int lineStartOffset = editor.getDocument().getLineStartOffset(caretPos.line);
		int lineEndOffset = editor.getDocument().getLineEndOffset(caretPos.line);

		CharSequence charSeq = editor.getDocument().getCharsSequence();

		for (int pos = lineStartOffset; pos < lineEndOffset; pos++) {
			if ((charSeq.charAt(pos) != '\t') && (charSeq.charAt(pos) != ' ') && (charSeq.charAt(pos) != '\n')) {
				return false;
			}
		}

		CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
		int column = caretPos.column - settings.getIndentSize(PythonSupportLoader.PYTHON);
		if (column < 0) column = 0;
		editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(caretPos.line, column));
		return true;
	}
}