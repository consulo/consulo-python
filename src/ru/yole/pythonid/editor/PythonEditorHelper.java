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

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import org.jetbrains.annotations.NonNls;

public class PythonEditorHelper
		implements ApplicationComponent {
	private EditorActionManager _actionManager;
	private EditorActionHandler _oldBackspaceHandler;

	public PythonEditorHelper(EditorActionManager actionManager) {
		this._actionManager = actionManager;
	}

	@NonNls
	public String getComponentName() {
		return "PythonEditorHelper";
	}

	public void initComponent() {
		this._oldBackspaceHandler = this._actionManager.getActionHandler("EditorBackSpace");
		PythonBackspaceHandler handler = new PythonBackspaceHandler(this._oldBackspaceHandler);
		this._actionManager.setActionHandler("EditorBackSpace", handler);
	}

	public void disposeComponent() {
		this._actionManager.setActionHandler("EditorBackSpace", this._oldBackspaceHandler);
	}
}