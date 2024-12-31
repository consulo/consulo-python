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
package com.jetbrains.python.rest.sphinx;

import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.impl.ReSTService;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import com.jetbrains.python.impl.run.PythonProcessRunner;
import com.jetbrains.python.impl.run.PythonTracebackFilter;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.application.util.SystemInfo;
import consulo.content.bundle.Sdk;
import consulo.execution.RunContentExecutor;
import consulo.execution.process.ProcessTerminatedListener;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParamsGroup;
import consulo.project.Project;
import consulo.python.buildout.module.extension.BuildoutModuleExtension;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.jetbrains.python.impl.sdk.PythonEnvUtil.*;

/**
 * User : catherine
 * base command for the sphinx actions
 * asks for the "Sphinx documentation sources"
 */
public class SphinxBaseCommand
{

	protected boolean setWorkDir(Module module)
	{
		final ReSTService service = ReSTService.getInstance(module);
		String workDir = service.getWorkdir();
		if(workDir.isEmpty())
		{
			AskForWorkDir dialog = new AskForWorkDir(module.getProject());
			if(!dialog.showAndGet())
			{
				return false;
			}
			service.setWorkdir(dialog.getInputFile());
		}
		return true;
	}

	public static class AskForWorkDir extends DialogWrapper
	{
		private TextFieldWithBrowseButton myInputFile;
		private JPanel myPanel;

		private AskForWorkDir(Project project)
		{
			super(project);

			setTitle("Set Sphinx Working Directory: ");
			init();
			VirtualFile baseDir = project.getBaseDir();
			String path = baseDir != null ? baseDir.getPath() : "";
			myInputFile.setText(path);
			myInputFile.setEditable(false);
			myInputFile.addBrowseFolderListener("Choose sphinx working directory (containing makefile): ", null, project, FileChooserDescriptorFactory.createSingleFolderDescriptor());

			myPanel.setPreferredSize(new Dimension(600, 20));
		}

		@Override
		protected JComponent createCenterPanel()
		{
			return myPanel;
		}

		public String getInputFile()
		{
			return myInputFile.getText();
		}
	}

	public void execute(@Nonnull final Module module)
	{
		final Project project = module.getProject();

		try
		{
			if(!setWorkDir(module))
			{
				return;
			}
			final ProcessHandler process = createProcess(module);
			new RunContentExecutor(project, process).withFilter(new PythonTracebackFilter(project)).withTitle("reStructuredText").withRerun(() -> execute(module)).withAfterCompletion(getAfterTask
					(module)).run();
		}
		catch(ExecutionException e)
		{
			Messages.showErrorDialog(e.getMessage(), "ReStructuredText Error");
		}
	}

	@Nullable
	protected Runnable getAfterTask(final Module module)
	{
		return () -> {
			final ReSTService service = ReSTService.getInstance(module);
			LocalFileSystem.getInstance().refreshAndFindFileByPath(service.getWorkdir());
		};
	}

	private ProcessHandler createProcess(Module module) throws ExecutionException
	{
		GeneralCommandLine commandLine = createCommandLine(module, Collections.<String>emptyList());
		ProcessHandler handler = PythonProcessRunner.createProcess(commandLine, false);
		ProcessTerminatedListener.attach(handler);
		return handler;
	}

	protected GeneralCommandLine createCommandLine(Module module, List<String> params) throws ExecutionException
	{
		Sdk sdk = PythonSdkType.findPythonSdk(module);
		if(sdk == null)
		{
			throw new ExecutionException("No sdk specified");
		}

		ReSTService service = ReSTService.getInstance(module);

		String sdkHomePath = sdk.getHomePath();

		GeneralCommandLine cmd = new GeneralCommandLine();
		if(sdkHomePath != null)
		{
			final String runnerName = "sphinx-quickstart" + (SystemInfo.isWindows ? ".exe" : "");
			String executablePath = PythonSdkType.getExecutablePath(sdkHomePath, runnerName);
			if(executablePath != null)
			{
				cmd.setExePath(executablePath);
			}
			else
			{
				cmd = PythonHelper.LOAD_ENTRY_POINT.newCommandLine(sdkHomePath, new ArrayList<>());
			}
		}

		cmd.setWorkDirectory(service.getWorkdir().isEmpty() ? module.getProject().getBaseDir().getPath() : service.getWorkdir());
		PythonCommandLineState.createStandardGroups(cmd);
		ParamsGroup scriptParams = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
		assert scriptParams != null;

		if(params != null)
		{
			for(String p : params)
			{
				scriptParams.addParameter(p);
			}
		}

		final Map<String, String> env = cmd.getEnvironment();
		setPythonIOEncoding(env, "utf-8");
		setPythonUnbuffered(env);
		if(sdkHomePath != null)
		{
			resetHomePathChanges(sdkHomePath, env);
		}
		env.put("PYCHARM_EP_DIST", "Sphinx");
		env.put("PYCHARM_EP_NAME", "sphinx-quickstart");

		List<String> pathList = new ArrayList<>(PythonCommandLineState.getAddedPaths(sdk));
		pathList.addAll(PythonCommandLineState.collectPythonPath(module));

		PythonCommandLineState.initPythonPath(cmd, true, pathList, sdkHomePath);

		PythonSdkType.patchCommandLineForVirtualenv(cmd, sdkHomePath, true);
		BuildoutModuleExtension facet = ModuleUtilCore.getExtension(module, BuildoutModuleExtension.class);
		if(facet != null)
		{
			facet.patchCommandLineForBuildout(cmd);
		}

		return cmd;
	}

}
