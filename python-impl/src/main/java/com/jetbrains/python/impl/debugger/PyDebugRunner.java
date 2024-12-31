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
package com.jetbrains.python.impl.debugger;

import com.google.common.collect.Lists;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.impl.console.PydevConsoleRunnerFactory;
import com.jetbrains.python.impl.console.PythonConsoleView;
import com.jetbrains.python.impl.console.PythonDebugConsoleCommunication;
import com.jetbrains.python.impl.console.PythonDebugLanguageConsoleView;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.impl.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.impl.run.CommandLinePatcher;
import com.jetbrains.python.impl.run.DebugAwareConfiguration;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.document.FileDocumentManager;
import consulo.execution.ExecutionResult;
import consulo.execution.RunManager;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunProfile;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.WrappingRunConfiguration;
import consulo.execution.debug.DefaultDebugExecutor;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.GenericProgramRunner;
import consulo.execution.ui.ExecutionConsole;
import consulo.execution.ui.RunContentDescriptor;
import consulo.module.content.ProjectRootManager;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.process.cmd.ParamsGroup;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
@ExtensionImpl
public class PyDebugRunner extends GenericProgramRunner
{
	public static final String PY_DEBUG_RUNNER = "PyDebugRunner";

	@SuppressWarnings("SpellCheckingInspection")
	public static final String DEBUGGER_MAIN = "pydev/pydevd.py";
	public static final String CLIENT_PARAM = "--client";
	public static final String PORT_PARAM = "--port";
	public static final String FILE_PARAM = "--file";
	public static final String MODULE_PARAM = "--module";
	public static final String MULTIPROCESS_PARAM = "--multiprocess";
	public static final String IDE_PROJECT_ROOTS = "IDE_PROJECT_ROOTS";
	public static final String LIBRARY_ROOTS = "LIBRARY_ROOTS";
	public static final String PYTHON_ASYNCIO_DEBUG = "PYTHONASYNCIODEBUG";
	@SuppressWarnings("SpellCheckingInspection")
	public static final String GEVENT_SUPPORT = "GEVENT_SUPPORT";
	public static final String PYDEVD_FILTERS = "PYDEVD_FILTERS";
	public static final String PYDEVD_FILTER_LIBRARIES = "PYDEVD_FILTER_LIBRARIES";
	public static boolean isModule = false;

	@Override
	@Nonnull
	public String getRunnerId()
	{
		return PY_DEBUG_RUNNER;
	}

	@Override
	public boolean canRun(@Nonnull final String executorId, @Nonnull final RunProfile profile)
	{
		if(!DefaultDebugExecutor.EXECUTOR_ID.equals(executorId))
		{
			// If not debug at all
			return false;
		}
		/**
		 * Any python configuration is debuggable unless it explicitly declares itself as DebugAwareConfiguration and denies it
		 * with canRunUnderDebug == false
		 */

		if(profile instanceof WrappingRunConfiguration)
		{
			// If configuration is wrapper -- unwrap it and check
			return isDebuggable(((WrappingRunConfiguration<?>) profile).getPeer());
		}
		return isDebuggable(profile);
	}

	private static boolean isDebuggable(@Nonnull final RunProfile profile)
	{
		if(profile instanceof DebugAwareConfiguration)
		{
			// if configuration knows whether debug is allowed
			return ((DebugAwareConfiguration) profile).canRunUnderDebug();
		}
		if(profile instanceof AbstractPythonRunConfiguration)
		{
			// Any python configuration is debuggable
			return true;
		}
		// No even a python configuration
		return false;
	}


	protected XDebugSession createSession(@Nonnull RunProfileState state,
										  @Nonnull final ExecutionEnvironment environment) throws ExecutionException
	{
		FileDocumentManager.getInstance().saveAllDocuments();

		final PythonCommandLineState pyState = (PythonCommandLineState) state;

		Sdk sdk = pyState.getSdk();
		PyDebugSessionFactory sessionCreator = PyDebugSessionFactory.findExtension(sdk);
		if(sessionCreator != null)
		{
			return sessionCreator.createSession(this, pyState, environment);
		}

		final ServerSocket serverSocket = PythonCommandLineState.createServerSocket();
		final int serverLocalPort = serverSocket.getLocalPort();
		RunProfile profile = environment.getRunProfile();
		final ExecutionResult result =
				pyState.execute(environment.getExecutor(), createCommandLinePatchers(environment.getProject(), pyState, profile, serverLocalPort));

		return XDebuggerManager.getInstance(environment.getProject()).startSession(environment, session ->
		{
			PyDebugProcess pyDebugProcess = createDebugProcess(session, serverSocket, result, pyState);

			createConsoleCommunicationAndSetupActions(environment.getProject(), result, pyDebugProcess, session);
			return pyDebugProcess;
		});
	}

	@Nonnull
	protected PyDebugProcess createDebugProcess(@Nonnull XDebugSession session,
												ServerSocket serverSocket,
												ExecutionResult result,
												PythonCommandLineState pyState)
	{
		return new PyDebugProcess(session,
				serverSocket,
				result.getExecutionConsole(),
				result.getProcessHandler(),
				pyState.isMultiprocessDebug());
	}

	@Override
	protected RunContentDescriptor doExecute(@Nonnull RunProfileState state,
											 @Nonnull final ExecutionEnvironment environment) throws ExecutionException
	{
		XDebugSession session = createSession(state, environment);
		initSession(session, state, environment.getExecutor());
		return session.getRunContentDescriptor();
	}

	protected void initSession(XDebugSession session, RunProfileState state, Executor executor)
	{
	}

	public static int findIndex(List<String> paramList, String paramName)
	{
		for(int i = 0; i < paramList.size(); i++)
		{
			if(paramName.equals(paramList.get(i)))
			{
				return i + 1;
			}
		}
		return -1;
	}

	public static void createConsoleCommunicationAndSetupActions(@Nonnull final Project project,
																 @Nonnull final ExecutionResult result,
																 @Nonnull PyDebugProcess debugProcess,
																 @Nonnull XDebugSession session)
	{
		ExecutionConsole console = result.getExecutionConsole();
		if(console instanceof PythonDebugLanguageConsoleView)
		{
			ProcessHandler processHandler = result.getProcessHandler();

			initDebugConsoleView(project, debugProcess, (PythonDebugLanguageConsoleView) console, processHandler, session);
		}
	}

	public static PythonDebugConsoleCommunication initDebugConsoleView(Project project,
																	   PyDebugProcess debugProcess,
																	   PythonDebugLanguageConsoleView console,
																	   ProcessHandler processHandler,
																	   final XDebugSession session)
	{
		PythonConsoleView pythonConsoleView = console.getPydevConsoleView();
		PythonDebugConsoleCommunication debugConsoleCommunication = new PythonDebugConsoleCommunication(project, debugProcess);

		pythonConsoleView.setConsoleCommunication(debugConsoleCommunication);


		PydevDebugConsoleExecuteActionHandler consoleExecuteActionHandler =
				new PydevDebugConsoleExecuteActionHandler(pythonConsoleView, processHandler, debugConsoleCommunication);
		pythonConsoleView.setExecutionHandler(consoleExecuteActionHandler);

		debugProcess.getSession().addSessionListener(consoleExecuteActionHandler);
		// TODO new LanguageConsoleBuilder(pythonConsoleView).processHandler(processHandler).initActions(consoleExecuteActionHandler, "py");


		debugConsoleCommunication.addCommunicationListener(new ConsoleCommunicationListener()
		{
			@Override
			public void commandExecuted(boolean more)
			{
				session.rebuildViews();
			}

			@Override
			public void inputRequested()
			{
			}
		});

		return debugConsoleCommunication;
	}

	@Nullable
	public static CommandLinePatcher createRunConfigPatcher(RunProfileState state, RunProfile profile)
	{
		CommandLinePatcher runConfigPatcher = null;
		if(state instanceof PythonCommandLineState && profile instanceof AbstractPythonRunConfiguration)
		{
			runConfigPatcher = (AbstractPythonRunConfiguration) profile;
		}
		return runConfigPatcher;
	}

	public CommandLinePatcher[] createCommandLinePatchers(final Project project,
														  final PythonCommandLineState state,
														  RunProfile profile,
														  final int serverLocalPort)
	{
		return new CommandLinePatcher[]{
				createDebugServerPatcher(project, state, serverLocalPort),
				createRunConfigPatcher(state, profile)
		};
	}

	private CommandLinePatcher createDebugServerPatcher(final Project project,
														final PythonCommandLineState pyState,
														final int serverLocalPort)
	{
		return new CommandLinePatcher()
		{

			private void patchExeParams(ParametersList parametersList)
			{
				// we should remove '-m' parameter, but notify debugger of it
				// but we can't remove one parameter from group, so we create new parameters group
				ParamsGroup newExeParams = new ParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS);
				int exeParamsIndex =
						parametersList.getParamsGroups().indexOf(parametersList.getParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS));
				ParamsGroup exeParamsOld = parametersList.removeParamsGroup(exeParamsIndex);
				isModule = false;
				for(String param : exeParamsOld.getParameters())
				{
					if(!param.equals("-m"))
					{
						newExeParams.addParameter(param);
					}
					else
					{
						isModule = true;
					}
				}

				parametersList.addParamsGroupAt(exeParamsIndex, newExeParams);
			}


			@Override
			public void patchCommandLine(GeneralCommandLine commandLine)
			{
				// script name is the last parameter; all other params are for python interpreter; insert just before name
				ParametersList parametersList = commandLine.getParametersList();

				@SuppressWarnings("ConstantConditions") @Nonnull ParamsGroup debugParams =
						parametersList.getParamsGroup(PythonCommandLineState.GROUP_DEBUGGER);

				patchExeParams(parametersList);

				@SuppressWarnings("ConstantConditions") @Nonnull ParamsGroup exeParams =
						parametersList.getParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS);

				final PythonSdkFlavor flavor = pyState.getSdkFlavor();
				if(flavor != null)
				{
					assert exeParams != null;
					for(String option : flavor.getExtraDebugOptions())
					{
						exeParams.addParameter(option);
					}
				}

				assert debugParams != null;
				fillDebugParameters(project, debugParams, serverLocalPort, pyState, commandLine);
			}
		};
	}

	private void fillDebugParameters(@Nonnull Project project,
									 @Nonnull ParamsGroup debugParams,
									 int serverLocalPort,
									 @Nonnull PythonCommandLineState pyState,
									 @Nonnull GeneralCommandLine cmd)
	{
		PythonHelper.DEBUGGER.addToGroup(debugParams, cmd);

		configureDebugParameters(project, debugParams, pyState, cmd);


		configureDebugEnvironment(project, cmd.getEnvironment());

		configureDebugConnectionParameters(debugParams, serverLocalPort);
	}

	public static void configureDebugEnvironment(@Nonnull Project project, Map<String, String> environment)
	{
		if(PyDebuggerOptionsProvider.getInstance(project).isSupportGeventDebugging())
		{
			environment.put(GEVENT_SUPPORT, "True");
		}

		PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
		if(debuggerSettings.isSteppingFiltersEnabled())
		{
			environment.put(PYDEVD_FILTERS, debuggerSettings.getSteppingFiltersForProject(project));
		}
		if(debuggerSettings.isLibrariesFilterEnabled())
		{
			environment.put(PYDEVD_FILTER_LIBRARIES, "True");
		}

		PydevConsoleRunnerFactory.putIPythonEnvFlag(project, environment);

		addProjectRootsToEnv(project, environment);
		addSdkRootsToEnv(project, environment);
	}

	protected void configureDebugParameters(@Nonnull Project project,
											@Nonnull ParamsGroup debugParams,
											@Nonnull PythonCommandLineState pyState,
											@Nonnull GeneralCommandLine cmd)
	{
		if(pyState.isMultiprocessDebug())
		{
			//noinspection SpellCheckingInspection
			debugParams.addParameter("--multiproc");
		}

		configureCommonDebugParameters(project, debugParams);
	}

	public static void configureCommonDebugParameters(@Nonnull Project project, @Nonnull ParamsGroup debugParams)
	{
		if(isModule)
		{
			debugParams.addParameter(MODULE_PARAM);
		}

		if(ApplicationManager.getApplication().isUnitTestMode())
		{
			debugParams.addParameter("--DEBUG");
		}

		if(PyDebuggerOptionsProvider.getInstance(project).isSaveCallSignatures())
		{
			debugParams.addParameter("--save-signatures");
		}

		if(PyDebuggerOptionsProvider.getInstance(project).isSupportQtDebugging())
		{
			debugParams.addParameter("--qt-support");
		}
	}

	private static void configureDebugConnectionParameters(@Nonnull ParamsGroup debugParams, int serverLocalPort)
	{
		final String[] debuggerArgs = new String[]{
				CLIENT_PARAM,
				"127.0.0.1",
				PORT_PARAM,
				String.valueOf(serverLocalPort),
				FILE_PARAM
		};
		for(String s : debuggerArgs)
		{
			debugParams.addParameter(s);
		}
	}

	private static void addProjectRootsToEnv(@Nonnull Project project, @Nonnull Map<String, String> environment)
	{

		List<String> roots = Lists.newArrayList();
		for(VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentRoots())
		{
			roots.add(contentRoot.getPath());
		}

		environment.put(IDE_PROJECT_ROOTS, StringUtil.join(roots, File.pathSeparator));
	}

	private static void addSdkRootsToEnv(@Nonnull Project project, @Nonnull Map<String, String> environment)
	{
		final RunManager runManager = RunManager.getInstance(project);
		final RunnerAndConfigurationSettings selectedConfiguration = runManager.getSelectedConfiguration();
		if(selectedConfiguration != null)
		{
			final RunConfiguration configuration = selectedConfiguration.getConfiguration();
			if(configuration instanceof AbstractPythonRunConfiguration)
			{
				AbstractPythonRunConfiguration runConfiguration = (AbstractPythonRunConfiguration) configuration;
				final Sdk sdk = runConfiguration.getSdk();
				if(sdk != null)
				{
					List<String> roots = Lists.newArrayList();
					for(VirtualFile contentRoot : sdk.getSdkModificator().getRoots(BinariesOrderRootType.getInstance()))
					{
						roots.add(contentRoot.getPath());
					}
					environment.put(LIBRARY_ROOTS, StringUtil.join(roots, File.pathSeparator));
				}
			}
		}
	}
}
