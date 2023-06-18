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
package com.jetbrains.rest.run.sphinx;

import java.util.Collections;

import consulo.execution.runner.ExecutionEnvironment;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.jetbrains.python.impl.HelperPackage;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.rest.run.RestCommandLineState;

/**
 * User : catherine
 */
public class SphinxCommandLineState extends RestCommandLineState
{

	public SphinxCommandLineState(SphinxRunConfiguration configuration, ExecutionEnvironment env)
	{
		super(configuration, env);
	}

	@Override
	protected Runnable getAfterTask()
	{
		return () -> {
			VirtualFile virtualFile = findOutput();
			if(virtualFile != null)
			{
				LocalFileSystem.getInstance().refreshFiles(Collections.singleton(virtualFile), false, true, null);
			}
		};
	}

	@Override
	protected HelperPackage getRunner()
	{
		return PythonHelper.SPHINX_RUNNER;
	}

	@Override
	protected String getKey()
	{
		return "-b";
	}

	@Override
	protected String getTask()
	{
		return myConfiguration.getTask().trim();
	}
}
