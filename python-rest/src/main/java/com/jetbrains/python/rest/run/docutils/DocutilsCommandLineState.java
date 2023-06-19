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
package com.jetbrains.python.rest.run.docutils;

import com.jetbrains.python.impl.HelperPackage;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.rest.run.RestCommandLineState;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.fileEditor.FileEditorManager;
import consulo.platform.Platform;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nullable;

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
