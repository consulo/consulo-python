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

package com.jetbrains.python.run;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.LineSeparator;

/**
 * @author VISTALL
 * @since 08-Nov-16
 */
public class PyVirtualEnvReader extends EnvironmentUtil.ShellEnvReader
{
	private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.run.PyVirtualEnvReader");

	private String activate;

	public PyVirtualEnvReader(String virtualEnvSdkPath)
	{
		try
		{
			activate = findActivateScript(virtualEnvSdkPath, getShell());
		}
		catch(Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	private static String findActivateScript(String path, String shellPath)
	{
		String shellName = shellPath != null ? new File(shellPath).getName() : null;

		File activate;
		if(SystemInfo.isWindows)
		{
			activate = new File(new File(path).getParentFile(), "activate.bat");
		}
		else
		{
			activate = ("fish".equals(shellName) || "csh".equals(shellName)) ? new File(new File(path).getParentFile(), "activate." + shellName) : new File(new File(path).getParentFile(),
					"activate");
		}

		return activate.exists() ? activate.getAbsolutePath() : null;
	}

	public String getActivate()
	{
		return activate;
	}

	@Nullable
	@Override
	protected String getShell() throws Exception
	{
		if(new File("/bin/bash").exists())
		{
			return "/bin/bash";
		}
		else if(new File("/bin/sh").exists())
		{
			return "/bin/sh";
		}
		else
		{
			return super.getShell();
		}
	}

	@Override
	protected List<String> getShellProcessCommand() throws Exception
	{
		String shellPath = getShell();

		if(shellPath == null || !new File(shellPath).canExecute())
		{
			throw new Exception("shell:" + shellPath);
		}

		return activate != null ? Arrays.asList(shellPath, "-c", ". '$activate'") : super.getShellProcessCommand();
	}

	@Override
	public Map<String, String> readShellEnv() throws Exception
	{
		if(SystemInfo.isUnix)
		{
			return super.readShellEnv();
		}
		else
		{
			if(activate != null)
			{
				return readVirtualEnvOnWindows(activate);
			}
			else
			{
				LOG.error("Can't find activate script for $virtualEnvSdkPath");
				return Collections.emptyMap();
			}
		}
	}

	private Map<String, String> readVirtualEnvOnWindows(String activate) throws Exception
	{
		File activateFile = FileUtil.createTempFile("pycharm-virualenv-activate.", ".bat", false);
		File envFile = FileUtil.createTempFile("pycharm-virualenv-envs.", ".tmp", false);
		try
		{
			FileUtil.copy(new File(activate), activateFile);
			FileUtil.appendToFile(activateFile, "\n\nset");
			List<String> command = Arrays.asList(activateFile.getPath(), ">", envFile.getAbsolutePath());

			return runProcessAndReadEnvs(command, envFile, LineSeparator.CRLF.getSeparatorString());
		}
		finally
		{
			FileUtil.delete(activateFile);
			FileUtil.delete(envFile);
		}
	}
}
