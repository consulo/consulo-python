/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import consulo.application.util.SystemInfo;
import consulo.container.boot.ContainerPathManager;
import consulo.content.OrderRootType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.ide.impl.idea.remote.RemoteSdkAdditionalData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.util.CapturingProcessAdapter;
import consulo.process.util.ProcessOutput;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.SystemProperties;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * A more flexible cousin of SdkVersionUtil.
 * Needs not to be instantiated and only holds static methods.
 *
 * @author dcheryasov
 * Date: Apr 24, 2008
 * Time: 1:19:47 PM
 */
public class PySdkUtil {
  protected static final Logger LOG = Logger.getInstance(PySdkUtil.class);

  // Windows EOF marker, Ctrl+Z
  public static final int SUBSTITUTE = 26;
  public static final String PATH_ENV_VARIABLE = "PATH";

  private PySdkUtil() {
    // explicitly none
  }

  /**
   * Executes a process and returns its stdout and stderr outputs as lists of lines.
   *
   * @param homePath process run directory
   * @param command  command to execute and its arguments
   * @return a tuple of (stdout lines, stderr lines, exit_code), lines in them have line terminators stripped, or may be null.
   */
  @Nonnull
  public static ProcessOutput getProcessOutput(String homePath, @NonNls String[] command) {
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
  @Nonnull
  public static ProcessOutput getProcessOutput(String homePath, @NonNls String[] command, final int timeout) {
    return getProcessOutput(homePath, command, null, timeout);
  }

  @Nonnull
  public static ProcessOutput getProcessOutput(String homePath,
                                               @NonNls String[] command,
                                               @Nullable @NonNls Map<String, String> extraEnv,
                                               final int timeout) {
    return getProcessOutput(homePath, command, extraEnv, timeout, null, true);
  }

  @Nonnull
  public static ProcessOutput getProcessOutput(String homePath,
                                               @NonNls String[] command,
                                               @Nullable @NonNls Map<String, String> extraEnv,
                                               final int timeout,
                                               @Nullable byte[] stdin,
                                               boolean needEOFMarker) {
    return getProcessOutput(new GeneralCommandLine(command), homePath, extraEnv, timeout, stdin, needEOFMarker);
  }

  public static ProcessOutput getProcessOutput(@Nonnull GeneralCommandLine cmd,
                                               @Nullable String homePath,
                                               @Nullable @NonNls Map<String, String> extraEnv,
                                               int timeout) {
    return getProcessOutput(cmd, homePath, extraEnv, timeout, null, true);
  }

  public static ProcessOutput getProcessOutput(@Nonnull GeneralCommandLine cmd,
                                               @Nullable String homePath,
                                               @Nullable @NonNls Map<String, String> extraEnv,
                                               int timeout,
                                               @Nullable byte[] stdin,
                                               boolean needEOFMarker) {
    if (homePath == null || !new File(homePath).exists()) {
      return new ProcessOutput();
    }
    final Map<String, String> systemEnv = System.getenv();
    final Map<String, String> expandedCmdEnv = mergeEnvVariables(systemEnv, cmd.getEnvironment());
    final Map<String, String> env = extraEnv != null ? mergeEnvVariables(expandedCmdEnv, extraEnv) : expandedCmdEnv;
    PythonEnvUtil.resetHomePathChanges(homePath, env);
    try {

      final GeneralCommandLine commandLine = cmd.withWorkDirectory(homePath).withEnvironment(env);
      final ProcessHandler processHandler = ProcessHandlerBuilder.create(commandLine).build();
      if (stdin != null) {
        final OutputStream processInput = processHandler.getProcessInput();
        assert processInput != null;
        processInput.write(stdin);
        if (SystemInfo.isWindows && needEOFMarker) {
          processInput.write(SUBSTITUTE);
          processInput.flush();
        }
        else {
          processInput.close();
        }
      }
      CapturingProcessAdapter adapter = new CapturingProcessAdapter();
      processHandler.addProcessListener(adapter);
      processHandler.startNotify();
      processHandler.waitFor();
      return adapter.getOutput();
    }
    catch (ExecutionException e) {
      return getOutputForException(e);
    }
    catch (IOException e) {
      return getOutputForException(e);
    }
  }

  private static ProcessOutput getOutputForException(final Exception e) {
    LOG.warn(e);
    return new ProcessOutput() {
      @Nonnull
      @Override
      public String getStderr() {
        String err = super.getStderr();
        if (!StringUtil.isEmpty(err)) {
          err += "\n" + e.getMessage();
        }
        else {
          err = e.getMessage();
        }
        return err;
      }
    };
  }

  @Nonnull
  public static Map<String, String> mergeEnvVariables(@Nonnull Map<String, String> environment,
                                                      @Nonnull Map<String, String> extraEnvironment) {
    final Map<String, String> result = new HashMap<>(environment);
    for (Map.Entry<String, String> entry : extraEnvironment.entrySet()) {
      final String name = entry.getKey();
      if (PATH_ENV_VARIABLE.equals(name) || PythonEnvUtil.PYTHONPATH.equals(name)) {
        PythonEnvUtil.addPathToEnv(result, name, entry.getValue());
      }
      else {
        result.put(name, entry.getValue());
      }
    }
    return result;
  }

  public static boolean isRemote(@Nullable Sdk sdk) {
    return sdk != null && sdk.getSdkAdditionalData() instanceof RemoteSdkAdditionalData;
  }

  public static String getUserSite() {
    if (SystemInfo.isWindows) {
      final String appdata = System.getenv("APPDATA");
      return appdata + File.separator + "Python";
    }
    else {
      final String userHome = SystemProperties.getUserHome();
      return userHome + File.separator + ".local";
    }
  }

  public static boolean isElementInSkeletons(@Nonnull final PsiElement element) {
    final PsiFile file = element.getContainingFile();
    if (file != null) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        final Sdk sdk = PythonSdkType.getSdk(element);
        if (sdk != null) {
          final VirtualFile skeletonsDir = findSkeletonsDir(sdk);
          if (skeletonsDir != null && VfsUtilCore.isAncestor(skeletonsDir, virtualFile, false)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static String getRemoteSourcesLocalPath(String sdkHome) {
    String sep = File.separator;

    String basePath = ContainerPathManager.get().getSystemPath();
    return basePath +
      File.separator +
      PythonSdkType.REMOTE_SOURCES_DIR_NAME +
      sep +
      FileUtil.toSystemIndependentName(sdkHome).hashCode() +
      sep;
  }

  @Nullable
  public static VirtualFile findSkeletonsDir(@Nonnull final Sdk sdk) {
    return findLibraryDir(sdk, PythonSdkType.SKELETON_DIR_NAME, BinariesOrderRootType.getInstance());
  }

  @Nullable
  public static VirtualFile findAnyRemoteLibrary(@Nonnull final Sdk sdk) {
    return findLibraryDir(sdk, PythonSdkType.REMOTE_SOURCES_DIR_NAME, BinariesOrderRootType.getInstance());
  }

  private static VirtualFile findLibraryDir(Sdk sdk, String dirName, OrderRootType rootType) {
    final VirtualFile[] virtualFiles = sdk.getRootProvider().getFiles(rootType);
    for (VirtualFile virtualFile : virtualFiles) {
      if (virtualFile.isValid() && virtualFile.getPath().contains(dirName)) {
        return virtualFile;
      }
    }
    return null;
  }
}
