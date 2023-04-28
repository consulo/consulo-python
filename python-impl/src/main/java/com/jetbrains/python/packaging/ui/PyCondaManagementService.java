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
package com.jetbrains.python.packaging.ui;

import com.google.common.collect.Lists;
import com.jetbrains.python.packaging.PyCondaPackageService;
import consulo.content.bundle.Sdk;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.internal.CapturingProcessHandler;
import consulo.process.local.ProcessOutput;
import consulo.project.Project;
import consulo.repository.ui.PackageVersionComparator;
import consulo.repository.ui.RepoPackage;
import consulo.util.concurrent.AsyncResult;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PyCondaManagementService extends PyPackageManagementService {
  private static final Logger LOG = Logger.getInstance(PyCondaManagementService.class);

  public PyCondaManagementService(@Nonnull final Project project, @Nonnull final Sdk sdk) {
    super(project, sdk);
  }

  @Override
  @Nonnull
  public List<RepoPackage> getAllPackagesCached() {
    return versionMapToPackageList(PyCondaPackageService.getInstance().getCondaPackages());
  }

  @Override
  @Nonnull
  public List<RepoPackage> getAllPackages() {
    return versionMapToPackageList(PyCondaPackageService.getInstance().loadAndGetPackages());
  }

  @Override
  @Nonnull
  public List<RepoPackage> reloadAllPackages() {
    return getAllPackages();
  }

  @Override
  public List<String> getAllRepositories() {
    List<String> result = new ArrayList<>();
    result.addAll(PyCondaPackageService.getInstance().loadAndGetChannels());
    return result;
  }

  @Override
  public void addRepository(String repositoryUrl) {
    final String conda = PyCondaPackageService.getCondaExecutable(mySdk.getHomeDirectory());
    final ArrayList<String> parameters = Lists.newArrayList(conda, "config", "--add", "channels", repositoryUrl, "--force");
    final GeneralCommandLine commandLine = new GeneralCommandLine(parameters);

    try {
      final CapturingProcessHandler handler = new CapturingProcessHandler(commandLine);
      final ProcessOutput result = handler.runProcess();
      final int exitCode = result.getExitCode();
      if (exitCode != 0) {
        final String message =
          StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr()) ? "Permission denied" : "Non-zero exit code";
        LOG.warn("Failed to add repository " + message);
      }
      PyCondaPackageService.getInstance().addChannel(repositoryUrl);
    }
    catch (ExecutionException e) {
      LOG.warn("Failed to add repository");
    }

  }

  @Override
  public void removeRepository(String repositoryUrl) {
    final String conda = PyCondaPackageService.getCondaExecutable(mySdk.getHomeDirectory());
    final ArrayList<String> parameters = Lists.newArrayList(conda, "config", "--remove", "channels", repositoryUrl, "--force");
    final GeneralCommandLine commandLine = new GeneralCommandLine(parameters);

    try {
      final CapturingProcessHandler handler = new CapturingProcessHandler(commandLine);
      final ProcessOutput result = handler.runProcess();
      final int exitCode = result.getExitCode();
      if (exitCode != 0) {
        final String message =
          StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr()) ? "Permission denied" : "Non-zero exit code";
        LOG.warn("Failed to remove repository " + message);
      }
      PyCondaPackageService.getInstance().removeChannel(repositoryUrl);
    }
    catch (ExecutionException e) {
      LOG.warn("Failed to remove repository");
    }
  }

  @Override
  public boolean canInstallToUser() {
    return false;
  }

  @Nonnull
  @Override
  public AsyncResult<List<String>> fetchPackageVersions(String packageName) {
    final List<String> versions = PyCondaPackageService.getInstance().getPackageVersions(packageName);
    Collections.sort(versions, Collections.reverseOrder(new PackageVersionComparator()));
    return AsyncResult.resolved(versions);
  }
}
