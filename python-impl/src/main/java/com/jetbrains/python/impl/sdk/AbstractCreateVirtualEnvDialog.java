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
package com.jetbrains.python.impl.sdk;

import com.jetbrains.python.impl.packaging.PyPackageService;
import com.jetbrains.python.impl.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.impl.sdk.flavors.VirtualEnvSdkFlavor;
import com.jetbrains.python.impl.ui.IdeaDialog;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkUtil;
import consulo.ide.impl.idea.webcore.packaging.PackagesNotificationPanel;
import consulo.process.ExecutionException;
import consulo.project.DumbModePermission;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.repository.ui.PackageManagementService;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.util.io.FileUtil;
import consulo.util.io.PathUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public abstract class AbstractCreateVirtualEnvDialog extends IdeaDialog {
    @Nullable
    protected Project myProject;
    protected JPanel myMainPanel;
    protected JTextField myName;
    protected TextFieldWithBrowseButton myDestination;
    protected JBCheckBox myMakeAvailableToAllProjectsCheckbox;
    protected String myInitialPath;

    public interface VirtualEnvCallback {
        void virtualEnvCreated(Sdk sdk, boolean associateWithProject);
    }

    @RequiredUIAccess
    public static void setupVirtualEnvSdk(String path, boolean associateWithProject, VirtualEnvCallback callback) {
        VirtualFile sdkHome =
            Application.get().runWriteAction((Supplier<VirtualFile>)() -> LocalFileSystem.getInstance().refreshAndFindFileByPath(path));
        if (sdkHome != null) {
            Sdk sdk =
                SdkUtil.createAndAddSDK(FileUtil.toSystemDependentName(sdkHome.getPath()), PythonSdkType.getInstance(), UIAccess.current());
            callback.virtualEnvCreated(sdk, associateWithProject);
        }
    }

    public AbstractCreateVirtualEnvDialog(Project project, List<Sdk> allSdks) {
        super(project);
        setupDialog(project, allSdks);
    }

    public AbstractCreateVirtualEnvDialog(Component owner, List<Sdk> allSdks) {
        super(owner);
        setupDialog(null, allSdks);
    }

    void setupDialog(Project project, List<Sdk> allSdks) {
        myProject = project;

        GridBagLayout layout = new GridBagLayout();
        myMainPanel = new JPanel(layout);
        myName = new JTextField();
        myDestination = new TextFieldWithBrowseButton();
        myMakeAvailableToAllProjectsCheckbox = new JBCheckBox(PyLocalize.sdkCreateVenvDialogMakeAvailableToAllProjects().get());
        myMakeAvailableToAllProjectsCheckbox.setSelected(true);
        myMakeAvailableToAllProjectsCheckbox.setVisible(false);

        layoutPanel(allSdks);
        init();
        setOKActionEnabled(false);
        /*registerValidators(new FacetValidatorsManager() {
            public void registerValidator(FacetEditorValidator validator, JComponent... componentsToWatch) {
            }

            public void validate() {
                checkValid();
            }
        });*/

        myMainPanel.setPreferredSize(new Dimension(300, 50));
        checkValid();
        setInitialDestination();
        addUpdater(myName);
        new LocationNameFieldsBinding(
            project,
            myDestination,
            myName,
            myInitialPath,
            PyLocalize.sdkCreateVenvDialogSelectVenvLocation().get()
        );
    }

    protected void setInitialDestination() {
        myInitialPath = "";

        VirtualFile file = VirtualEnvSdkFlavor.getDefaultLocation();

        if (file != null) {
            myInitialPath = file.getPath();
        }
        else {
            String savedPath = PyPackageService.getInstance().getVirtualEnvBasePath();
            if (!StringUtil.isEmptyOrSpaces(savedPath)) {
                myInitialPath = savedPath;
            }
            else if (myProject != null) {
                VirtualFile baseDir = myProject.getBaseDir();
                if (baseDir != null) {
                    myInitialPath = baseDir.getPath();
                }
            }
        }
    }

    /*protected void registerValidators(FacetValidatorsManager validatorsManager) {
        myDestination.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                validatorsManager.validate();
            }
        });

        myDestination.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                validatorsManager.validate();
            }
        });
        myName.addCaretListener(new CaretListener() {
            @Override
            public void caretUpdate(CaretEvent event) {
                validatorsManager.validate();
            }
        });

        myDestination.getTextField().addCaretListener(new CaretListener() {
            @Override
            public void caretUpdate(CaretEvent event) {
                validatorsManager.validate();
            }
        });
    }*/

    protected void checkValid() {
        String projectName = myName.getText();
        File destFile = new File(getDestination());
        if (destFile.exists()) {
            String[] content = destFile.list();
            if (content != null && content.length != 0) {
                setOKActionEnabled(false);
                setErrorText(PyLocalize.sdkCreateVenvDialogErrorNotEmptyDirectory());
                return;
            }
        }
        if (StringUtil.isEmptyOrSpaces(projectName)) {
            setOKActionEnabled(false);
            setErrorText(PyLocalize.sdkCreateVenvDialogErrorEmptyVenvName());
            return;
        }
        if (!PathUtil.isValidFileName(projectName)) {
            setOKActionEnabled(false);
            setErrorText(PyLocalize.sdkCreateVenvDialogErrorInvalidDirectoryName());
            return;
        }
        if (StringUtil.isEmptyOrSpaces(myDestination.getText())) {
            setOKActionEnabled(false);
            setErrorText(PyLocalize.sdkCreateVenvDialogErrorEmptyVenvLocation());
            return;
        }

        setOKActionEnabled(true);
        clearErrorText();
    }

    abstract protected void layoutPanel(List<Sdk> allSdks);

    @Override
    protected JComponent createCenterPanel() {
        return myMainPanel;
    }

    @Nullable
    abstract public Sdk getSdk();

    abstract public boolean useGlobalSitePackages();

    public String getDestination() {
        return myDestination.getText();
    }

    public String getName() {
        return myName.getText();
    }

    public boolean associateWithProject() {
        return !myMakeAvailableToAllProjectsCheckbox.isSelected();
    }

    public void createVirtualEnv(VirtualEnvCallback callback) {
        ProgressManager progman = ProgressManager.getInstance();
        Sdk basicSdk = getSdk();
        Task.Modal createTask = new Task.Modal(myProject, PyLocalize.sdkCreateVenvDialogCreatingVenv(), false) {
            String myPath;

            @Override
            public void run(@Nonnull ProgressIndicator indicator) {
                try {
                    indicator.setTextValue(PyLocalize.sdkCreateVenvDialogCreatingVenv());
                    myPath = createEnvironment(basicSdk);
                }
                catch (ExecutionException e) {
                    Application.get().invokeLater(
                        () -> {
                            PackageManagementService.ErrorDescription description =
                                PyPackageManagementService.toErrorDescription(Collections.singletonList(e), basicSdk);
                            if (description != null) {
                                PackagesNotificationPanel.showError(
                                    PyLocalize.sdkCreateVenvDialogErrorFailedToCreateVenv().get(),
                                    description
                                );
                            }
                        },
                        Application.get().getAnyModalityState()
                    );
                }
            }

            @Override
            @RequiredUIAccess
            public void onSuccess() {
                if (myPath != null) {
                    Application.get().invokeLater(() -> DumbService.allowStartingDumbModeInside(
                        DumbModePermission.MAY_START_BACKGROUND,
                        () -> setupVirtualEnvSdk(
                            myPath,
                            associateWithProject(),
                            callback
                        )
                    ));
                }
            }
        };
        progman.run(createTask);
    }

    abstract protected String createEnvironment(Sdk basicSdk) throws ExecutionException;

    @Override
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
        return myName;
    }

    @Override
    protected void doOKAction() {
        super.doOKAction();
        VirtualFile baseDir = myProject != null ? myProject.getBaseDir() : null;
        if (!myDestination.getText().startsWith(myInitialPath)
            && (baseDir == null || !myDestination.getText().startsWith(baseDir.getPath()))) {
            String path = myDestination.getText();
            PyPackageService.getInstance().setVirtualEnvBasePath(
                !path.contains(File.separator) ? path : path.substring(0, path.lastIndexOf(File.separator))
            );
        }
    }
}
