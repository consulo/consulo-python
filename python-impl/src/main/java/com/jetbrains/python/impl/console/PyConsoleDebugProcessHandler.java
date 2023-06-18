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
package com.jetbrains.python.impl.console;

import com.jetbrains.python.debugger.PyPositionConverter;
import com.jetbrains.python.impl.debugger.PositionConverterProvider;
import com.jetbrains.python.impl.debugger.PyDebugProcess;
import com.jetbrains.python.impl.debugger.PyLocalPositionConverter;
import consulo.process.BaseProcessHandler;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.OutputStream;

/**
 * @author traff
 */
public class PyConsoleDebugProcessHandler extends BaseProcessHandler implements PositionConverterProvider
{
	private final ProcessHandler myConsoleProcessHandler;

	public PyConsoleDebugProcessHandler(final ProcessHandler processHandler)
	{
		myConsoleProcessHandler = processHandler;

		processHandler.addProcessListener(new ProcessListener()
		{
			@Override
			public void processTerminated(@Nonnull ProcessEvent event)
			{
				PyConsoleDebugProcessHandler.this.notifyProcessTerminated(event.getExitCode());
			}

			@Override
			public void onTextAvailable(@Nonnull ProcessEvent event, @Nonnull Key outputType)
			{
				PyConsoleDebugProcessHandler.this.notifyTextAvailable(event.getText(), outputType);
			}
		});
	}

	@Override
	protected void destroyProcessImpl()
	{
		detachProcessImpl();
	}

	@Override
	protected void detachProcessImpl()
	{
		notifyProcessTerminated(0);
		notifyTextAvailable("Debugger disconnected.\n", ProcessOutputTypes.SYSTEM);
	}

	@Override
	public boolean detachIsDefault()
	{
		return false;
	}

	@Override
	public OutputStream getProcessInput()
	{
		return null;
	}

	public ProcessHandler getConsoleProcessHandler()
	{
		return myConsoleProcessHandler;
	}

	@Nullable
	@Override
	public PyPositionConverter createPositionConverter(PyDebugProcess debugProcess)
	{
		if(myConsoleProcessHandler instanceof PositionConverterProvider)
		{
			return ((PositionConverterProvider) myConsoleProcessHandler).createPositionConverter(debugProcess);
		}
		else
		{
			return new PyLocalPositionConverter();
		}
	}
}
