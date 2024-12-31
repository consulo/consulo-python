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
package com.jetbrains.python.impl;

import java.util.List;
import java.util.Map;

import jakarta.annotation.Nonnull;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParamsGroup;
import consulo.content.bundle.Sdk;

/**
 * @author traff
 */
public interface HelperPackage
{
	void addToPythonPath(@Nonnull Map<String, String> environment);

	/**
	 * @return entry (directory or ZIP archive) that will be added to <tt>PYTHONPATH</tt> environment variable before the process is started.
	 */
	@Nonnull
	String getPythonPathEntry();

	void addToGroup(@Nonnull ParamsGroup group, @Nonnull GeneralCommandLine cmd);

	/**
	 * @return the first parameter passed to Python interpreter that indicates which script to run. For scripts started as modules it's
	 * module name with <tt>-m</tt> flag, like <tt>-mpackage.module.name</tt>, and for average helpers it's full path to the script.
	 */
	@Nonnull
	String asParamString();

	@Nonnull
	GeneralCommandLine newCommandLine(@Nonnull String sdkPath, @Nonnull List<String> parameters);

	/**
	 * Version-sensitive version of {@link #newCommandLine(String, List)}. It adds additional directories with libraries inside python-helpers
	 * depending on the version of pythonSdk: either {@link PythonHelper#PY2_HELPER_DEPENDENCIES_DIR} or
	 * {@link PythonHelper#PY3_HELPER_DEPENDENCIES_DIR}.
	 *
	 * @param pythonSdk  Python SDK containing interpreter that will be used to run this helper
	 * @param parameters additional command line parameters of this helper
	 * @return instance {@link GeneralCommandLine} used to start the process
	 */
	@Nonnull
	GeneralCommandLine newCommandLine(@Nonnull Sdk pythonSdk, @Nonnull List<String> parameters);
}
