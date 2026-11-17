// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.run.sphinx;

import java.util.Collections;

import consulo.execution.runner.ExecutionEnvironment;
import consulo.virtualFileSystem.RefreshQueue;
import consulo.virtualFileSystem.VirtualFile;
import com.jetbrains.python.impl.HelperPackage;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.rest.run.RestCommandLineState;

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
				RefreshQueue.getInstance().refresh(false, true, null, Collections.singleton(virtualFile));
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
