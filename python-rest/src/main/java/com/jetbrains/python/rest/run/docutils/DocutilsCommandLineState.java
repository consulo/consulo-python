// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.run.docutils;

import com.jetbrains.python.impl.HelperPackage;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.rest.run.RestCommandLineState;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.fileEditor.FileEditorManager;
import consulo.platform.Platform;
import consulo.virtualFileSystem.VirtualFile;

import org.jspecify.annotations.Nullable;

/**
 * User : catherine
 */
public class DocutilsCommandLineState extends RestCommandLineState
{
	public DocutilsCommandLineState(DocutilsRunConfiguration configuration, ExecutionEnvironment env)
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
				if(myConfiguration.openInBrowser())
				{
					Platform.current().openInBrowser(virtualFile.getUrl());
				}
				else
				{
					FileEditorManager.getInstance(myConfiguration.getProject()).openFile(virtualFile, true);
				}
			}
		};
	}

	@Override
	protected HelperPackage getRunner()
	{
		return PythonHelper.REST_RUNNER;
	}

	@Override
	protected String getTask()
	{
		return myConfiguration.getTask();
	}

	@Override
	@Nullable
	protected String getKey()
	{
		return null;
	}
}
