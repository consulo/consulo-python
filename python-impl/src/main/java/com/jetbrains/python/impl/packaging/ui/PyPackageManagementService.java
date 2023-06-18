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
package com.jetbrains.python.impl.packaging.ui;

import com.jetbrains.python.impl.packaging.PyExecutionException;
import com.jetbrains.python.impl.packaging.PyPIPackageUtil;
import com.jetbrains.python.impl.packaging.PyPackageManagerUI;
import com.jetbrains.python.impl.packaging.PyPackageService;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.impl.packaging.PyPIPackageUtil.PackageDetails;
import com.jetbrains.python.packaging.requirement.PyRequirementRelation;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.application.util.SystemInfo;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.content.bundle.Sdk;
import consulo.execution.RunCanceledByUserException;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.repository.ui.InstalledPackage;
import consulo.repository.ui.PackageManagementServiceEx;
import consulo.repository.ui.RepoPackage;
import consulo.util.concurrent.AsyncResult;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class PyPackageManagementService extends PackageManagementServiceEx {
  @Nonnull
  private static final Pattern PATTERN_ERROR_LINE = Pattern.compile(".*error:.*", Pattern.CASE_INSENSITIVE);
  @NonNls
  private static final String TEXT_PREFIX = "<html><head>" +
    "    <style type=\"text/css\">" +
    "        p {" +
    "            font-family: Arial,serif; font-size: 12pt; margin: 2px 2px" +
    "        }" +
    "    </style>" +
    "</head><body style=\"font-family: Arial,serif; font-size: 12pt; margin: 5px 5px;\">";
  @NonNls
  private static final String TEXT_SUFFIX = "</body></html>";

  private final Project myProject;
  protected final Sdk mySdk;
  protected final ExecutorService myExecutorService;

  public PyPackageManagementService(@Nonnull Project project, @Nonnull Sdk sdk) {
    myProject = project;
    mySdk = sdk;
    // Dumb heuristic for the size of IO-bound tasks pool: safer than unlimited, snappier than a single thread
    myExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("PyPackageManagementService pool", 4);
  }

  @Nonnull
  public Sdk getSdk() {
    return mySdk;
  }

  @Nullable
  @Override
  public List<String> getAllRepositories() {
    final PyPackageService packageService = PyPackageService.getInstance();
    final List<String> result = new ArrayList<>();
    if (!packageService.PYPI_REMOVED) {
      result.add(PyPIPackageUtil.PYPI_LIST_URL);
    }
    result.addAll(packageService.additionalRepositories);
    return result;
  }

  @Override
  public void addRepository(String repositoryUrl) {
    PyPackageService.getInstance().addRepository(repositoryUrl);
  }

  @Override
  public void removeRepository(String repositoryUrl) {
    PyPackageService.getInstance().removeRepository(repositoryUrl);
  }

  @Nonnull
  @Override
  public List<RepoPackage> getAllPackages() throws IOException {
    final Map<String, String> packageToVersionMap = PyPIPackageUtil.INSTANCE.loadAndGetPackages();
    final List<RepoPackage> packages = versionMapToPackageList(packageToVersionMap);
    packages.addAll(PyPIPackageUtil.INSTANCE.getAdditionalPackages());
    return packages;
  }

  @Nonnull
  protected static List<RepoPackage> versionMapToPackageList(@Nonnull Map<String, String> packageToVersionMap) {
    final boolean customRepoConfigured = !PyPackageService.getInstance().additionalRepositories.isEmpty();
    final String url = customRepoConfigured ? PyPIPackageUtil.PYPI_LIST_URL : "";
    final List<RepoPackage> packages = new ArrayList<>();
    for (Map.Entry<String, String> entry : packageToVersionMap.entrySet()) {
      packages.add(new RepoPackage(entry.getKey(), url, entry.getValue()));
    }
    return packages;
  }

  @Nonnull
  @Override
  public List<RepoPackage> reloadAllPackages() throws IOException {
    PyPIPackageUtil.INSTANCE.clearPackagesCache();
    return getAllPackages();
  }

  @Nonnull
  @Override
  public List<RepoPackage> getAllPackagesCached() {
    return versionMapToPackageList(PyPIPackageUtil.getPyPIPackages());
  }

  @Override
  public boolean canInstallToUser() {
    return !PythonSdkType.isVirtualEnv(mySdk);
  }

  @Nonnull
  @Override
  public String getInstallToUserText() {
    String userSiteText = "Install to user's site packages directory";
    if (!PythonSdkType.isRemote(mySdk)) {
      userSiteText += " (" + PySdkUtil.getUserSite() + ")";
    }
    return userSiteText;
  }

  @Override
  public boolean isInstallToUserSelected() {
    return PyPackageService.getInstance().useUserSite(mySdk.getHomePath());
  }

  @Override
  public void installToUserChanged(boolean newValue) {
    PyPackageService.getInstance().addSdkToUserSite(mySdk.getHomePath(), newValue);
  }

  @Nonnull
  @Override
  public Collection<InstalledPackage> getInstalledPackages() throws IOException {

    final PyPackageManager manager = PyPackageManager.getInstance(mySdk);
    final List<PyPackage> packages;
    try {
      packages = new ArrayList<>(manager.refreshAndGetPackages(true));
    }
    catch (ExecutionException e) {
      throw new IOException(e);
    }
    Collections.sort(packages, (pkg1, pkg2) -> pkg1.getName().compareTo(pkg2.getName()));
    return new ArrayList<>(packages);
  }

  @Override
  public void installPackage(@Nonnull RepoPackage repoPackage,
                             @Nullable String version,
                             boolean forceUpgrade,
                             @Nullable String extraOptions,
                             @Nonnull Listener listener,
                             boolean installToUser) {
    final String packageName = repoPackage.getName();
    final String repository = PyPIPackageUtil.isPyPIRepository(repoPackage.getRepoUrl()) ? null : repoPackage.getRepoUrl();
    final List<String> extraArgs = new ArrayList<>();
    if (installToUser) {
      extraArgs.add(PyPackageManager.USE_USER_SITE);
    }
    if (extraOptions != null) {
      // TODO: Respect arguments quotation
      Collections.addAll(extraArgs, extraOptions.split(" +"));
    }
    if (!StringUtil.isEmptyOrSpaces(repository)) {
      extraArgs.add("--index-url");
      extraArgs.add(repository);
    }
    if (forceUpgrade) {
      extraArgs.add("-U");
    }
    final PyRequirement req;
    if (version != null) {
      req = new PyRequirement(packageName, version);
    }
    else {
      req = new PyRequirement(packageName);
    }

    final PyPackageManagerUI ui = new PyPackageManagerUI(myProject, mySdk, new PyPackageManagerUI.Listener() {
      @Override
      public void started() {
        listener.operationStarted(packageName);
      }

      @Override
      public void finished(@Nullable List<ExecutionException> exceptions) {
        listener.operationFinished(packageName, toErrorDescription(exceptions, mySdk));
      }
    });
    ui.install(Collections.singletonList(req), extraArgs);
  }

  @Nullable
  public static ErrorDescription toErrorDescription(@Nullable List<ExecutionException> exceptions, @Nullable Sdk sdk) {
    if (exceptions != null && !exceptions.isEmpty() && !isCancelled(exceptions)) {
      return createDescription(exceptions.get(0), sdk);
    }
    return null;
  }

  @Override
  public void uninstallPackages(@Nonnull List<InstalledPackage> installedPackages,
                                @Nonnull Listener listener) {
    final String packageName = installedPackages.size() == 1 ? installedPackages.get(0).getName() : null;
    final PyPackageManagerUI ui = new PyPackageManagerUI(myProject, mySdk, new PyPackageManagerUI.Listener() {
      @Override
      public void started() {
        listener.operationStarted(packageName);
      }

      @Override
      public void finished(List<ExecutionException> exceptions) {
        listener.operationFinished(packageName, toErrorDescription(exceptions, mySdk));
      }
    });

    final List<PyPackage> pyPackages = new ArrayList<>();
    for (InstalledPackage aPackage : installedPackages) {
      if (aPackage instanceof PyPackage) {
        pyPackages.add((PyPackage)aPackage);
      }
    }
    ui.uninstall(pyPackages);
  }

  @Nonnull
  @Override
  public AsyncResult<List<String>> fetchPackageVersions(String packageName) {
    return PyPIPackageUtil.INSTANCE.usePackageReleases(packageName);
  }

  @Nonnull
  @Override
  public AsyncResult<String> fetchPackageDetails(String packageName) {
    return PyPIPackageUtil.INSTANCE.fillPackageDetails(packageName, info -> formatPackageInfo(info));
  }

  private static String formatPackageInfo(@Nonnull PackageDetails.Info info) {
    final StringBuilder stringBuilder = new StringBuilder(TEXT_PREFIX);
    final String description = info.getSummary();
    if (StringUtil.isNotEmpty(description)) {
      stringBuilder.append(description).append("<br/>");
    }
    final String version = info.getVersion();
    if (StringUtil.isNotEmpty(version)) {
      stringBuilder.append("<h4>Version</h4>");
      stringBuilder.append(version);
    }
    final String author = info.getAuthor();
    if (StringUtil.isNotEmpty(author)) {
      stringBuilder.append("<h4>Author</h4>");
      stringBuilder.append(author).append("<br/><br/>");
    }
    final String authorEmail = info.getAuthorEmail();
    if (StringUtil.isNotEmpty(authorEmail)) {
      stringBuilder.append("<br/>");
      stringBuilder.append(composeHref("mailto:" + authorEmail));
    }
    final String homePage = info.getHomePage();
    if (StringUtil.isNotEmpty(homePage)) {
      stringBuilder.append("<br/>");
      stringBuilder.append(composeHref(homePage));
    }
    stringBuilder.append(TEXT_SUFFIX);
    return stringBuilder.toString();
  }

  @NonNls
  private static final String HTML_PREFIX = "<a href=\"";
  @NonNls
  private static final String HTML_SUFFIX = "</a>";

  @Nonnull
  private static String composeHref(String vendorUrl) {
    return HTML_PREFIX + vendorUrl + "\">" + vendorUrl + HTML_SUFFIX;
  }

  private static boolean isCancelled(@Nonnull List<ExecutionException> exceptions) {
    for (ExecutionException e : exceptions) {
      if (e instanceof RunCanceledByUserException) {
        return true;
      }
    }
    return false;
  }

  @Nonnull
  private static ErrorDescription createDescription(@Nonnull ExecutionException e, @Nullable Sdk sdk) {
    if (e instanceof PyExecutionException) {
      final PyExecutionException ee = (PyExecutionException)e;
      final String stdoutCause = findErrorCause(ee.getStdout());
      final String stderrCause = findErrorCause(ee.getStderr());
      final String cause = stdoutCause != null ? stdoutCause : stderrCause;
      final String message = cause != null ? cause : ee.getMessage();
      final String command = ee.getCommand() + " " + StringUtil.join(ee.getArgs(), " ");
      return new ErrorDescription(message, command, ee.getStdout() + "\n" + ee.getStderr(), findErrorSolution(ee, cause, sdk));
    }
    else {
      return ErrorDescription.fromMessage(e.getMessage());
    }
  }

  @Nullable
  private static String findErrorSolution(@Nonnull PyExecutionException e, @Nullable String cause, @Nullable Sdk sdk) {
    if (cause != null) {
      if (StringUtil.containsIgnoreCase(cause, "SyntaxError")) {
        final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
        return "Make sure that you use a version of Python supported by this package. Currently you are using Python " +
          languageLevel + ".";
      }
    }

    if (SystemInfo.isLinux && (containsInOutput(e, "pyconfig.h") || containsInOutput(e, "Python.h"))) {
      return "Make sure that you have installed Python development packages for your operating system.";
    }

    if ("pip".equals(e.getCommand()) && sdk != null) {
      return "Try to run this command from the system terminal. Make sure that you use the correct version of 'pip' " +
        "installed for your Python interpreter located at '" + sdk.getHomePath() + "'.";
    }

    return null;
  }

  private static boolean containsInOutput(@Nonnull PyExecutionException e, @Nonnull String text) {
    return StringUtil.containsIgnoreCase(e.getStdout(), text) || StringUtil.containsIgnoreCase(e.getStderr(), text);
  }

  @Nullable
  private static String findErrorCause(@Nonnull String output) {
    final Matcher m = PATTERN_ERROR_LINE.matcher(output);
    if (m.find()) {
      final String result = m.group();
      return result != null ? result.trim() : null;
    }
    return null;
  }

  @Override
  public void updatePackage(@Nonnull InstalledPackage installedPackage,
                            @Nullable String version,
                            @Nonnull Listener listener) {
    installPackage(new RepoPackage(installedPackage.getName(), null), null, true, null, listener, false);
  }

  /**
   * @return whether the latest version should be requested independently for each package
   */
  @Override
  public boolean shouldFetchLatestVersionsForOnlyInstalledPackages() {
    return true;
  }

  @Override
  public AsyncResult<String> fetchLatestVersion(@Nonnull InstalledPackage pkg) {
    AsyncResult<String> result = AsyncResult.undefined();

    myExecutorService.submit(() -> {
      try {
        PyPIPackageUtil.INSTANCE.loadAndGetPackages();
        final String version = PyPIPackageUtil.INSTANCE.fetchLatestPackageVersion(pkg.getName());
        result.setDone(StringUtil.notNullize(version));
      }
      catch (IOException e) {
        result.rejectWithThrowable(e);
      }
    });
    return result;
  }

  @Override
  public int compareVersions(@Nonnull String version1, @Nonnull String version2) {
    if (PyRequirement.calculateVersionSpec(version2, PyRequirementRelation.EQ).matches(version1)) {
      // Here we're catching the case described in PY-20939.
      // The order of 'version1' and 'version2' is important: version2 is an available version which version1 tries to match
      return 0;
    }
    return super.compareVersions(version1, version2);
  }
}