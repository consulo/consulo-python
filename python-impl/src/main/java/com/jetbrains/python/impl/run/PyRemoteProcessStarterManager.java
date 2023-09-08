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
package com.jetbrains.python.impl.run;

import com.jetbrains.python.impl.remote.PyRemotePathMapper;
import com.jetbrains.python.impl.remote.PyRemoteProcessHandlerBase;
import com.jetbrains.python.impl.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.impl.remote.PythonRemoteInterpreterManager;
import consulo.annotation.DeprecationInfo;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.util.ProcessOutput;
import consulo.project.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Alexander Koshevoy
 */
@ExtensionAPI(ComponentScope.APPLICATION)
@Deprecated
@DeprecationInfo("We don't have remote plugin")
public interface PyRemoteProcessStarterManager {
  ExtensionPointName<PyRemoteProcessStarterManager> EP_NAME = ExtensionPointName.create(PyRemoteProcessStarterManager.class);

  boolean supports(@Nonnull PyRemoteSdkAdditionalDataBase sdkAdditionalData);

  @Nonnull
  PyRemoteProcessHandlerBase startRemoteProcess(@Nullable Project project,
                                                @Nonnull GeneralCommandLine commandLine,
                                                @Nonnull PythonRemoteInterpreterManager manager,
                                                @Nonnull PyRemoteSdkAdditionalDataBase sdkAdditionalData,
                                                @Nonnull PyRemotePathMapper pathMapper) throws ExecutionException, InterruptedException;

  @Nonnull
  ProcessOutput executeRemoteProcess(@Nullable Project project,
                                     @Nonnull String[] command,
                                     @Nullable String workingDir,
                                     @Nonnull PythonRemoteInterpreterManager manager,
                                     @Nonnull PyRemoteSdkAdditionalDataBase sdkAdditionalData,
                                     @Nonnull PyRemotePathMapper pathMapper,
                                     boolean askForSudo,
                                     boolean checkHelpers) throws ExecutionException, InterruptedException;


  String getFullInterpreterPath(@Nonnull PyRemoteSdkAdditionalDataBase sdkAdditionalData) throws ExecutionException, InterruptedException;
}
