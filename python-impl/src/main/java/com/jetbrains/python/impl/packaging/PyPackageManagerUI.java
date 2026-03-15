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

import com.jetbrains.python.impl.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageManagers;
import com.jetbrains.python.packaging.PyRequirement;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.content.bundle.Sdk;
import consulo.execution.RunCanceledByUserException;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;
import consulo.project.ui.notification.NotificationsManager;
import consulo.project.ui.notification.event.NotificationListener;
import consulo.repository.ui.PackageManagementService;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * @author vlan
 */
public class PyPackageManagerUI {
    private static final Logger LOG = Logger.getInstance(PyPackageManagerUI.class);

    @Nullable
    private Listener myListener;
    private Project myProject;
    private Sdk mySdk;

    public interface Listener {
        void started();

        void finished(List<ExecutionException> exceptions);
    }

    public PyPackageManagerUI(Project project, Sdk sdk, @Nullable Listener listener) {
        myProject = project;
        mySdk = sdk;
        myListener = listener;
    }

    public void installManagement() {
        ProgressManager.getInstance().run(new InstallManagementTask(myProject, mySdk, myListener));
    }

    public void install(List<PyRequirement> requirements, List<String> extraArgs) {
        ProgressManager.getInstance().run(new InstallTask(myProject, mySdk, requirements, extraArgs, myListener));
    }

    @RequiredUIAccess
    public void uninstall(List<PyPackage> packages) {
        if (checkDependents(packages)) {
            return;
        }
        ProgressManager.getInstance().run(new UninstallTask(myProject, mySdk, myListener, packages));
    }

    @RequiredUIAccess
    private boolean checkDependents(List<PyPackage> packages) {
        try {
            Map<String, Set<PyPackage>> dependentPackages = collectDependents(packages, mySdk);
            int[] warning = {0};
            if (!dependentPackages.isEmpty()) {
                Application application = myProject.getApplication();
                application.invokeAndWait(
                    () -> {
                        if (dependentPackages.size() == 1) {
                            String message = "You are attempting to uninstall ";
                            List<String> dep = new ArrayList<>();
                            int size = 1;
                            for (Map.Entry<String, Set<PyPackage>> entry : dependentPackages.entrySet()) {
                                Set<PyPackage> value = entry.getValue();
                                size = value.size();
                                dep.add(entry.getKey() + " package which is required for " + StringUtil.join(value, ", "));
                            }
                            message += StringUtil.join(dep, "\n");
                            message += size == 1 ? " package" : " packages";
                            message += "\n\nDo you want to proceed?";
                            warning[0] = Messages.showYesNoDialog(message, "Warning", PlatformIconGroup.generalBalloonwarning());
                        }
                        else {
                            String message = "You are attempting to uninstall packages which are required for another packages.\n\n";
                            List<String> dep = new ArrayList<>();
                            for (Map.Entry<String, Set<PyPackage>> entry : dependentPackages.entrySet()) {
                                dep.add(entry.getKey() + " -> " + StringUtil.join(entry.getValue(), ", "));
                            }
                            message += StringUtil.join(dep, "\n");
                            message += "\n\nDo you want to proceed?";
                            warning[0] = Messages.showYesNoDialog(message, "Warning", PlatformIconGroup.generalBalloonwarning());
                        }
                    },
                    application.getCurrentModalityState()
                );
            }
            if (warning[0] != Messages.YES) {
                return true;
            }
        }
        catch (ExecutionException e) {
            LOG.info("Error loading packages dependents: " + e.getMessage(), e);
        }
        return false;
    }

    private static Map<String, Set<PyPackage>> collectDependents(List<PyPackage> packages, Sdk sdk) throws ExecutionException {
        Map<String, Set<PyPackage>> dependentPackages = new HashMap<>();
        for (PyPackage pkg : packages) {
            Set<PyPackage> dependents = PyPackageManager.getInstance(sdk).getDependents(pkg);
            if (!dependents.isEmpty()) {
                for (PyPackage dependent : dependents) {
                    if (!packages.contains(dependent)) {
                        dependentPackages.put(pkg.getName(), dependents);
                    }
                }
            }
        }
        return dependentPackages;
    }

    private abstract static class PackagingTask extends Task.Backgroundable {
        private static final NotificationGroup PACKAGING_GROUP_ID = NotificationGroup.balloonGroup("Python Packaging");

        protected final Sdk mySdk;
        @Nullable
        protected final Listener myListener;

        public PackagingTask(@Nullable Project project, Sdk sdk, String title, @Nullable Listener listener) {
            super(project, title);
            mySdk = sdk;
            myListener = listener;
        }

        @Override
        public void run(ProgressIndicator indicator) {
            taskStarted(indicator);
            taskFinished(runTask(indicator));
        }

        protected abstract List<ExecutionException> runTask(ProgressIndicator indicator);

        protected abstract String getSuccessTitle();

        protected abstract String getSuccessDescription();

        protected abstract String getFailureTitle();

        protected void taskStarted(ProgressIndicator indicator) {
            Project project = (Project) getProject();
            PackagingNotification[] notifications =
                NotificationsManager.getNotificationsManager().getNotificationsOfType(PackagingNotification.class, project);
            for (PackagingNotification notification : notifications) {
                notification.expire();
            }
            indicator.setText(getTitle() + "...");
            if (myListener != null) {
                project.getApplication().invokeLater(myListener::started);
            }
        }

        protected void taskFinished(List<ExecutionException> exceptions) {
            SimpleReference<Notification> notificationRef = new SimpleReference<>(null);
            if (exceptions.isEmpty()) {
                notificationRef.set(new PackagingNotification(
                    PACKAGING_GROUP_ID,
                    getSuccessTitle(),
                    getSuccessDescription(),
                    NotificationType.INFORMATION,
                    null
                ));
            }
            else {
                PackageManagementService.ErrorDescription description = PyPackageManagementService.toErrorDescription(exceptions, mySdk);
                if (description != null) {
                    String firstLine = getTitle() + ": error occurred.";
                    NotificationListener listener = (notification, event) -> {
                        assert myProject != null;
                        String title = StringUtil.capitalizeWords(getFailureTitle(), true);
                        consulo.ide.impl.idea.webcore.packaging.PackagesNotificationPanel.showError(title, description);
                    };
                    notificationRef.set(new PackagingNotification(
                        PACKAGING_GROUP_ID,
                        getFailureTitle(),
                        firstLine + " <a href=\"xxx\">Details...</a>",
                        consulo.project.ui.notification.NotificationType.ERROR,
                        listener
                    ));
                }
            }
            Project project = (Project) myProject;
            project.getApplication().invokeLater(() -> {
                if (myListener != null) {
                    myListener.finished(exceptions);
                }
                Notification notification = notificationRef.get();
                if (notification != null) {
                    notification.notify(project);
                }
            });
        }

        private static class PackagingNotification extends Notification {

            public PackagingNotification(
                NotificationGroup groupDisplayId,
                String title,
                String content,
                consulo.project.ui.notification.NotificationType type,
                @Nullable NotificationListener listener
            ) {
                super(groupDisplayId, title, content, type, listener);
            }
        }
    }

    private static class InstallTask extends PackagingTask {
        private final List<PyRequirement> myRequirements;
        private final List<String> myExtraArgs;

        public InstallTask(
            @Nullable Project project,
            Sdk sdk,
            List<PyRequirement> requirements,
            List<String> extraArgs,
            @Nullable Listener listener
        ) {
            super(project, sdk, "Installing packages", listener);
            myRequirements = requirements;
            myExtraArgs = extraArgs;
        }

        @Override
        protected List<ExecutionException> runTask(ProgressIndicator indicator) {
            List<ExecutionException> exceptions = new ArrayList<>();
            int size = myRequirements.size();
            PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
            for (int i = 0; i < size; i++) {
                PyRequirement requirement = myRequirements.get(i);
                indicator.setText(String.format("Installing package '%s'...", requirement));
                if (i == 0) {
                    indicator.setIndeterminate(true);
                }
                else {
                    indicator.setIndeterminate(false);
                    indicator.setFraction((double) i / size);
                }
                try {
                    manager.install(Collections.singletonList(requirement), myExtraArgs);
                }
                catch (RunCanceledByUserException e) {
                    exceptions.add(e);
                    break;
                }
                catch (ExecutionException e) {
                    exceptions.add(e);
                }
            }
            manager.refresh();
            return exceptions;
        }

        @Override
        protected String getSuccessTitle() {
            return "Packages installed successfully";
        }

        @Override
        protected String getSuccessDescription() {
            return "Installed packages: " + PyPackageUtil.requirementsToString(myRequirements);
        }

        @Override
        protected String getFailureTitle() {
            return "Install packages failed";
        }
    }

    private static class InstallManagementTask extends InstallTask {

        public InstallManagementTask(@Nullable Project project, Sdk sdk, @Nullable Listener listener) {
            super(project, sdk, Collections.<PyRequirement>emptyList(), Collections.<String>emptyList(), listener);
        }

        @Override
        protected List<ExecutionException> runTask(ProgressIndicator indicator) {
            List<ExecutionException> exceptions = new ArrayList<>();
            PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
            indicator.setText("Installing packaging tools...");
            indicator.setIndeterminate(true);
            try {
                manager.installManagement();
            }
            catch (ExecutionException e) {
                exceptions.add(e);
            }
            manager.refresh();
            return exceptions;
        }

        @Override
        protected String getSuccessDescription() {
            return "Installed Python packaging tools";
        }
    }

    private static class UninstallTask extends PackagingTask {
        private final List<PyPackage> myPackages;

        public UninstallTask(@Nullable Project project, Sdk sdk, @Nullable Listener listener, List<PyPackage> packages) {
            super(project, sdk, "Uninstalling packages", listener);
            myPackages = packages;
        }

        @Override
        protected List<ExecutionException> runTask(ProgressIndicator indicator) {
            PyPackageManager manager = PyPackageManagers.getInstance().forSdk(mySdk);
            indicator.setIndeterminate(true);
            try {
                manager.uninstall(myPackages);
                return Collections.emptyList();
            }
            catch (ExecutionException e) {
                return Collections.singletonList(e);
            }
            finally {
                manager.refresh();
            }
        }

        @Override
        protected String getSuccessTitle() {
            return "Packages uninstalled successfully";
        }

        @Override
        protected String getSuccessDescription() {
            String packagesString = StringUtil.join(myPackages, pkg -> "'" + pkg.getName() + "'", ", ");
            return "Uninstalled packages: " + packagesString;
        }

        @Override
        protected String getFailureTitle() {
            return "Uninstall packages failed";
        }
    }
}
