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

package com.jetbrains.python.run;

import java.nio.charset.Charset;

import javax.annotation.Nonnull;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.RunnerMediator;
import com.intellij.openapi.util.SystemInfo;

/**
 * @author traff
 */
public class PythonProcessHandler extends KillableColoredProcessHandler
{
	protected PythonProcessHandler(@Nonnull Process process, @Nonnull GeneralCommandLine commandLine)
	{
		super(process, commandLine.getCommandLineString());
	}

	public PythonProcessHandler(Process process, String commandLine, @Nonnull Charset charset)
	{
		super(process, commandLine, charset);
	}

	public static PythonProcessHandler createProcessHandler(GeneralCommandLine commandLine) throws ExecutionException
	{
		Process p = commandLine.createProcess();

		return new PythonProcessHandler(p, commandLine);
	}

	public static ProcessHandler createDefaultProcessHandler(GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException
	{
		if(withMediator && SystemInfo.isWindows)
		{
			return RunnerMediator.getInstance().createProcess(commandLine);
		}
		else
		{
			return PythonProcessHandler.createProcessHandler(commandLine);
		}
	}

	@Override
	protected boolean shouldDestroyProcessRecursively()
	{
		return true;
	}
}
