package com.jetbrains.python.fixtures;

import java.util.List;

import com.google.common.collect.Lists;
import consulo.process.ExecutionException;
import consulo.execution.executor.Executor;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.process.cmd.GeneralCommandLine;
import consulo.execution.debug.DefaultDebugExecutor;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ExecutionEnvironmentBuilder;
import consulo.project.Project;
import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.impl.debugger.PyDebugRunner;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.impl.run.PythonCommandLineState;

/**
 * @author yole
 */
public abstract class PyCommandLineTestCase extends PyTestCase {
  private static final int PORT = 123;

  protected static int verifyPyDevDParameters(List<String> params) {
    params = Lists.newArrayList(params);
    int debugParam = params.remove("--DEBUG") ? 1 : 0;
    assertEquals(PythonHelpersLocator.getHelperPath("pydev/pydevd.py"), params.get(0));
    assertEquals("--multiproc", params.get(1));
    assertEquals("--client", params.get(2));
    assertEquals("--port", params.get(4));
    assertEquals("" + PORT, params.get(5));
    assertEquals("--file", params.get(6));
    return 7 + debugParam;
  }

  protected <T extends AbstractPythonRunConfiguration> T createConfiguration(ConfigurationType configurationType, Class<T> cls) {
    ConfigurationFactory factory = configurationType.getConfigurationFactories()[0];
    Project project = myFixture.getProject();
    return cls.cast(factory.createTemplateConfiguration(project));
  }

  protected List<String> buildRunCommandLine(AbstractPythonRunConfiguration configuration) {
    try {
      Executor executor = DefaultRunExecutor.getRunExecutorInstance();
      ExecutionEnvironment env = new ExecutionEnvironmentBuilder(myFixture.getProject(), executor).runProfile(configuration).build();
      PythonCommandLineState state = (PythonCommandLineState)configuration.getState(executor, env);
      GeneralCommandLine generalCommandLine = state.generateCommandLine();
      return generalCommandLine.getParametersList().getList();
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  protected List<String> buildDebugCommandLine(AbstractPythonRunConfiguration configuration) {
    try {
      Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
      ExecutionEnvironment env = new ExecutionEnvironmentBuilder(myFixture.getProject(), executor).runProfile(configuration).build();
      PythonCommandLineState state = (PythonCommandLineState)configuration.getState(executor, env);
      GeneralCommandLine generalCommandLine =
        state.generateCommandLine(new PyDebugRunner().createCommandLinePatchers(configuration.getProject(), state, configuration, PORT));
      return generalCommandLine.getParametersList().getList();
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
