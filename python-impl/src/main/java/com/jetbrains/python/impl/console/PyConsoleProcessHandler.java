/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import consulo.disposer.Disposer;
import consulo.process.BaseProcessHandler;
import consulo.process.ProcessHandler;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;

import javax.annotation.Nullable;
import java.io.OutputStream;

/**
 * @author traff
 */
public class PyConsoleProcessHandler extends BaseProcessHandler
{
	private final ProcessHandler myProcessHandler;
	private final PythonConsoleView myConsoleView;
	private final PydevConsoleCommunication myPydevConsoleCommunication;

	public PyConsoleProcessHandler(ProcessHandler processHandler,
								   PythonConsoleView consoleView,
								   PydevConsoleCommunication pydevConsoleCommunication)
	{
		myProcessHandler = processHandler;
		myConsoleView = consoleView;
		myPydevConsoleCommunication = pydevConsoleCommunication;

		processHandler.addProcessListener(new ProcessListener()
		{
			@Override
			public void onTextAvailable(ProcessEvent event, Key outputType)
			{
				String string = PyConsoleUtil.processPrompts(myConsoleView, StringUtil.convertLineSeparators(event.getText()));

				myConsoleView.print(string, outputType);
			}
		});

		Disposer.register(consoleView, () ->
		{
			if(!isProcessTerminated())
			{
				destroyProcess();
			}
		});
	}

	@Override
	protected void destroyProcessImpl()
	{
		myProcessHandler.destroyProcess();

		doCloseCommunication();
	}

	@Override
	protected void detachProcessImpl()
	{

	}

	@Override
	public boolean detachIsDefault()
	{
		return false;
	}

	@Nullable
	@Override
	public OutputStream getProcessInput()
	{
		return null;
	}

	@Override
	public boolean isSilentlyDestroyOnClose()
	{
		return !myPydevConsoleCommunication.isExecuting();
	}

	@Override
	public void startNotify()
	{
		super.startNotify();
		myProcessHandler.startNotify();
	}

	@Override
	public boolean isProcessTerminated()
	{
		return myProcessHandler.isProcessTerminated();
	}

	@Override
	public boolean isProcessTerminating()
	{
		return myProcessHandler.isProcessTerminating();
	}

	private void doCloseCommunication()
	{
		if(myPydevConsoleCommunication != null)
		{

			UIUtil.invokeAndWaitIfNeeded((Runnable) () ->
			{
				try
				{
					myPydevConsoleCommunication.close();
					Thread.sleep(300);
				}
				catch(Exception e1)
				{
					// Ignore
				}
			});

			// waiting for REPL communication before destroying process handler
		}
	}
}

