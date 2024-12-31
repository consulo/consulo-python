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

package com.jetbrains.python.impl.sdk.flavors;

import com.jetbrains.python.impl.PythonIcons;
import com.jetbrains.python.impl.run.PythonProcessHandler;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonEnvUtil;
import com.jetbrains.python.impl.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.util.SystemInfo;
import consulo.component.extension.ExtensionPointName;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkAdditionalData;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.util.ProcessOutput;
import consulo.ui.image.Image;
import consulo.util.io.FileUtil;
import consulo.util.lang.PatternUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.encoding.EncodingManager;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author yole
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class PythonSdkFlavor {
  public static final ExtensionPointName<PythonSdkFlavor> EP_NAME = ExtensionPointName.create(PythonSdkFlavor.class);
  private static final Logger LOG = Logger.getInstance(PythonSdkFlavor.class);

  public static Collection<String> appendSystemPythonPath(@Nonnull Collection<String> pythonPath) {
    return appendSystemEnvPaths(pythonPath, PythonEnvUtil.PYTHONPATH);
  }

  protected static Collection<String> appendSystemEnvPaths(@Nonnull Collection<String> pythonPath, String envname) {
    String syspath = System.getenv(envname);
    if (syspath != null) {
      pythonPath.addAll(List.of(syspath.split(File.pathSeparator)));
    }
    return pythonPath;
  }

  public static void initPythonPath(@Nonnull Map<String, String> envs, boolean passParentEnvs, @Nonnull Collection<String> pythonPathList) {
    if (passParentEnvs && !envs.containsKey(PythonEnvUtil.PYTHONPATH)) {
      pythonPathList = appendSystemPythonPath(pythonPathList);
    }
    PythonEnvUtil.addToPythonPath(envs, pythonPathList);
  }

  public static List<PythonSdkFlavor> getApplicableFlavors() {
    List<PythonSdkFlavor> result = new ArrayList<PythonSdkFlavor>();

    if (SystemInfo.isWindows) {
      result.add(WinPythonSdkFlavor.INSTANCE);
    }
    else if (SystemInfo.isMac) {
      result.add(MacPythonSdkFlavor.INSTANCE);
    }
    else if (SystemInfo.isUnix) {
      result.add(UnixPythonSdkFlavor.INSTANCE);
    }

    Collections.addAll(result, EP_NAME.getExtensions());

    return result;
  }

  @Nullable
  public static PythonSdkFlavor getFlavor(Sdk sdk) {
    final SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (data instanceof PythonSdkAdditionalData) {
      PythonSdkFlavor flavor = ((PythonSdkAdditionalData)data).getFlavor();
      if (flavor != null) {
        return flavor;
      }
    }
    return getFlavor(sdk.getHomePath());
  }

  @Nullable
  public static PythonSdkFlavor getFlavor(@Nullable String sdkPath) {
    if (sdkPath == null) {
      return null;
    }

    for (PythonSdkFlavor flavor : getApplicableFlavors()) {
      if (flavor.isValidSdkHome(sdkPath)) {
        return flavor;
      }
    }
    return null;
  }

  @Nullable
  public static PythonSdkFlavor getPlatformIndependentFlavor(@Nullable final String sdkPath) {
    if (sdkPath == null) {
      return null;
    }

    for (PythonSdkFlavor flavor : EP_NAME.getExtensionList()) {
      if (flavor.isValidSdkHome(sdkPath)) {
        return flavor;
      }
    }
    return null;
  }

  @Nullable
  protected static String getVersionFromOutput(String sdkHome, String version_opt, String version_regexp) {
    String run_dir = new File(sdkHome).getParent();
    final ProcessOutput process_output = PySdkUtil.getProcessOutput(run_dir, new String[]{
      sdkHome,
      version_opt
    });

    return getVersionFromOutput(version_regexp, process_output);
  }

  @Nullable
  private static String getVersionFromOutput(String version_regexp, ProcessOutput process_output) {
    if (process_output.getExitCode() != 0) {
      String err = process_output.getStderr();
      if (StringUtil.isEmpty(err)) {
        err = process_output.getStdout();
      }
      LOG.warn("Couldn't get interpreter version: process exited with code " + process_output.getExitCode() + "\n" + err);
      return null;
    }
    Pattern pattern = Pattern.compile(version_regexp);
    final String result = PatternUtil.getFirstMatch(process_output.getStderrLines(), pattern);
    if (result != null) {
      return result;
    }
    return PatternUtil.getFirstMatch(process_output.getStdoutLines(), pattern);
  }

  public static void addToEnv(final String key, String value, Map<String, String> envs) {
    PythonEnvUtil.addPathToEnv(envs, key, value);
  }

  public Collection<String> suggestHomePaths() {
    return Collections.emptyList();
  }

  /**
   * Checks if the path is the name of a Python interpreter of this flavor.
   *
   * @param path path to check.
   * @return true if paths points to a valid home.
   */
  public boolean isValidSdkHome(String path) {
    File file = new File(path);
    return file.isFile() && isValidSdkPath(file);
  }

  public boolean isValidSdkPath(@Nonnull File file) {
    return FileUtil.getNameWithoutExtension(file).toLowerCase().startsWith("python");
  }

  public String getVersionString(String sdkHome) {
    return getVersionStringFromOutput(getVersionFromOutput(sdkHome, getVersionOption(), getVersionRegexp()));
  }

  public String getVersionStringFromOutput(String version) {
    return version;
  }

  public boolean allowCreateVirtualEnv() {
    return true;
  }

  public String getVersionRegexp() {
    return "(Python \\S+).*";
  }

  public String getVersionOption() {
    return "-V";
  }

  @Nullable
  public String getVersionFromOutput(ProcessOutput processOutput) {
    return getVersionFromOutput(getVersionRegexp(), processOutput);
  }

  public Collection<String> getExtraDebugOptions() {
    return Collections.emptyList();
  }

  public void initPythonPath(GeneralCommandLine cmd, Collection<String> path) {
    initPythonPath(path, cmd.getEnvironment());
  }

  public ProcessHandler createProcessHandler(GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException {
    return PythonProcessHandler.createDefaultProcessHandler(commandLine, withMediator);
  }

  public static void setupEncodingEnvs(Map<String, String> envs, @Nonnull Charset charset) {
    final String encoding = charset.name();
    PythonEnvUtil.setPythonIOEncoding(envs, encoding);
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  public void addPredefinedEnvironmentVariables(Map<String, String> envs) {
    Charset defaultCharset = EncodingManager.getInstance().getDefaultCharset();
    if (defaultCharset != null) {
      final String encoding = defaultCharset.name();
      PythonEnvUtil.setPythonIOEncoding(envs, encoding);
    }
  }

  @Nonnull
  public abstract String getName();

  public LanguageLevel getLanguageLevel(Sdk sdk) {
    final String version = sdk.getVersionString();
    final String prefix = getName() + " ";
    if (version != null && version.startsWith(prefix)) {
      return LanguageLevel.fromPythonVersion(version.substring(prefix.length()));
    }
    return LanguageLevel.getDefault();
  }

  @Nonnull
  public Image getIcon() {
    return PythonIcons.Python.Python;
  }

  public void initPythonPath(Collection<String> path, Map<String, String> env) {
    path = appendSystemPythonPath(path);
    addToEnv(PythonEnvUtil.PYTHONPATH, StringUtil.join(path, File.pathSeparator), env);
  }

  public VirtualFile getSdkPath(VirtualFile path) {
    return path;
  }

  @Nonnull
  public Collection<String> collectDebugPythonPath() {
    return Collections.emptyList();
  }
}
