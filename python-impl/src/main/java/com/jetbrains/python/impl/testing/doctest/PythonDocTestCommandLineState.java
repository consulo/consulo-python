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
package com.jetbrains.python.impl.testing.doctest;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nonnull;

import consulo.execution.runner.ExecutionEnvironment;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.impl.testing.PythonTestCommandLineStateBase;

/**
 * User: catherine
 */
public class PythonDocTestCommandLineState extends PythonTestCommandLineStateBase
{
	private final PythonDocTestRunConfiguration myConfig;


	public PythonDocTestCommandLineState(PythonDocTestRunConfiguration runConfiguration, ExecutionEnvironment env)
	{
		super(runConfiguration, env);
		myConfig = runConfiguration;
	}

	@Override
	protected PythonHelper getRunner()
	{
		return PythonHelper.DOCSTRING;
	}

	@Nonnull
	protected List<String> getTestSpecs()
	{
		List<String> specs = new ArrayList<>();

		switch(myConfig.getTestType())
		{
			case TEST_SCRIPT:
				specs.add(myConfig.getScriptName());
				break;
			case TEST_CLASS:
				specs.add(myConfig.getScriptName() + "::" + myConfig.getClassName());
				break;
			case TEST_METHOD:
				specs.add(myConfig.getScriptName() + "::" + myConfig.getClassName() + "::" + myConfig.getMethodName());
				break;
			case TEST_FOLDER:
				if(!myConfig.getPattern().isEmpty())
				{
					specs.add(myConfig.getFolderName() + "/" + ";" + myConfig.getPattern());
				}
				else
				{
					specs.add(myConfig.getFolderName() + "/");
				}
				// TODO[kate]:think about delimiter between folderName and Pattern
				break;
			case TEST_FUNCTION:
				specs.add(myConfig.getScriptName() + "::::" + myConfig.getMethodName());
				break;
			default:
				throw new IllegalArgumentException("Unknown test type: " + myConfig.getTestType());
		}

		return specs;
	}
}
