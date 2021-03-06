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

package com.jetbrains.python.run;

import javax.annotation.Nonnull;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;

/**
 * @author yole
 */
public class PythonRunner extends DefaultProgramRunner {

  @Nonnull
  public String getRunnerId() {
    return "PythonRunner";
  }

  public boolean canRun(@Nonnull final String executorId, @Nonnull final RunProfile profile) {
    return executorId.equals(DefaultRunExecutor.EXECUTOR_ID) && profile instanceof AbstractPythonRunConfiguration;
  }

  @Override
  protected RunContentDescriptor doExecute(
    RunProfileState state,
    ExecutionEnvironment env
  ) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    ExecutionResult executionResult;
    RunProfile profile = env.getRunProfile();
    if (state instanceof PythonCommandLineState && profile instanceof CommandLinePatcher) {
      executionResult = ((PythonCommandLineState)state).execute(env.getExecutor(), (CommandLinePatcher)profile);
    }
    else {
      executionResult = state.execute(env.getExecutor(), this);
    }
    if (executionResult == null) return null;

    final RunContentBuilder contentBuilder = new RunContentBuilder(executionResult, env);
    return contentBuilder.showRunContent(env.getContentToReuse());
  }
}
