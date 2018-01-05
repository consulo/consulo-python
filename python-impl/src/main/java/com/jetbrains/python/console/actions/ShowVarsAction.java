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

package com.jetbrains.python.console.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.jetbrains.python.console.PydevConsoleCommunication;
import com.jetbrains.python.console.PythonConsoleView;

/**
 * @author VISTALL
 * @since 08-Nov-16
 */
public class ShowVarsAction extends ToggleAction implements DumbAware
{
	private boolean mySelected;
	private PythonConsoleView myConsoleView;
	private PydevConsoleCommunication myConsoleCommunication;

	public ShowVarsAction(PythonConsoleView consoleView, PydevConsoleCommunication consoleCommunication)
	{
		super("Show Variables", "Shows active console variables", AllIcons.Debugger.Watches);
		myConsoleView = consoleView;
		myConsoleCommunication = consoleCommunication;
	}

	@Override
	public boolean isSelected(AnActionEvent anActionEvent)
	{
		return mySelected;
	}

	@Override
	public void setSelected(AnActionEvent anActionEvent, boolean b)
	{
		mySelected = b;

		if(mySelected)
		{
			myConsoleView.showVariables(myConsoleCommunication);
		}
		else
		{
			myConsoleView.restoreWindow();
		}
	}
}
