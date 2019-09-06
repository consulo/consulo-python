/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.console;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.wm.IdeFocusManager;
import com.jetbrains.python.PyBundle;
import icons.PythonIcons;

/**
 * @author traff
 */
public class PythonDebugLanguageConsoleView extends DuplexConsoleView<ConsoleView, PythonConsoleView> implements PyCodeExecutor
{

	public static final String DEBUG_CONSOLE_START_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))";

	public PythonDebugLanguageConsoleView(final Project project, Sdk sdk, ConsoleView consoleView)
	{
		super(consoleView, new PythonConsoleView(project, "Python Console", sdk));

		enableConsole(!PyConsoleOptions.getInstance(project).isShowDebugConsoleByDefault());

		getSwitchConsoleActionPresentation().setIcon(PythonIcons.Python.Debug.CommandLine);
		getSwitchConsoleActionPresentation().setText(PyBundle.message("run.configuration.show.command.line.action.name"));
	}

	public PythonDebugLanguageConsoleView(final Project project, Sdk sdk)
	{
		this(project, sdk, TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole());
	}

	@Override
	public void executeCode(@Nonnull String code, @Nullable Editor e)
	{
		enableConsole(false);
		getPydevConsoleView().executeCode(code, e);
	}

	@Nonnull
	public PythonConsoleView getPydevConsoleView()
	{
		return getSecondaryConsoleView();
	}

	public ConsoleViewImpl getTextConsole()
	{
		ConsoleView consoleView = getPrimaryConsoleView();
		if(consoleView instanceof ConsoleViewImpl)
		{
			return (ConsoleViewImpl) consoleView;
		}
		return null;
	}

	@Override
	public void enableConsole(boolean primary)
	{
		super.enableConsole(primary);

		if(!primary && !isPrimaryConsoleEnabled())
		{
			PythonConsoleView console = getPydevConsoleView();
			console.addConsoleFolding(true);
			console.showStartMessageForFirstExecution(DEBUG_CONSOLE_START_COMMAND);

			IdeFocusManager.findInstance().requestFocus(console.getConsoleEditor().getContentComponent(), true);
		}
	}
}