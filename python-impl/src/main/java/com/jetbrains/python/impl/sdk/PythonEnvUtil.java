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
package com.jetbrains.python.impl.sdk;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Sets;
import consulo.util.lang.StringUtil;
import consulo.process.local.EnvironmentUtil;

/**
 * @author traff
 */
public class PythonEnvUtil
{
	@SuppressWarnings("SpellCheckingInspection")
	public static final String PYTHONPATH = "PYTHONPATH";
	@SuppressWarnings("SpellCheckingInspection")
	public static final String PYTHONUNBUFFERED = "PYTHONUNBUFFERED";
	@SuppressWarnings("SpellCheckingInspection")
	public static final String PYTHONIOENCODING = "PYTHONIOENCODING";
	@SuppressWarnings("SpellCheckingInspection")
	public static final String IPYTHONENABLE = "IPYTHONENABLE";
	@SuppressWarnings("SpellCheckingInspection")
	public static final String PYTHONDONTWRITEBYTECODE = "PYTHONDONTWRITEBYTECODE";
	@SuppressWarnings("SpellCheckingInspection")
	public static final String PYVENV_LAUNCHER = "__PYVENV_LAUNCHER__";

	private PythonEnvUtil()
	{
	}

	public static Map<String, String> setPythonUnbuffered(@Nonnull Map<String, String> env)
	{
		env.put(PYTHONUNBUFFERED, "1");
		return env;
	}

	public static Map<String, String> setPythonIOEncoding(@Nonnull Map<String, String> env, @Nonnull String encoding)
	{
		env.put(PYTHONIOENCODING, encoding);
		return env;
	}

	/**
	 * Resets the environment variables that affect the way the Python interpreter searches for its settings and libraries.
	 */
	public static Map<String, String> resetHomePathChanges(@Nonnull String homePath, @Nonnull Map<String, String> env)
	{
		if(System.getenv(PYVENV_LAUNCHER) != null || EnvironmentUtil.getEnvironmentMap().containsKey(PYVENV_LAUNCHER))
		{
			env.put(PYVENV_LAUNCHER, homePath);
		}
		return env;
	}

	/**
	 * Appends a value to the end os a path-like environment variable, using system-dependent path separator.
	 *
	 * @param source path-like string to append to
	 * @param value  what to append
	 * @return modified path-like string
	 */
	@Nonnull
	public static String appendToPathEnvVar(@Nullable String source, @Nonnull String value)
	{
		if(StringUtil.isEmpty(source))
		{
			return value;
		}
		Set<String> paths = Sets.newHashSet(source.split(File.pathSeparator));
		return !paths.contains(value) ? source + File.pathSeparator + value : source;
	}

	public static void addPathsToEnv(@Nonnull Map<String, String> env, String key, @Nonnull Collection<String> values)
	{
		for(String val : values)
		{
			addPathToEnv(env, key, val);
		}
	}

	public static void addPathToEnv(@Nonnull Map<String, String> env, String key, String value)
	{
		if(!StringUtil.isEmpty(value))
		{
			if(env.containsKey(key))
			{
				env.put(key, appendToPathEnvVar(env.get(key), value));
			}
			else
			{
				env.put(key, value);
			}
		}
	}

	public static void addToPythonPath(@Nonnull Map<String, String> env, @Nonnull Collection<String> values)
	{
		addPathsToEnv(env, PYTHONPATH, values);
	}

	public static void addToPythonPath(@Nonnull Map<String, String> env, String value)
	{
		addPathToEnv(env, PYTHONPATH, value);
	}

	public static void mergePythonPath(@Nonnull Map<String, String> from, @Nonnull Map<String, String> to)
	{
		String value = from.get(PYTHONPATH);
		if(value != null)
		{
			Set<String> paths = Sets.newHashSet(value.split(File.pathSeparator));
			addToPythonPath(to, paths);
		}
	}

	@Nonnull
	public static Map<String, String> setPythonDontWriteBytecode(@Nonnull Map<String, String> env)
	{
		env.put(PYTHONDONTWRITEBYTECODE, "1");
		return env;
	}
}
