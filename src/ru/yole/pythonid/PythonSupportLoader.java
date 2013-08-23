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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;

public class PythonSupportLoader
		implements ApplicationComponent {
	public static final PythonFileType PYTHON = new PythonFileType();

	public static PythonSupportLoader getInstance() {
		Application app = ApplicationManager.getApplication();
		return (PythonSupportLoader) app.getComponent(PythonSupportLoader.class);
	}

	public String getComponentName() {
		return "Python support loader";
	}

	public void initComponent() {
		ApplicationManager.getApplication().runWriteAction(new Runnable() {
			public void run() {
				FileTypeManager.getInstance().registerFileType(PythonSupportLoader.PYTHON, new String[]{"py"});
			}
		});
	}

	public void disposeComponent() {
	}
}