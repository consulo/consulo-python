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

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.jetbrains.python.impl.remote.PyRemotePathMapper;
import com.jetbrains.python.impl.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.impl.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.impl.run.PyRemoteProcessStarterManagerUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkAdditionalData;
import consulo.ide.impl.idea.remote.RemoteSdkAdditionalData;
import consulo.ide.impl.idea.remote.VagrantNotStartedException;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.process.util.ProcessOutput;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyRemotePackageManagerImpl extends PyPackageManagerImpl {
  private static final Logger LOG = Logger.getInstance(PyRemotePackageManagerImpl.class);

  PyRemotePackageManagerImpl(@Nonnull final Sdk sdk) {
    super(sdk);
  }

  @Nullable
  @Override
  protected String getHelperPath(String helper) throws ExecutionException {
    final Sdk sdk = getSdk();

    final SdkAdditionalData sdkData = sdk.getSdkAdditionalData();
    if (sdkData instanceof PyRemoteSdkAdditionalDataBase) {
      final PyRemoteSdkAdditionalDataBase remoteSdkData = (PyRemoteSdkAdditionalDataBase)sdkData;
      try {
        String helpersPath;
        if (consulo.ide.impl.idea.remote.ext.CaseCollector.useRemoteCredentials(remoteSdkData)) {
          final consulo.ide.impl.idea.remote.RemoteSdkCredentials remoteSdkCredentials = remoteSdkData.getRemoteSdkCredentials(false);
          helpersPath = remoteSdkCredentials.getHelpersPath();
        }
        else {
          helpersPath = remoteSdkData.getHelpersPath();
        }
        if (!StringUtil.isEmpty(helpersPath)) {
          return new consulo.ide.impl.idea.remote.RemoteFile(helpersPath, helper).getPath();
        }
        else {
          return null;
        }
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (ExecutionException e) {
        throw analyzeException(e, helper, Collections.<String>emptyList());
      }
    }
    return null;
  }

  @Nonnull
  @Override
  protected ProcessOutput getPythonProcessOutput(@Nonnull String helperPath,
                                                 @Nonnull List<String> args,
                                                 boolean askForSudo,
                                                 boolean showProgress,
                                                 @Nullable final String workingDir) throws ExecutionException {
    final Sdk sdk = getSdk();
    final String homePath = sdk.getHomePath();
    if (homePath == null) {
      throw new ExecutionException("Cannot find Python interpreter for SDK " + sdk.getName());
    }
    final SdkAdditionalData sdkData = sdk.getSdkAdditionalData();
    if (sdkData instanceof PyRemoteSdkAdditionalDataBase) { //remote interpreter
      final PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();

      consulo.ide.impl.idea.remote.RemoteSdkCredentials remoteSdkCredentials;
      if (consulo.ide.impl.idea.remote.ext.CaseCollector.useRemoteCredentials((PyRemoteSdkAdditionalDataBase)sdkData)) {
        try {
          remoteSdkCredentials = ((RemoteSdkAdditionalData)sdkData).getRemoteSdkCredentials(false);
        }
        catch (InterruptedException e) {
          LOG.error(e);
          remoteSdkCredentials = null;
        }
        catch (ExecutionException e) {
          throw analyzeException(e, helperPath, args);
        }
        if (manager != null && remoteSdkCredentials != null) {
          if (askForSudo) {
            askForSudo = !manager.ensureCanWrite(null, remoteSdkCredentials, remoteSdkCredentials.getInterpreterPath());
          }
        }
        else {
          throw new PyExecutionException(PythonRemoteInterpreterManager.WEB_DEPLOYMENT_PLUGIN_IS_DISABLED, helperPath, args);
        }
      }

      if (manager != null) {
        final List<String> cmdline = new ArrayList<>();
        cmdline.add(homePath);
        cmdline.add(consulo.ide.impl.idea.remote.RemoteFile.detectSystemByPath(homePath).createRemoteFile(helperPath).getPath());
        cmdline.addAll(Collections2.transform(args, new Function<String, String>() {
          @Override
          public String apply(@Nullable String input) {
            return quoteIfNeeded(input);
          }
        }));
        ProcessOutput processOutput;
        do {
          final PyRemoteSdkAdditionalDataBase remoteSdkAdditionalData = (PyRemoteSdkAdditionalDataBase)sdkData;
          final PyRemotePathMapper pathMapper = manager.setupMappings(null, remoteSdkAdditionalData, null);
          try {
            processOutput = PyRemoteProcessStarterManagerUtil.getManager(remoteSdkAdditionalData)
                                                             .executeRemoteProcess(null,
                                                                                   ArrayUtil.toStringArray(cmdline),
                                                                                   workingDir,
                                                                                   manager,
                                                                                   remoteSdkAdditionalData,
                                                                                   pathMapper,
                                                                                   askForSudo,
                                                                                   true);
          }
          catch (InterruptedException e) {
            throw new ExecutionException(e);
          }
          if (askForSudo && processOutput.getStderr().contains("sudo: 3 incorrect password attempts")) {
            continue;
          }
          break;
        }
        while (true);
        return processOutput;
      }
      else {
        throw new PyExecutionException(PythonRemoteInterpreterManager.WEB_DEPLOYMENT_PLUGIN_IS_DISABLED, helperPath, args);
      }
    }
    else {
      throw new PyExecutionException("Invalid remote SDK", helperPath, args);
    }
  }

  private ExecutionException analyzeException(ExecutionException exception, String command, List<String> args) {
    final Throwable cause = exception.getCause();
    if (cause instanceof VagrantNotStartedException) {
      return new PyExecutionException("Vagrant instance is down",
                                      command,
                                      args,
                                      "",
                                      "",
                                      0,
                                      ImmutableList.of(new LaunchVagrantFix(((VagrantNotStartedException)cause).getVagrantFolder(), (
                                        (consulo.ide.impl.idea.remote.VagrantNotStartedException)cause).getMachineName())));
    }
    return exception;
  }

  @Override
  protected void subscribeToLocalChanges() {
    // Local VFS changes aren't needed
  }

  @Override
  protected void installManagement(@Nonnull String name) throws ExecutionException {
    super.installManagement(name);
    // TODO: remove temp directory for remote interpreter
  }

  private static String quoteIfNeeded(String arg) {
    return arg.replace("<", "\\<").replace(">", "\\>"); //TODO: move this logic to ParametersListUtil.encode
  }

  private class LaunchVagrantFix implements PyExecutionFix {
    @Nonnull
    private final String myVagrantFolder;
    @Nullable
    private final String myMachineName;

    public LaunchVagrantFix(@Nonnull String vagrantFolder, @Nullable String machineName) {
      myVagrantFolder = vagrantFolder;
      myMachineName = machineName;
    }

    @Nonnull
    @Override
    public String getName() {
      return "Launch Vagrant";
    }

    @Override
    public void run(@Nonnull Sdk sdk) {
      final PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        try {
          manager.runVagrant(myVagrantFolder, myMachineName);
          PythonSdkType.getInstance().setupSdkPaths(sdk);
        }
        catch (ExecutionException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
