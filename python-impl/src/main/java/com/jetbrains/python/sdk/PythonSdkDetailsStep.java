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

import java.awt.Component;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.swing.JComponent;

import javax.annotation.Nullable;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.projectRoots.impl.SdkImpl;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyCondaPackageService;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;

public class PythonSdkDetailsStep extends BaseListPopupStep<String>
{
	@Nullable
	private DialogWrapper myMore;
	private final Project myProject;
	private final Component myOwnerComponent;
	private final Sdk[] myExistingSdks;
	private final NullableConsumer<Sdk> mySdkAddedCallback;

	private static final String LOCAL = PyBundle.message("sdk.details.step.add.local");
	private static final String REMOTE = PyBundle.message("sdk.details.step.add.remote");
	private static final String VIRTUALENV = PyBundle.message("sdk.details.step.create.virtual.env");
	private static final String CONDA = PyBundle.message("sdk.details.step.create.conda.env");
	private static final String MORE = PyBundle.message("sdk.details.step.show.more");
	private boolean myNewProject;

	public static void show(final Project project,
			final Sdk[] existingSdks,
			@Nullable final DialogWrapper moreDialog,
			JComponent ownerComponent,
			final Point popupPoint,
			final NullableConsumer<Sdk> sdkAddedCallback)
	{
		show(project, existingSdks, moreDialog, ownerComponent, popupPoint, sdkAddedCallback, false);

	}

	public static void show(final Project project,
			final Sdk[] existingSdks,
			@Nullable final DialogWrapper moreDialog,
			JComponent ownerComponent,
			final Point popupPoint,
			final NullableConsumer<Sdk> sdkAddedCallback,
			boolean isNewProject)
	{
		final PythonSdkDetailsStep sdkHomesStep = new PythonSdkDetailsStep(project, moreDialog, ownerComponent, existingSdks, sdkAddedCallback);
		sdkHomesStep.setNewProject(isNewProject);
		final ListPopup popup = JBPopupFactory.getInstance().createListPopup(sdkHomesStep);
		popup.showInScreenCoordinates(ownerComponent, popupPoint);
	}

	private void setNewProject(boolean isNewProject)
	{
		myNewProject = isNewProject;
	}

	public PythonSdkDetailsStep(@Nullable final Project project,
			@Nullable final DialogWrapper moreDialog,
			@Nonnull final Component ownerComponent,
			@Nonnull final Sdk[] existingSdks,
			@Nonnull final NullableConsumer<Sdk> sdkAddedCallback)
	{
		super(null, getAvailableOptions(moreDialog != null));
		myProject = project;
		myMore = moreDialog;
		myOwnerComponent = ownerComponent;
		myExistingSdks = existingSdks;
		mySdkAddedCallback = sdkAddedCallback;
	}

	private static List<String> getAvailableOptions(boolean showMore)
	{
		final List<String> options = new ArrayList<>();
		options.add(LOCAL);
		if(PythonRemoteInterpreterManager.getInstance() != null)
		{
			options.add(REMOTE);
		}
		options.add(VIRTUALENV);
		if(PyCondaPackageService.getSystemCondaExecutable() != null)
		{
			options.add(CONDA);
		}

		if(showMore)
		{
			options.add(MORE);
		}
		return options;
	}

	@Nullable
	@Override
	public ListSeparator getSeparatorAbove(String value)
	{
		return MORE.equals(value) ? new ListSeparator() : null;
	}

	private void optionSelected(final String selectedValue)
	{
		if(!MORE.equals(selectedValue) && myMore != null)
		{
			Disposer.dispose(myMore.getDisposable());
		}
		if(LOCAL.equals(selectedValue))
		{
			createLocalSdk();
		}
		else if(REMOTE.equals(selectedValue))
		{
			createRemoteSdk();
		}
		else if(VIRTUALENV.equals(selectedValue))
		{
			createVirtualEnvSdk();
		}
		else if(CONDA.equals(selectedValue))
		{
			createCondaEnvSdk();
		}
		else if(myMore != null)
		{
			myMore.show();
		}
	}

	private void createLocalSdk()
	{
		createSdk(myProject, myExistingSdks, mySdkAddedCallback, false, PythonSdkType.getInstance());
	}

	private void createRemoteSdk()
	{
		PythonRemoteInterpreterManager remoteInterpreterManager = PythonRemoteInterpreterManager.getInstance();
		if(remoteInterpreterManager != null)
		{
			remoteInterpreterManager.addRemoteSdk(myProject, myOwnerComponent, Lists.newArrayList(myExistingSdks), mySdkAddedCallback);
		}
		else
		{
			final String pathToPluginsPage = ShowSettingsUtil.getSettingsMenuName() + " | Plugins";
			Messages.showErrorDialog(PyBundle.message("remote.interpreter.error.plugin.missing", pathToPluginsPage), PyBundle.message("remote.interpreter.add.title"));
		}
	}

	private void createVirtualEnvSdk()
	{
		AbstractCreateVirtualEnvDialog.VirtualEnvCallback callback = getVEnvCallback();

		final CreateVirtualEnvDialog dialog;
		final List<Sdk> allSdks = Lists.newArrayList(myExistingSdks);
		Iterables.removeIf(allSdks, new Predicate<Sdk>()
		{
			@Override
			public boolean apply(Sdk sdk)
			{
				return !(sdk.getSdkType() instanceof PythonSdkType);
			}
		});
		final List<PythonSdkFlavor> flavors = PythonSdkFlavor.getApplicableFlavors();
		for(PythonSdkFlavor flavor : flavors)
		{
			final Collection<String> strings = flavor.suggestHomePaths();
			for(String string : filterExistingPaths(PythonSdkType.getInstance(), strings, myExistingSdks))
			{
				allSdks.add(new PyDetectedSdk(string));
			}
		}
		if(myProject != null)
		{
			dialog = new CreateVirtualEnvDialog(myProject, allSdks);
		}
		else
		{
			dialog = new CreateVirtualEnvDialog(myOwnerComponent, allSdks);
		}
		if(dialog.showAndGet())
		{
			dialog.createVirtualEnv(callback);
		}
	}


	public static void createSdk(@Nullable final Project project, final Sdk[] existingSdks, final NullableConsumer<Sdk> onSdkCreatedCallBack, final boolean createIfExists, final SdkType... sdkTypes)
	{
		if(sdkTypes.length == 0)
		{
			onSdkCreatedCallBack.consume(null);
			return;
		}

		FileChooserDescriptor descriptor = createCompositeDescriptor(sdkTypes);
		VirtualFile suggestedDir = getSuggestedSdkRoot(sdkTypes[0]);
		FileChooser.chooseFiles(descriptor, project, suggestedDir, new FileChooser.FileChooserConsumer()
		{
			@Override
			public void consume(List<VirtualFile> selectedFiles)
			{
				for(SdkType sdkType : sdkTypes)
				{
					final String path = selectedFiles.get(0).getPath();
					if(sdkType.isValidSdkHome(path))
					{
						Sdk newSdk = null;
						if(!createIfExists)
						{
							for(Sdk sdk : existingSdks)
							{
								if(path.equals(sdk.getHomePath()))
								{
									newSdk = sdk;
									break;
								}
							}
						}
						if(newSdk == null)
						{
							newSdk = setupSdk(existingSdks, selectedFiles.get(0), sdkType, false, null, null);
						}
						onSdkCreatedCallBack.consume(newSdk);
						return;
					}
				}
				onSdkCreatedCallBack.consume(null);
			}

			@Override
			public void cancelled()
			{
				onSdkCreatedCallBack.consume(null);
			}
		});
	}

	@Nullable
	public static Sdk setupSdk(@Nonnull Sdk[] allSdks,
			@Nonnull VirtualFile homeDir,
			final SdkType sdkType,
			final boolean silent,
			@Nullable final SdkAdditionalData additionalData,
			@Nullable final String customSdkSuggestedName)
	{
		final SdkImpl sdk;
		try
		{
			sdk = createSdk(allSdks, homeDir, sdkType, additionalData, customSdkSuggestedName);

			sdkType.setupSdkPaths(sdk);
		}
		catch(Exception e)
		{
			if(!silent)
			{
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

	public static SdkImpl createSdk(@Nonnull Sdk[] allSdks, @Nonnull VirtualFile homeDir, SdkType sdkType, @Nullable SdkAdditionalData additionalData, @Nullable String customSdkSuggestedName)
	{
		final List<Sdk> sdksList = Arrays.asList(allSdks);

		String sdkPath = sdkType.sdkPath(homeDir);

		Sdk[] sdks = sdksList.toArray(new Sdk[sdksList.size()]);
		final String sdkName = customSdkSuggestedName == null ? SdkConfigurationUtil.createUniqueSdkName(sdkType, sdkPath, sdks) : SdkConfigurationUtil.createUniqueSdkName(customSdkSuggestedName,
				sdks);

		SdkImpl sdk = new SdkImpl(SdkTable.getInstance(), sdkName, sdkType);

		if(additionalData != null)
		{
			// additional initialization.
			// E.g. some ruby sdks must be initialized before
			// setupSdkPaths() method invocation
			sdk.setSdkAdditionalData(additionalData);
		}

		sdk.setHomePath(sdkPath);
		return sdk;
	}

	private static FileChooserDescriptor createCompositeDescriptor(final SdkType... sdkTypes)
	{
		return new FileChooserDescriptor(sdkTypes[0].getHomeChooserDescriptor())
		{
			@Override
			public void validateSelectedFiles(final VirtualFile[] files) throws Exception
			{
				if(files.length > 0)
				{
					for(SdkType type : sdkTypes)
					{
						if(type.isValidSdkHome(files[0].getPath()))
						{
							return;
						}
					}
				}
				String key = files.length > 0 && files[0].isDirectory() ? "sdk.configure.home.invalid.error" : "sdk.configure.home.file.invalid.error";
				throw new Exception(ProjectBundle.message(key, sdkTypes[0].getPresentableName()));
			}
		};
	}

	@Nullable
	public static VirtualFile getSuggestedSdkRoot(@Nonnull SdkType sdkType)
	{
		final String homePath = ContainerUtil.getFirstItem(sdkType.suggestHomePaths());
		return homePath == null ? null : LocalFileSystem.getInstance().findFileByPath(homePath);
	}

	@Nonnull
	public static List<String> filterExistingPaths(@Nonnull SdkType sdkType, Collection<String> sdkHomes, final Sdk[] sdks)
	{
		List<String> result = new ArrayList<>();
		for(String sdkHome : sdkHomes)
		{
			if(findByPath(sdkType, sdks, sdkHome) == null)
			{
				result.add(sdkHome);
			}
		}
		return result;
	}

	@Nullable
	private static Sdk findByPath(@Nonnull SdkType sdkType, @Nonnull Sdk[] sdks, @Nonnull String sdkHome)
	{
		for(Sdk sdk : sdks)
		{
			final String path = sdk.getHomePath();
			if(sdk.getSdkType() == sdkType && path != null &&
					FileUtil.pathsEqual(FileUtil.toSystemIndependentName(path), FileUtil.toSystemIndependentName(sdkHome)))
			{
				return sdk;
			}
		}
		return null;
	}

	@Nonnull
	private AbstractCreateVirtualEnvDialog.VirtualEnvCallback getVEnvCallback()
	{
		return new CreateVirtualEnvDialog.VirtualEnvCallback()
		{
			@Override
			public void virtualEnvCreated(Sdk sdk, boolean associateWithProject)
			{
				if(associateWithProject)
				{
					SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
					if(additionalData == null)
					{
						additionalData = new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(sdk.getHomePath()));
						((SdkImpl) sdk).setSdkAdditionalData(additionalData);
					}
					if(myNewProject)
					{
						((PythonSdkAdditionalData) additionalData).associateWithNewProject();
					}
					else
					{
						((PythonSdkAdditionalData) additionalData).associateWithProject(myProject);
					}
				}
				mySdkAddedCallback.consume(sdk);
			}
		};
	}

	private void createCondaEnvSdk()
	{
		AbstractCreateVirtualEnvDialog.VirtualEnvCallback callback = getVEnvCallback();

		final CreateCondaEnvDialog dialog;
		if(myProject != null)
		{
			dialog = new CreateCondaEnvDialog(myProject);
		}
		else
		{
			dialog = new CreateCondaEnvDialog(myOwnerComponent);
		}
		if(dialog.showAndGet())
		{
			dialog.createVirtualEnv(callback);
		}
	}

	@Override
	public boolean canBeHidden(String value)
	{
		return true;
	}

	@Override
	public void canceled()
	{
		if(getFinalRunnable() == null && myMore != null)
		{
			Disposer.dispose(myMore.getDisposable());
		}
	}

	@Override
	public PopupStep onChosen(final String selectedValue, boolean finalChoice)
	{
		return doFinalStep(() -> optionSelected(selectedValue));
	}
}
