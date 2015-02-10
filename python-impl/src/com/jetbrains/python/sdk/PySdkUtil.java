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

package com.jetbrains.python.sdk;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.remotesdk.RemoteCredentials;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NullableConsumer;
import com.intellij.util.containers.HashMap;

/**
 * A more flexible cousin of SdkVersionUtil.
 * Needs not to be instantiated and only holds static methods.
 *
 * @author dcheryasov
 *         Date: Apr 24, 2008
 *         Time: 1:19:47 PM
 */
public class PySdkUtil
{
	protected static final Logger LOG = Logger.getInstance("#com.jetbrains.python.sdk.SdkVersionUtil");

	// Windows EOF marker, Ctrl+Z
	public static final int SUBSTITUTE = 26;

	private PySdkUtil()
	{
		// explicitly none
	}

	/**
	 * Executes a process and returns its stdout and stderr outputs as lists of lines.
	 *
	 * @param homePath process run directory
	 * @param command  command to execute and its arguments
	 * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null.
	 */
	@NotNull
	public static ProcessOutput getProcessOutput(String homePath, @NonNls String[] command)
	{
		return getProcessOutput(homePath, command, -1);
	}

	/**
	 * Executes a process and returns its stdout and stderr outputs as lists of lines.
	 * Waits for process for possibly limited duration.
	 *
	 * @param homePath process run directory
	 * @param command  command to execute and its arguments
	 * @param timeout  how many milliseconds to wait until the process terminates; non-positive means inifinity.
	 * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null.
	 */
	@NotNull
	public static ProcessOutput getProcessOutput(String homePath, @NonNls String[] command, final int timeout)
	{
		return getProcessOutput(homePath, command, null, timeout);
	}

	/**
	 * Executes a process and returns its stdout and stderr outputs as lists of lines.
	 * Waits for process for possibly limited duration.
	 *
	 * @param homePath process run directory
	 * @param command  command to execute and its arguments
	 * @param addEnv   items are prepended to same-named values of inherited process environment.
	 * @param timeout  how many milliseconds to wait until the process terminates; non-positive means inifinity.
	 * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null.
	 */
	@NotNull
	public static ProcessOutput getProcessOutput(String homePath, @NonNls String[] command, @Nullable @NonNls String[] addEnv, final int timeout)
	{
		return getProcessOutput(homePath, command, addEnv, timeout, null, true);
	}

	/**
	 * Executes a process and returns its stdout and stderr outputs as lists of lines.
	 * Waits for process for possibly limited duration.
	 *
	 * @param homePath      process run directory
	 * @param command       command to execute and its arguments
	 * @param addEnv        items are prepended to same-named values of inherited process environment.
	 * @param timeout       how many milliseconds to wait until the process terminates; non-positive means infinity.
	 * @param stdin         the data to write to the process standard input stream
	 * @param needEOFMarker
	 * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null.
	 */
	@NotNull
	public static ProcessOutput getProcessOutput(String homePath,
			@NonNls String[] command,
			@Nullable @NonNls String[] addEnv,
			final int timeout,
			@Nullable byte[] stdin,
			boolean needEOFMarker)
	{
		final ProcessOutput failureOutput = new ProcessOutput();
		if(homePath == null || !new File(homePath).exists())
		{
			return failureOutput;
		}
		try
		{
			List<String> commands = new ArrayList<String>();
			if(SystemInfo.isWindows && StringUtil.endsWithIgnoreCase(command[0], ".bat"))
			{
				commands.add("cmd");
				commands.add("/c");
			}
			Collections.addAll(commands, command);
			String[] newEnv = buildAdditionalEnv(addEnv);
			Process process = Runtime.getRuntime().exec(ArrayUtil.toStringArray(commands), newEnv, new File(homePath));
			CapturingProcessHandler processHandler = new CapturingProcessHandler(process);
			if(stdin != null)
			{
				final OutputStream processInput = processHandler.getProcessInput();
				assert processInput != null;
				processInput.write(stdin);
				if(SystemInfo.isWindows && needEOFMarker)
				{
					processInput.write(SUBSTITUTE);
					processInput.flush();
				}
				else
				{
					processInput.close();
				}
			}
			return processHandler.runProcess(timeout);
		}
		catch(final IOException ex)
		{
			LOG.warn(ex);
			return new ProcessOutput()
			{
				@Override
				public String getStderr()
				{
					String err = super.getStderr();
					if(!StringUtil.isEmpty(err))
					{
						err += "\n" + ex.getMessage();
					}
					else
					{
						err = ex.getMessage();
					}
					return err;
				}
			};
		}
	}

	private static String[] buildAdditionalEnv(String[] addEnv)
	{
		String[] newEnv = null;
		if(addEnv != null)
		{
			Map<String, String> envMap = buildEnvMap(addEnv);
			newEnv = new String[envMap.size()];
			int i = 0;
			for(Map.Entry<String, String> entry : envMap.entrySet())
			{
				newEnv[i] = entry.getKey() + "=" + entry.getValue();
				i += 1;
			}
		}
		return newEnv;
	}

	public static Map<String, String> buildEnvMap(String[] addEnv)
	{
		Map<String, String> envMap = new HashMap<String, String>(System.getenv());
		// turn additional ent into map
		Map<String, String> addMap = new HashMap<String, String>();
		for(String envItem : addEnv)
		{
			int pos = envItem.indexOf('=');
			if(pos > 0)
			{
				String key = envItem.substring(0, pos);
				String value = envItem.substring(pos + 1, envItem.length());
				addMap.put(key, value);
			}
			else
			{
				LOG.warn(String.format("Invalid env value: '%s'", envItem));
			}
		}
		// fuse old and new
		for(Map.Entry<String, String> entry : addMap.entrySet())
		{
			final String key = entry.getKey();
			final String value = entry.getValue();
			final String oldValue = envMap.get(key);
			if(oldValue != null)
			{
				envMap.put(key, value + oldValue);
			}
			else
			{
				envMap.put(key, value);
			}
		}
		return envMap;
	}

	public static boolean isRemote(@Nullable Sdk sdk)
	{
		return sdk != null && sdk.getSdkAdditionalData() instanceof RemoteCredentials;
	}

	public static boolean isElementInSkeletons(@NotNull final PsiElement element)
	{
		final PsiFile file = element.getContainingFile();
		if(file != null)
		{
			final VirtualFile virtualFile = file.getVirtualFile();
			if(virtualFile != null)
			{
				final Sdk sdk = PythonSdkType.getSdk(element);
				if(sdk != null)
				{
					final VirtualFile skeletonsDir = PythonSdkType.findSkeletonsDir(sdk);
					if(skeletonsDir != null && VfsUtilCore.isAncestor(skeletonsDir, virtualFile, false))
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	public static void createSdk(@Nullable final com.intellij.openapi.project.Project project,
			final Sdk[] existingSdks,
			final NullableConsumer<Sdk> onSdkCreatedCallBack,
			final SdkType... sdkTypes)
	{
		if(sdkTypes.length == 0)
		{
			onSdkCreatedCallBack.consume(null);
			return;
		}
		final FileChooserDescriptor descriptor = createCompositeDescriptor(sdkTypes);
		if(SystemInfo.isMac)
		{
			descriptor.putUserData(PathChooserDialog.NATIVE_MAC_CHOOSER_SHOW_HIDDEN_FILES, Boolean.TRUE);
		}
		String suggestedPath = sdkTypes[0].suggestHomePath();
		VirtualFile suggestedDir = suggestedPath == null ? null : LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName
				(suggestedPath));
		FileChooser.chooseFiles(descriptor, project, suggestedDir, new FileChooser.FileChooserConsumer()
		{
			@Override
			public void consume(List<VirtualFile> selectedFiles)
			{
				for(SdkType sdkType : sdkTypes)
				{
					if(sdkType.isValidSdkHome(selectedFiles.get(0).getPath()))
					{
						onSdkCreatedCallBack.consume(SdkConfigurationUtil.setupSdk(existingSdks, selectedFiles.get(0), sdkType, false, false, null,
								null));
						return;
					}
				}
				onSdkCreatedCallBack.consume(null);
			}

			@Override
			public void cancelled()
			{
				onSdkCreatedCallBack.consume(null);
			}
		});
	}

	private static FileChooserDescriptor createCompositeDescriptor(final SdkType... sdkTypes)
	{
		FileChooserDescriptor descriptor0 = sdkTypes[0].getHomeChooserDescriptor();
		FileChooserDescriptor descriptor = new FileChooserDescriptor(descriptor0.isChooseFiles(), descriptor0.isChooseFolders(),
				descriptor0.isChooseJars(), descriptor0.isChooseJarsAsFiles(), descriptor0.isChooseJarContents(), descriptor0.isChooseMultiple())
		{

			@Override
			public void validateSelectedFiles(final VirtualFile[] files) throws Exception
			{
				if(files.length > 0)
				{
					for(SdkType type : sdkTypes)
					{
						if(type.isValidSdkHome(files[0].getPath()))
						{
							return;
						}
					}
				}
				String message = files.length > 0 && files[0].isDirectory() ? ProjectBundle.message("sdk.configure.home.invalid.error",
						sdkTypes[0].getPresentableName()) : ProjectBundle.message("sdk.configure.home.file.invalid.error",
						sdkTypes[0].getPresentableName());
				throw new Exception(message);
			}
		};
		descriptor.setTitle(descriptor0.getTitle());
		return descriptor;
	}

	public static List<String> filterExistingPaths(SdkType sdkType, Collection<String> sdkHomes, final Sdk[] sdks)
	{
		List<String> result = new ArrayList<String>();
		for(String sdkHome : sdkHomes)
		{
			if(findByPath(sdkType, sdks, sdkHome) == null)
			{
				result.add(sdkHome);
			}
		}
		return result;
	}

	@Nullable
	private static Sdk findByPath(SdkType sdkType, Sdk[] sdks, String sdkHome)
	{
		for(Sdk sdk : sdks)
		{
			if(sdk.getSdkType() == sdkType && FileUtil.pathsEqual(FileUtil.toSystemIndependentName(sdk.getHomePath()),
					FileUtil.toSystemIndependentName(sdkHome)))
			{
				return sdk;
			}
		}
		return null;
	}
}
