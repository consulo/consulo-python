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

package com.jetbrains.python.sdk.flavors;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.run.JythonProcessHandler;
import com.jetbrains.python.run.PythonCommandLineState;
import consulo.jython.JythonIcons;
import consulo.ui.image.Image;

/**
 * @author yole
 */
public class JythonSdkFlavor extends PythonSdkFlavor
{
	private static final String JYTHONPATH = "JYTHONPATH";
	private static final String PYTHON_PATH_PREFIX = "-Dpython.path=";

	private JythonSdkFlavor()
	{
	}

	public static String getPythonPathCmdLineArgument(Collection<String> path)
	{
		return PYTHON_PATH_PREFIX + StringUtil.join(appendSystemEnvPaths(path, JYTHONPATH), File.pathSeparator);
	}

	public boolean isValidSdkPath(@Nonnull File file)
	{
		return FileUtil.getNameWithoutExtension(file).toLowerCase().startsWith("jython");
	}

	@Override
	public String getVersionRegexp()
	{
		return "(Jython \\S+)( on .*)?";
	}

	@Override
	public String getVersionOption()
	{
		return "--version";
	}

	@Override
	public void initPythonPath(GeneralCommandLine cmd, Collection<String> path)
	{
		initPythonPath(path, cmd.getEnvironment());
		ParamsGroup paramGroup = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS);
		assert paramGroup != null;
		for(String param : paramGroup.getParameters())
		{
			if(param.startsWith(PYTHON_PATH_PREFIX))
			{
				return;
			}
		}
		paramGroup.addParameter(getPythonPathCmdLineArgument(path));
	}

	@Override
	public void initPythonPath(Collection<String> path, Map<String, String> env)
	{
		path = appendSystemEnvPaths(path, JYTHONPATH);
		final String jythonPath = StringUtil.join(path, File.pathSeparator);
		addToEnv(JYTHONPATH, jythonPath, env);
	}

	@Override
	public ProcessHandler createProcessHandler(GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException
	{
		return JythonProcessHandler.createProcessHandler(commandLine);
	}

	@Nonnull
	@Override
	public Collection<String> collectDebugPythonPath()
	{
		List<String> list = new ArrayList<String>(2);
		//that fixes Jython problem changing sys.argv on execfile, see PY-8164
		list.add(PythonHelpersLocator.getHelperPath("pycharm"));
		list.add(PythonHelpersLocator.getHelperPath("pydev"));
		return list;
	}

	@Nonnull
	@Override
	public String getName()
	{
		return "Jython";
	}

	@Override
	public Image getIcon()
	{
		return JythonIcons.Jython;
	}
}