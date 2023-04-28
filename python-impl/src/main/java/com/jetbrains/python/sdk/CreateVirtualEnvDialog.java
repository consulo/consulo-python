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
package com.jetbrains.python.sdk;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyPackageManager;
import consulo.content.bundle.Sdk;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.IdeaFileChooser;
import consulo.ide.impl.idea.remote.RemoteSdkCredentialsHolder;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.ui.ex.awt.*;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class CreateVirtualEnvDialog extends AbstractCreateVirtualEnvDialog {
  private JComboBox mySdkCombo;
  private JBCheckBox mySitePackagesCheckBox;

  public CreateVirtualEnvDialog(Project project, final List<Sdk> allSdks) {
    super(project, allSdks);
  }

  public CreateVirtualEnvDialog(Component owner, final List<Sdk> allSdks) {
    super(owner, allSdks);
  }

  void setupDialog(Project project, final List<Sdk> allSdks) {
    super.setupDialog(project, allSdks);

    setTitle(PyBundle.message("sdk.create.venv.dialog.title"));

    allSdks.removeIf(new Predicate<Sdk>() {
      @Override
      public boolean test(Sdk s) {
        return PythonSdkType.isInvalid(s) || PythonSdkType.isVirtualEnv(s) || RemoteSdkCredentialsHolder.isRemoteSdk(
          s.getHomePath()) ||
          PythonSdkType.isCondaVirtualEnv(s);
      }
    });
    List<Sdk> sortedSdks = new ArrayList<>(allSdks);
    Collections.sort(sortedSdks, new PreferredSdkComparator());
    updateSdkList(allSdks, sortedSdks.isEmpty() ? null : sortedSdks.get(0));
  }

  protected void layoutPanel(final List<Sdk> allSdks) {

    final GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = new Insets(2, 2, 2, 2);

    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 0.0;
    myMainPanel.add(new JBLabel(PyBundle.message("sdk.create.venv.dialog.label.name")), c);

    c.gridx = 1;
    c.gridy = 0;
    c.gridwidth = 2;
    c.weightx = 1.0;

    myMainPanel.add(myName, c);

    c.gridx = 0;
    c.gridy = 1;
    c.gridwidth = 1;
    c.weightx = 0.0;
    myMainPanel.add(new JBLabel(PyBundle.message("sdk.create.venv.dialog.label.location")), c);

    c.gridx = 1;
    c.gridy = 1;
    c.gridwidth = 2;
    c.weightx = 1.0;
    myMainPanel.add(myDestination, c);

    c.gridx = 0;
    c.gridy = 2;
    c.gridwidth = 1;
    c.weightx = 0.0;
    myMainPanel.add(new JBLabel(PyBundle.message("sdk.create.venv.dialog.label.base.interpreter")), c);

    c.gridx = 1;
    c.gridy = 2;
    mySdkCombo = new ComboBox();
    c.insets = new Insets(2, 2, 2, 2);
    c.weightx = 1.0;
    myMainPanel.add(mySdkCombo, c);

    c.gridx = 2;
    c.gridy = 2;
    c.insets = new Insets(0, 0, 2, 2);
    c.weightx = 0.0;
    FixedSizeButton button = new FixedSizeButton();
    button.setPreferredSize(myDestination.getButton().getPreferredSize());
    myMainPanel.add(button, c);

    c.gridx = 0;
    c.gridy = 3;
    c.gridwidth = 3;
    c.insets = new Insets(2, 2, 2, 2);
    mySitePackagesCheckBox = new JBCheckBox(PyBundle.message("sdk.create.venv.dialog.label.inherit.global.site.packages"));
    myMainPanel.add(mySitePackagesCheckBox, c);

    c.gridx = 0;
    c.gridy = 4;

    myMainPanel.add(myMakeAvailableToAllProjectsCheckbox, c);
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final PythonSdkType sdkType = PythonSdkType.getInstance();
        final FileChooserDescriptor descriptor = sdkType.getHomeChooserDescriptor();

        String suggestedPath = ContainerUtil.getFirstItem(sdkType.suggestHomePaths());
        VirtualFile suggestedDir =
          suggestedPath == null ? null : LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(suggestedPath));
        final Consumer<Sdk> consumer = sdk -> {
          if (sdk == null) {
            return;
          }
          if (!allSdks.contains(sdk)) {
            allSdks.add(sdk);
          }
          updateSdkList(allSdks, sdk);
        };
        IdeaFileChooser.chooseFiles(descriptor, myProject, suggestedDir, new Consumer<List<VirtualFile>>() {
          @Override
          public void accept(List<VirtualFile> selectedFiles) {
            String path = selectedFiles.get(0).getPath();
            if (sdkType.isValidSdkHome(path)) {
              path = FileUtil.toSystemDependentName(path);
              Sdk newSdk = null;
              for (Sdk sdk : allSdks) {
                if (path.equals(sdk.getHomePath())) {
                  newSdk = sdk;
                }
              }
              if (newSdk == null) {
                newSdk = new PyDetectedSdk(path);
              }
              consumer.accept(newSdk);
            }
          }
        });

      }
    });
  }

  protected void checkValid() {
    setOKActionEnabled(true);
    setErrorText(null);

    super.checkValid();
    if (mySdkCombo.getSelectedItem() == null) {
      setOKActionEnabled(false);
      setErrorText(PyBundle.message("sdk.create.venv.dialog.error.no.base.interpreter"));
    }
  }

	/*protected void registerValidators(final FacetValidatorsManager validatorsManager)
  {
		super.registerValidators(validatorsManager);

		mySdkCombo.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent event)
			{
				validatorsManager.validate();
			}
		});
	} */

  private void updateSdkList(final List<Sdk> allSdks, @Nullable Sdk initialSelection) {
    mySdkCombo.setRenderer(new PySdkListCellRenderer(false));
    mySdkCombo.setModel(new CollectionComboBoxModel<>(allSdks, initialSelection));
    checkValid();
  }

  public String getDestination() {
    return myDestination.getText();
  }

  public String getName() {
    return myName.getText();
  }

  public Sdk getSdk() {
    return (Sdk)mySdkCombo.getSelectedItem();
  }

  @Override
  public boolean useGlobalSitePackages() {
    return mySitePackagesCheckBox.isSelected();
  }

  protected String createEnvironment(Sdk basicSdk) throws ExecutionException {
    final PyPackageManager packageManager = PyPackageManager.getInstance(basicSdk);
    return packageManager.createVirtualEnv(getDestination(), useGlobalSitePackages());
  }
}
