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

package ru.yole.pythonid;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PythonFileType extends LanguageFileType {
	private Icon _icon;

	public PythonFileType() {
		super(new PythonLanguageImpl());
		this._icon = IconLoader.getIcon("python.png");
	}

	@NotNull
	public String getName() {
		String tmp2_0 = "Python";
		if (tmp2_0 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp2_0;
	}

	@NotNull
	public String getDescription() {
		String tmp2_0 = "Python script";
		if (tmp2_0 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp2_0;
	}

	@NotNull
	public String getDefaultExtension() {
		String tmp2_0 = "py";
		if (tmp2_0 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp2_0;
	}

	public Icon getIcon() {
		return this._icon;
	}
}