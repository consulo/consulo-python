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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.sdk.skeletons.PySkeletonRefresher;
import com.jetbrains.python.packaging.PyPackageManager;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.container.boot.ContainerPathManager;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkAdditionalData;
import consulo.content.bundle.SdkModificator;
import consulo.ide.impl.idea.util.concurrency.BlockingSet;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.project.startup.PostStartupActivity;
import consulo.ui.UIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Refreshes all project's Python SDKs.
 *
 * @author vlan
 * @author yole
 */
@ExtensionImpl
public class PythonSdkUpdater implements PostStartupActivity {
    private static final Logger LOG = Logger.getInstance(PythonSdkUpdater.class);
    public static final int INITIAL_ACTIVITY_DELAY = 7000;

    private static final Object ourLock = new Object();
    private static final Set<String> ourScheduledToRefresh = Sets.newHashSet();
    private static final BlockingSet<String> ourUnderRefresh = new BlockingSet<>();

    /**
     * Refreshes the SDKs of the modules for the open project after some delay.
     */
    @Override
    public void runActivity(@Nonnull final Project project, UIAccess uiAccess) {
        uiAccess.getScheduler()
            .schedule(() -> ProgressManager.getInstance().run(new Task.Backgroundable(project, "Updating Python Paths", false) {
                @Override
                public void run(@Nonnull ProgressIndicator indicator) {
                    final Project project = (Project) getProject();
                    if (project.isDisposed()) {
                        return;
                    }
                    for (final Sdk sdk : getPythonSdks(project)) {
                        update(sdk, null, project, null);
                    }
                }
            }), INITIAL_ACTIVITY_DELAY, TimeUnit.MILLISECONDS);
    }

    /**
     * Updates the paths of an SDK and regenerates its skeletons as a background task.
     * <p>
     * May be invoked from any thread. May freeze the current thread while evaluating sys.path.
     * <p>
     * For a local SDK it commits all the SDK paths and runs a background task for updating skeletons. For a remote SDK it runs a background
     * task for updating skeletons that saves path mappings in the additional SDK data and then commits all the SDK paths.
     * <p>
     * The commit of the changes in the SDK happens in the AWT thread while the current thread is waiting the result.
     *
     * @param sdkModificator if null then it tries to get an SDK modifier from the SDK table, falling back to the modifier of the SDK
     *                       passed as an argument accessed from the AWT thread
     * @return false if there was an immediate problem updating the SDK. Other problems are reported as log entries and balloons.
     */
    public static boolean update(@Nonnull Sdk sdk,
                                 @Nullable SdkModificator sdkModificator,
                                 @Nullable final Project project,
                                 @Nullable final Component ownerComponent) {
        final String key = PythonSdkType.getSdkKey(sdk);
        synchronized (ourLock) {
            ourScheduledToRefresh.add(key);
        }
        if (!updateLocalSdkPaths(sdk, sdkModificator, project)) {
            return false;
        }


        final Application application = ApplicationManager.getApplication();

        if (application.isUnitTestMode()) {
            // All actions we take after this line are dedicated to skeleton update process. Not all tests do need them. To find test API that
            // updates skeleton, see PySkeletonRefresher
            return true;
        }

        @SuppressWarnings("ThrowableInstanceNeverThrown") final Throwable methodCallStacktrace = new Throwable();
        application.invokeLater(() -> {
            synchronized (ourLock) {
                if (!ourScheduledToRefresh.contains(key)) {
                    return;
                }
                ourScheduledToRefresh.remove(key);
            }
            ProgressManager.getInstance().run(new Task.Backgroundable(project, PyBundle.message("sdk.gen.updating.interpreter"), false) {
                @Override
                public void run(@Nonnull ProgressIndicator indicator) {
                    final Project project1 = (Project) getProject();
                    final Sdk sdkInsideTask = PythonSdkType.findSdkByKey(key);
                    if (sdkInsideTask != null) {
                        ourUnderRefresh.put(key);
                        try {
                            final String skeletonsPath = getBinarySkeletonsPath(sdk.getHomePath());
                            try {
                                if (PythonSdkType.isRemote(sdkInsideTask) && project1 == null && ownerComponent == null) {
                                    LOG.error("For refreshing skeletons of remote SDK, either project or owner component must be specified");
                                }
                                final String sdkPresentableName = getSdkPresentableName(sdk);
                                LOG.info("Performing background update of skeletons for SDK " + sdkPresentableName);
                                indicator.setText("Updating skeletons...");
                                PySkeletonRefresher.refreshSkeletonsOfSdk(project1, ownerComponent, skeletonsPath, sdkInsideTask);
                                indicator.setIndeterminate(true);
                                indicator.setText("Scanning installed packages...");
                                indicator.setText2("");
                                LOG.info("Performing background scan of packages for SDK " + sdkPresentableName);
                                try {
                                    PyPackageManager.getInstance(sdkInsideTask).refreshAndGetPackages(true);
                                }
                                catch (ExecutionException e) {
                                    if (LOG.isDebugEnabled()) {
                                        e.initCause(methodCallStacktrace);
                                        LOG.debug(e);
                                    }
                                    else {
                                        LOG.warn(e.getMessage());
                                    }
                                }
                            }
                            catch (InvalidSdkException e) {
                                if (!PythonSdkType.isInvalid(sdkInsideTask)) {
                                    LOG.error(e);
                                }
                            }
                        }
                        finally {
                            try {
                                ourUnderRefresh.remove(key);
                            }
                            catch (IllegalStateException e) {
                                LOG.error(e);
                            }
                        }
                    }
                }
            });
        }, application.getNoneModalityState());
        return true;
    }

    /**
     * Updates the paths of an SDK and regenerates its skeletons as a background task. Shows an error message if the update fails.
     *
     * @see {@link #update(Sdk, SdkModificator, Project, Component)}
     */
    public static void updateOrShowError(@Nonnull Sdk sdk,
                                         @Nullable SdkModificator sdkModificator,
                                         @Nullable Project project,
                                         @Nullable Component ownerComponent) {
        final boolean success = update(sdk, sdkModificator, project, ownerComponent);
        if (!success) {
            Messages.showErrorDialog(project,
                PyBundle.message("MSG.cant.setup.sdk.$0", getSdkPresentableName(sdk)),
                PyBundle.message("MSG.title.bad.sdk"));
        }
    }

    /**
     * Updates the paths of a local SDK.
     * <p>
     * May be invoked from any thread. May freeze the current thread while evaluating sys.path.
     */
    public static boolean updateLocalSdkPaths(@Nonnull Sdk sdk, @Nullable SdkModificator sdkModificator, @Nullable Project project) {
        if (!PythonSdkType.isRemote(sdk)) {
            final List<VirtualFile> localSdkPaths;
            final boolean forceCommit = ensureBinarySkeletonsDirectoryExists(sdk);
            try {
                localSdkPaths = getLocalSdkPaths(sdk, project);
            }
            catch (InvalidSdkException e) {
                if (!PythonSdkType.isInvalid(sdk)) {
                    LOG.error(e);
                }
                return false;
            }
            commitSdkPathsIfChanged(sdk, sdkModificator, localSdkPaths, forceCommit);
        }
        return true;
    }

    private static boolean ensureBinarySkeletonsDirectoryExists(Sdk sdk) {
        final String skeletonsPath = getBinarySkeletonsPath(sdk.getHomePath());
        if (skeletonsPath != null) {
            if (new File(skeletonsPath).mkdirs()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns all the paths for a local SDK.
     */
    @Nonnull
    private static List<VirtualFile> getLocalSdkPaths(@Nonnull Sdk sdk, @Nullable Project project) throws InvalidSdkException {
        return ImmutableList.<VirtualFile>builder().addAll(filterRootPaths(sdk, evaluateSysPath(sdk), project))
            .addAll(getSkeletonsPaths(sdk))
            .addAll(getUserAddedPaths(sdk))
            .build();
    }

    /**
     * Returns all the paths manually added to an SDK by the user.
     */
    @Nonnull
    private static List<VirtualFile> getUserAddedPaths(@Nonnull Sdk sdk) {
        final SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
        final PythonSdkAdditionalData pythonAdditionalData = PyUtil.as(additionalData, PythonSdkAdditionalData.class);
        return pythonAdditionalData != null ? Lists.newArrayList(pythonAdditionalData.getAddedPathFiles()) : Collections.emptyList();
    }

    /**
     * Filters valid paths from an initial set of Python paths and returns them as virtual files.
     */
    @Nonnull
    private static List<VirtualFile> filterRootPaths(@Nonnull Sdk sdk, @Nonnull List<String> paths, @Nullable Project project) {
        final PythonSdkAdditionalData pythonAdditionalData = PyUtil.as(sdk.getSdkAdditionalData(), PythonSdkAdditionalData.class);
        final Collection<VirtualFile> excludedPaths =
            pythonAdditionalData != null ? pythonAdditionalData.getExcludedPathFiles() : Collections.emptyList();
        final Set<VirtualFile> moduleRoots = new HashSet<>();
        if (project != null) {
            final Module[] modules = ModuleManager.getInstance(project).getModules();
            for (Module module : modules) {
                moduleRoots.addAll(PyUtil.getSourceRoots(module));
            }
        }
        final List<VirtualFile> results = Lists.newArrayList();
        for (String path : paths) {
            if (path != null && !FileUtil.extensionEquals(path, "egg-info")) {
                final VirtualFile virtualFile = StandardFileSystems.local().refreshAndFindFileByPath(path);
                if (virtualFile != null) {
                    final VirtualFile rootFile = PythonSdkType.getSdkRootVirtualFile(virtualFile);
                    if (!excludedPaths.contains(rootFile) && !moduleRoots.contains(rootFile)) {
                        results.add(rootFile);
                        continue;
                    }
                }
            }
            LOG.info("Bogus sys.path entry " + path);
        }
        return results;
    }

    /**
     * Returns the paths of the binary skeletons and user skeletons for an SDK.
     */
    @Nonnull
    private static List<VirtualFile> getSkeletonsPaths(@Nonnull Sdk sdk) {
        final List<VirtualFile> results = Lists.newArrayList();
        final String skeletonsPath = getBinarySkeletonsPath(sdk.getHomePath());
        if (skeletonsPath != null) {
            final VirtualFile skeletonsDir = StandardFileSystems.local().refreshAndFindFileByPath(skeletonsPath);
            if (skeletonsDir != null) {
                results.add(skeletonsDir);
                LOG.info("Binary skeletons directory for SDK " + getSdkPresentableName(sdk) + "): " + skeletonsDir.getPath());
            }
        }
        final VirtualFile userSkeletonsDir = PyUserSkeletonsUtil.getUserSkeletonsDirectory();
        if (userSkeletonsDir != null) {
            results.add(userSkeletonsDir);
            LOG.info("User skeletons directory for SDK " + getSdkPresentableName(sdk) + "): " + userSkeletonsDir.getPath());
        }
        return results;
    }

    @Nonnull
    private static String getSdkPresentableName(@Nonnull Sdk sdk) {
        final String homePath = sdk.getHomePath();
        final String name = sdk.getName();
        return homePath != null ? name + " (" + homePath + ")" : name;
    }

    @Nullable
    private static String getBinarySkeletonsPath(@Nullable String path) {
        return path != null ? PythonSdkType.getSkeletonsPath(ContainerPathManager.get().getSystemPath(), path) : null;
    }

    /**
     * Evaluates sys.path by running the Python interpreter from a local SDK.
     * <p>
     * Returns all the existing paths except those manually excluded by the user.
     */
    @Nonnull
    private static List<String> evaluateSysPath(@Nonnull Sdk sdk) throws InvalidSdkException {
        if (PythonSdkType.isRemote(sdk)) {
            throw new IllegalArgumentException("Cannot evaluate sys.path for remote Python interpreter " + sdk);
        }
        final long startTime = System.currentTimeMillis();
        final List<String> sysPath = PythonSdkType.getSysPath(sdk.getHomePath());
        LOG.info("Updating sys.path took " + (System.currentTimeMillis() - startTime) + " ms");
        return sysPath;
    }

    /**
     * Commits new SDK paths using an SDK modificator if the paths have been changed.
     * <p>
     * You may invoke it from any thread. Blocks until the commit is done in the AWT thread.
     */
    private static void commitSdkPathsIfChanged(@Nonnull Sdk sdk,
                                                @Nullable final SdkModificator sdkModificator,
                                                @Nonnull final List<VirtualFile> sdkPaths,
                                                boolean forceCommit) {
        final String key = PythonSdkType.getSdkKey(sdk);
        final SdkModificator modificatorToGetRoots = sdkModificator != null ? sdkModificator : sdk.getSdkModificator();
        final List<VirtualFile> currentSdkPaths = Arrays.asList(modificatorToGetRoots.getRoots(BinariesOrderRootType.getInstance()));
        if (forceCommit || !Sets.newHashSet(sdkPaths).equals(Sets.newHashSet(currentSdkPaths))) {
            final Sdk sdkInsideInvoke = PythonSdkType.findSdkByKey(key);
            final SdkModificator modificatorToCommit =
                sdkModificator != null ? sdkModificator : sdkInsideInvoke != null ? sdkInsideInvoke.getSdkModificator() : modificatorToGetRoots;
            modificatorToCommit.removeAllRoots();
            for (VirtualFile sdkPath : sdkPaths) {
                modificatorToCommit.addRoot(PythonSdkType.getSdkRootVirtualFile(sdkPath), BinariesOrderRootType.getInstance());
            }
            modificatorToCommit.commitChanges();
        }
    }

    /**
     * Returns unique Python SDKs for the open modules of the project.
     */
    @Nonnull
    private static Set<Sdk> getPythonSdks(@Nonnull Project project) {
        final Set<Sdk> pythonSdks = Sets.newLinkedHashSet();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            final Sdk sdk = PythonSdkType.findPythonSdk(module);
            if (sdk != null && sdk.getSdkType() instanceof PythonSdkType) {
                pythonSdks.add(sdk);
            }
        }
        return pythonSdks;
    }
}
