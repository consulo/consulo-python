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
package com.jetbrains.python.impl.sdk;

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.packaging.PyCondaPackageService;
import com.jetbrains.python.impl.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import consulo.content.bundle.*;
import consulo.disposer.Disposer;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.IdeaFileChooser;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.project.Project;
import consulo.project.ProjectBundle;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.popup.*;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class PythonSdkDetailsStep extends BaseListPopupStep<String> {
  @Nullable
  private DialogWrapper myMore;
  private final Project myProject;
  private final Component myOwnerComponent;
  private final Sdk[] myExistingSdks;
  private final Consumer<Sdk> mySdkAddedCallback;

  private static final String LOCAL = PyBundle.message("sdk.details.step.add.local");
  private static final String MORE = PyBundle.message("sdk.details.step.show.more");
  private boolean myNewProject;

  public static void show(final Project project,
                          final Sdk[] existingSdks,
                          @Nullable final DialogWrapper moreDialog,
                          JComponent ownerComponent,
                          final Point popupPoint,
                          final Consumer<Sdk> sdkAddedCallback) {
    show(project, existingSdks, moreDialog, ownerComponent, popupPoint, sdkAddedCallback, false);

  }

  public static void show(final Project project,
                          final Sdk[] existingSdks,
                          @Nullable final DialogWrapper moreDialog,
                          JComponent ownerComponent,
                          final Point popupPoint,
                          final Consumer<Sdk> sdkAddedCallback,
                          boolean isNewProject) {
    final PythonSdkDetailsStep sdkHomesStep = new PythonSdkDetailsStep(project, moreDialog, ownerComponent, existingSdks, sdkAddedCallback);
    sdkHomesStep.setNewProject(isNewProject);
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(sdkHomesStep);
    popup.showInScreenCoordinates(ownerComponent, popupPoint);
  }

  private void setNewProject(boolean isNewProject) {
    myNewProject = isNewProject;
  }

  public PythonSdkDetailsStep(@Nullable final Project project,
                              @Nullable final DialogWrapper moreDialog,
                              @Nonnull final Component ownerComponent,
                              @Nonnull final Sdk[] existingSdks,
                              @Nonnull final Consumer<Sdk> sdkAddedCallback) {
    super(null, getAvailableOptions(moreDialog != null));
    myProject = project;
    myMore = moreDialog;
    myOwnerComponent = ownerComponent;
    myExistingSdks = existingSdks;
    mySdkAddedCallback = sdkAddedCallback;
  }

  private static List<String> getAvailableOptions(boolean showMore) {
    final List<String> options = new ArrayList<>();
    options.add(LOCAL);
//  options.add(VIRTUALENV);
//    if (PyCondaPackageService.getSystemCondaExecutable() != null) {
//      options.add(CONDA);
//    }

    if (showMore) {
      options.add(MORE);
    }
    return options;
  }

  @Nullable
  @Override
  public ListSeparator getSeparatorAbove(String value) {
    return MORE.equals(value) ? new ListSeparator() : null;
  }

  private void optionSelected(final String selectedValue) {
    if (!MORE.equals(selectedValue) && myMore != null) {
      Disposer.dispose(myMore.getDisposable());
    }
    if (LOCAL.equals(selectedValue)) {
      createLocalSdk();
    }
    else if (myMore != null) {
      myMore.show();
    }
  }

  private void createLocalSdk() {
    createSdk(myProject, myExistingSdks, mySdkAddedCallback, false, PythonSdkType.getInstance());
  }

  public static void createSdk(@Nullable final Project project,
                               final Sdk[] existingSdks,
                               final Consumer<Sdk> onSdkCreatedCallBack,
                               final boolean createIfExists,
                               final SdkType... sdkTypes) {
    if (sdkTypes.length == 0) {
      onSdkCreatedCallBack.accept(null);
      return;
    }

    FileChooserDescriptor descriptor = createCompositeDescriptor(sdkTypes);
    VirtualFile suggestedDir = getSuggestedSdkRoot(sdkTypes[0]);
    IdeaFileChooser.chooseFiles(descriptor, project, suggestedDir, new IdeaFileChooser.FileChooserConsumer() {
      @Override
      public void accept(List<VirtualFile> selectedFiles) {
        for (SdkType sdkType : sdkTypes) {
          final String path = selectedFiles.get(0).getPath();
          if (sdkType.isValidSdkHome(path)) {
            Sdk newSdk = null;
            if (!createIfExists) {
              for (Sdk sdk : existingSdks) {
                if (path.equals(sdk.getHomePath())) {
                  newSdk = sdk;
                  break;
                }
              }
            }
            if (newSdk == null) {
              newSdk = setupSdk(existingSdks, selectedFiles.get(0), sdkType, false, null, null);
            }
            onSdkCreatedCallBack.accept(newSdk);
            return;
          }
        }
        onSdkCreatedCallBack.accept(null);
      }

      @Override
      public void cancelled() {
        onSdkCreatedCallBack.accept(null);
      }
    });
  }

  @Nullable
  public static Sdk setupSdk(@Nonnull Sdk[] allSdks,
                             @Nonnull VirtualFile homeDir,
                             final SdkType sdkType,
                             final boolean silent,
                             @Nullable final SdkAdditionalData additionalData,
                             @Nullable final String customSdkSuggestedName) {
    final Sdk sdk;
    try {
      sdk = createSdk(allSdks, homeDir, sdkType, additionalData, customSdkSuggestedName);

      sdkType.setupSdkPaths((Sdk)sdk);
    }
    catch (Exception e) {
      if (!silent) {
        Messages.showErrorDialog("Error configuring SDK: " +
                                   e.getMessage() +
                                   ".\nPlease make sure that " +
                                   FileUtil.toSystemDependentName(homeDir.getPath()) +
                                   " is a valid home path for this SDK type.", "Error Configuring SDK");
      }
      return null;
    }
    return sdk;
  }

  public static Sdk createSdk(@Nonnull Sdk[] allSdks,
                              @Nonnull VirtualFile homeDir,
                              SdkType sdkType,
                              @Nullable SdkAdditionalData additionalData,
                              @Nullable String customSdkSuggestedName) {
    final List<Sdk> sdksList = Arrays.asList(allSdks);

    String sdkPath = sdkType.sdkPath(homeDir);

    Sdk[] sdks = sdksList.toArray(new Sdk[sdksList.size()]);
    final String sdkName = customSdkSuggestedName == null ? SdkUtil.createUniqueSdkName(sdkType,
                                                                                        sdkPath,
                                                                                        sdks) : SdkUtil.createUniqueSdkName(
      customSdkSuggestedName,
      sdks);

    Sdk sdk = SdkTable.getInstance().createSdk(sdkName, sdkType);

    SdkModificator sdkModificator = sdk.getSdkModificator();

    if (additionalData != null) {
      // additional initialization.
      // E.g. some ruby sdks must be initialized before
      // setupSdkPaths() method invocation
      sdkModificator.setSdkAdditionalData(additionalData);
    }

    sdkModificator.setHomePath(sdkPath);
    sdkModificator.commitChanges();

    return sdk;
  }

  private static FileChooserDescriptor createCompositeDescriptor(final SdkType... sdkTypes) {
    return new FileChooserDescriptor(sdkTypes[0].getHomeChooserDescriptor()) {
      @Override
      public void validateSelectedFiles(final VirtualFile[] files) throws Exception {
        if (files.length > 0) {
          for (SdkType type : sdkTypes) {
            if (type.isValidSdkHome(files[0].getPath())) {
              return;
            }
          }
        }
        String key =
          files.length > 0 && files[0].isDirectory() ? "sdk.configure.home.invalid.error" : "sdk.configure.home.file.invalid.error";
        throw new Exception(ProjectBundle.message(key, sdkTypes[0].getPresentableName()));
      }
    };
  }

  @Nullable
  public static VirtualFile getSuggestedSdkRoot(@Nonnull SdkType sdkType) {
    final String homePath = ContainerUtil.getFirstItem(sdkType.suggestHomePaths());
    return homePath == null ? null : LocalFileSystem.getInstance().findFileByPath(homePath);
  }

  @Nonnull
  public static List<String> filterExistingPaths(@Nonnull SdkType sdkType, Collection<String> sdkHomes, final Sdk[] sdks) {
    List<String> result = new ArrayList<>();
    for (String sdkHome : sdkHomes) {
      if (findByPath(sdkType, sdks, sdkHome) == null) {
        result.add(sdkHome);
      }
    }
    return result;
  }

  @Nullable
  private static Sdk findByPath(@Nonnull SdkType sdkType, @Nonnull Sdk[] sdks, @Nonnull String sdkHome) {
    for (Sdk sdk : sdks) {
      final String path = sdk.getHomePath();
      if (sdk.getSdkType() == sdkType && path != null &&
        FileUtil.pathsEqual(FileUtil.toSystemIndependentName(path), FileUtil.toSystemIndependentName(sdkHome))) {
        return sdk;
      }
    }
    return null;
  }

  @Override
  public boolean canBeHidden(String value) {
    return true;
  }

  @Override
  public void canceled() {
    if (getFinalRunnable() == null && myMore != null) {
      Disposer.dispose(myMore.getDisposable());
    }
  }

  @Override
  public PopupStep onChosen(final String selectedValue, boolean finalChoice) {
    return doFinalStep(() -> optionSelected(selectedValue));
  }
}
