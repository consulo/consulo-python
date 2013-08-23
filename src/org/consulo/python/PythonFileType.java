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

package org.consulo.python;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PythonFileType extends LanguageFileType {
	public static final PythonFileType INSTANCE = new PythonFileType();

	public PythonFileType() {
		super(PythonLanguage.INSTANCE);
	}

	@Override
	@NotNull
	public String getName() {
		return "PYTHON";
	}

	@Override
	@NotNull
	public String getDescription() {
		return "Python script";
	}

	@Override
	@NotNull
	public String getDefaultExtension() {
		return "py";
	}

	@Override
	public Icon getIcon() {
		return PythonIcons.PythonFileType;
	}
}