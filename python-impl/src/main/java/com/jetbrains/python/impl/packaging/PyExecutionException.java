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
package com.jetbrains.python.impl.packaging;

import consulo.process.ExecutionException;
import consulo.process.util.ProcessOutput;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyExecutionException extends ExecutionException
{
	@Nonnull
	private String myCommand;
	@Nonnull
	private List<String> myArgs;
	@Nonnull
	private final String myStdout;
	@Nonnull
	private final String myStderr;
	private final int myExitCode;
	@Nonnull
	private final List<? extends PyExecutionFix> myFixes;

	public PyExecutionException(@Nonnull String message, @Nonnull String command, @Nonnull List<String> args)
	{
		this(message, command, args, "", "", 0, Collections.<PyExecutionFix>emptyList());
	}

	public PyExecutionException(@Nonnull String message, @Nonnull String command, @Nonnull List<String> args, @Nonnull ProcessOutput output)
	{
		this(message, command, args, output.getStdout(), output.getStderr(), output.getExitCode(), Collections.<PyExecutionFix>emptyList());
	}

	public PyExecutionException(@Nonnull String message,
			@Nonnull String command,
			@Nonnull List<String> args,
			@Nonnull String stdout,
			@Nonnull String stderr,
			int exitCode,
			@Nonnull List<? extends PyExecutionFix> fixes)
	{
		super(message);
		myCommand = command;
		myArgs = args;
		myStdout = stdout;
		myStderr = stderr;
		myExitCode = exitCode;
		myFixes = fixes;
	}

	@Override
	public String toString()
	{
		final StringBuilder b = new StringBuilder();
		b.append("The following command was executed:\n\n");
		final String command = getCommand() + " " + StringUtil.join(getArgs(), " ");
		b.append(command);
		b.append("\n\n");
		b.append("The exit code: ").append(myExitCode).append("\n");
		b.append("The error output of the command:\n\n");
		b.append(myStdout);
		b.append("\n");
		b.append(myStderr);
		b.append("\n");
		b.append(getMessage());
		return b.toString();
	}

	@Nonnull
	public String getCommand()
	{
		return myCommand;
	}

	@Nonnull
	public List<String> getArgs()
	{
		return myArgs;
	}

	@Nonnull
	public List<? extends PyExecutionFix> getFixes()
	{
		return myFixes;
	}

	public int getExitCode()
	{
		return myExitCode;
	}

	@Nonnull
	public String getStdout()
	{
		return myStdout;
	}

	@Nonnull
	public String getStderr()
	{
		return myStderr;
	}
}
