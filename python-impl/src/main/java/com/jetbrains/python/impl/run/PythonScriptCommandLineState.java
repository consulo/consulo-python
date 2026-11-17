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
package com.jetbrains.python.impl.run;

import com.jetbrains.python.impl.actions.PyExecuteInConsole;
import com.jetbrains.python.impl.actions.PyRunFileInConsoleAction;
import com.jetbrains.python.impl.console.PyConsoleOptions;
import consulo.application.Application;
import consulo.execution.ExecutionResult;
import consulo.execution.debug.DefaultDebugExecutor;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.util.ProgramParametersUtil;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.process.cmd.ParamsGroup;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
public class PythonScriptCommandLineState extends PythonCommandLineState {
  private final PythonRunConfiguration myConfig;

  public PythonScriptCommandLineState(PythonRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(runConfiguration, env);
    myConfig = runConfiguration;
  }

  @Override
  public @Nullable ExecutionResult execute(Executor executor, CommandLinePatcher... patchers) throws ExecutionException {
    Project project = myConfig.getProject();

    if (myConfig.showCommandLineAfterwards()) {
      if (!DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId()) && !DefaultRunExecutor.EXECUTOR_ID.equals(executor.getId())) {
        // disable "Show command line" for all executors except of Run and Debug, because it's useless
        return super.execute(executor, patchers);
      }

      PyRunFileInConsoleAction.configExecuted(myConfig);

      if (DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId())) {
        return super.execute(executor, ArrayUtil.append(patchers,
                                                        commandLine -> commandLine.getParametersList()
                                                                                  .getParamsGroup(PythonCommandLineState.GROUP_DEBUGGER)
                                                                                  .addParameterAt(1, "--cmd-line")));
      }

      final String runFileText = PythonConsoleScripts.buildScriptWithConsoleRun(myConfig);
      final boolean useExistingConsole = PyConsoleOptions.getInstance(project).isUseExistingConsole();
      Application.get().invokeLater(() -> {
        PyExecuteInConsole.executeCodeInConsole(project, runFileText, null, useExistingConsole, false, true, myConfig);
      });
      return null;
    }
    else {
      return super.execute(executor, patchers);
    }
  }

  @Override
  protected void buildCommandLineParameters(GeneralCommandLine commandLine) {
    ParametersList parametersList = commandLine.getParametersList();
    ParamsGroup exe_options = parametersList.getParamsGroup(GROUP_EXE_OPTIONS);
    assert exe_options != null;
    exe_options.addParametersString(myConfig.getInterpreterOptions());

    ParamsGroup script_parameters = parametersList.getParamsGroup(GROUP_SCRIPT);
    assert script_parameters != null;
    if (!StringUtil.isEmptyOrSpaces(myConfig.getScriptName())) {
      script_parameters.addParameter(myConfig.getScriptName());
    }

    String script_options_string = myConfig.getScriptParameters();
    if (script_options_string != null) {
      script_parameters.addParametersString(script_options_string);
    }

    if (!StringUtil.isEmptyOrSpaces(myConfig.getWorkingDirectory())) {
      setWorkingDirectory(commandLine, myConfig.getWorkingDirectory());
    }
  }

  public static String getExpandedWorkingDir(AbstractPythonRunConfiguration config) {
    final String workingDirectory = config.getWorkingDirectory();
    return ProgramParametersUtil.expandPath(workingDirectory, config.getModule(), config.getProject());
  }

  public static String getExpandedScriptName(PythonRunConfiguration config) {
    final String scriptName = config.getScriptName();
    return ProgramParametersUtil.expandPath(scriptName, config.getModule(), config.getProject());
  }
}
