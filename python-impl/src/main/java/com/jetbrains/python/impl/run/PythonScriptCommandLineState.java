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

import com.google.common.collect.Lists;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.impl.console.PyConsoleOptions;
import com.jetbrains.python.impl.console.PyConsoleType;
import com.jetbrains.python.impl.console.PydevConsoleRunner;
import com.jetbrains.python.impl.console.PydevConsoleRunnerImpl;
import com.jetbrains.python.impl.console.actions.ShowVarsAction;
import com.jetbrains.python.impl.sdk.PythonEnvUtil;
import consulo.content.bundle.Sdk;
import consulo.execution.DefaultExecutionResult;
import consulo.execution.ExecutionResult;
import consulo.execution.debug.DefaultDebugExecutor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.console.ConsoleExecuteAction;
import consulo.execution.ui.console.language.AbstractConsoleRunnerWithHistory;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.process.cmd.ParamsGroup;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;

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
  public ExecutionResult execute(Executor executor, CommandLinePatcher... patchers) throws ExecutionException {
    if (myConfig.showCommandLineAfterwards()) {
      if (executor.getId().equals(DefaultDebugExecutor.EXECUTOR_ID)) {
        return super.execute(executor, ArrayUtil.append(patchers,
                                                        commandLine -> commandLine.getParametersList()
                                                                                  .getParamsGroup(PythonCommandLineState.GROUP_DEBUGGER)
                                                                                  .addParameterAt(1, "--cmd-line")));
      }

      PydevConsoleRunner runner = new PythonScriptWithConsoleRunner(myConfig.getProject(),
                                                                    myConfig.getSdk(),
                                                                    PyConsoleType.PYTHON,
                                                                    myConfig.getWorkingDirectory(),
                                                                    myConfig.getEnvs(),
                                                                    patchers,
                                                                    PyConsoleOptions.getInstance(myConfig.getProject())
                                                                                    .getPythonConsoleSettings());

      runner.runSync();
      // runner.getProcessHandler() would be null if execution error occurred
      if (runner.getProcessHandler() == null) {
        return null;
      }
      List<AnAction> actions = Lists.newArrayList(createActions(runner.getConsoleView(), runner.getProcessHandler()));
      actions.add(new ShowVarsAction(runner.getConsoleView(), runner.getPydevConsoleCommunication()));

      return new DefaultExecutionResult(runner.getConsoleView(), runner.getProcessHandler(), actions.toArray(new AnAction[actions.size()]));
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
      commandLine.setWorkDirectory(myConfig.getWorkingDirectory());
    }
  }

  /**
   * @author traff
   */
  public class PythonScriptWithConsoleRunner extends PydevConsoleRunnerImpl {

    private CommandLinePatcher[] myPatchers;

    public PythonScriptWithConsoleRunner(@Nonnull Project project,
                                         @Nonnull Sdk sdk,
                                         @Nonnull PyConsoleType consoleType,
                                         @Nullable String workingDir,
                                         Map<String, String> environmentVariables,
                                         CommandLinePatcher[] patchers,
                                         PyConsoleOptions.PyConsoleSettings consoleSettings,
                                         String... statementsToExecute) {
      super(project, sdk, consoleType, workingDir, environmentVariables, consoleSettings, (s) -> {
      }, statementsToExecute);
      myPatchers = patchers;
    }

    @Override
    protected void createContentDescriptorAndActions() {
      AnAction a = new ConsoleExecuteAction(super.getConsoleView(),
                                            myConsoleExecuteActionHandler,
                                            myConsoleExecuteActionHandler.getEmptyExecuteAction(),
                                            myConsoleExecuteActionHandler);
      AbstractConsoleRunnerWithHistory.registerActionShortcuts(Lists.newArrayList(a), getConsoleView().getConsoleEditor().getComponent());
    }

    @Override
    protected GeneralCommandLine createCommandLine(@Nonnull Sdk sdk,
                                                   @Nonnull Map<String, String> environmentVariables,
                                                   String workingDir,
                                                   int[] ports) {
      GeneralCommandLine consoleCmdLine = doCreateConsoleCmdLine(sdk, environmentVariables, workingDir, ports, PythonHelper.RUN_IN_CONSOLE);

      GeneralCommandLine cmd = generateCommandLine(myPatchers);

      ParamsGroup group = consoleCmdLine.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
      assert group != null;
      group.addParameters(cmd.getParametersList().getList());

      PythonEnvUtil.mergePythonPath(consoleCmdLine.getEnvironment(), cmd.getEnvironment());

      consoleCmdLine.getEnvironment().putAll(cmd.getEnvironment());

      return consoleCmdLine;
    }
  }
}
