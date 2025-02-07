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
package com.jetbrains.python.impl.packaging.ui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.jetbrains.python.impl.packaging.*;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.content.bundle.Sdk;
import consulo.ide.impl.idea.webcore.packaging.InstalledPackagesPanel;
import consulo.ide.impl.idea.webcore.packaging.PackagesNotificationPanel;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.repository.ui.InstalledPackage;
import consulo.repository.ui.PackageManagementService;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyInstalledPackagesPanel extends InstalledPackagesPanel
{
	private boolean myHasManagement = false;

	public PyInstalledPackagesPanel(Project project, PackagesNotificationPanel area)
	{
		super(project, area);
	}

	private Sdk getSelectedSdk()
	{
		PyPackageManagementService service = (PyPackageManagementService) myPackageManagementService;
		return service != null ? service.getSdk() : null;
	}

	class PyInstallPackageManagementFix implements PyExecutionFix
	{
		@Nonnull
		@Override
		public String getName()
		{
			return "Install packaging tools";
		}

		@Override
		public void run(@Nonnull final Sdk sdk)
		{
			final PyPackageManagerUI ui = new PyPackageManagerUI(myProject, sdk, new PyPackageManagerUI.Listener()
			{
				@Override
				public void started()
				{
					myPackagesTable.setPaintBusy(true);
				}

				@Override
				public void finished(List<ExecutionException> exceptions)
				{
					myPackagesTable.setPaintBusy(false);
					PyPackageManager packageManager = PyPackageManager.getInstance(sdk);
					final PackageManagementService.ErrorDescription description = PyPackageManagementService.toErrorDescription(exceptions, sdk);
					if(description != null)
					{
						consulo.ide.impl.idea.webcore.packaging.PackagesNotificationPanel.showError("Failed to install Python packaging tools", description);
					}
					packageManager.refresh();
					updatePackages(PyPackageManagers.getInstance().getManagementService(myProject, sdk));
					updateNotifications(sdk);
				}
			});
			ui.installManagement();
		}
	}

	public void updateNotifications(@Nullable final Sdk selectedSdk)
	{
		if(selectedSdk == null)
		{
			myNotificationArea.hide();
			return;
		}
		final Application application = ApplicationManager.getApplication();
		application.executeOnPooledThread(() -> {
			PyExecutionException exception = null;
			try
			{
				myHasManagement = PyPackageManager.getInstance(selectedSdk).hasManagement();
				if(!myHasManagement)
				{
					throw new PyExecutionException("Python packaging tools not found", "pip", Collections.<String>emptyList(), "", "", 0, ImmutableList.of(new PyInstallPackageManagementFix()));
				}
			}
			catch(PyExecutionException e)
			{
				exception = e;
			}
			catch(ExecutionException e)
			{
				return;
			}
			final PyExecutionException problem = exception;
			application.invokeLater(() -> {
				if(selectedSdk == getSelectedSdk())
				{
					myNotificationArea.hide();
					if(problem != null)
					{
						final boolean invalid = PythonSdkType.isInvalid(selectedSdk);
						if(!invalid)
						{
							final StringBuilder builder = new StringBuilder(problem.getMessage());
							builder.append(". ");
							for(final PyExecutionFix fix : problem.getFixes())
							{
								final String key = "id" + fix.hashCode();
								final String link = "<a href=\"" + key + "\">" + fix.getName() + "</a>";
								builder.append(link);
								builder.append(" ");
								myNotificationArea.addLinkHandler(key, () -> {
									final Sdk sdk = getSelectedSdk();
									if(sdk != null)
									{
										fix.run(sdk);
										myNotificationArea.removeLinkHandler(key);
										updatePackages(PyPackageManagers.getInstance().getManagementService(myProject, sdk));
										updateNotifications(sdk);
									}
								});
							}
							myNotificationArea.showWarning(builder.toString());
						}
						myInstallButton.setEnabled(!invalid && installEnabled());
					}
				}
			}, application.getAnyModalityState());
		});
	}

	@Override
	protected Set<String> getPackagesToPostpone()
	{
		return Sets.newHashSet("pip", "distutils", "setuptools");
	}

	@Override
	protected boolean canUninstallPackage(InstalledPackage pkg)
	{
		if(!myHasManagement)
		{
			return false;
		}

		if(!PyPackageUtil.packageManagementEnabled(getSelectedSdk()))
		{
			return false;
		}

		if(PythonSdkType.isVirtualEnv(getSelectedSdk()) && pkg instanceof PyPackage)
		{
			final String location = ((PyPackage) pkg).getLocation();
			if(location != null && location.startsWith(PySdkUtil.getUserSite()))
			{
				return false;
			}
		}
		final String name = pkg.getName();
		if(PyPackageUtil.PIP.equals(name) ||
				PyPackageUtil.SETUPTOOLS.equals(name) ||
				PyPackageUtil.DISTRIBUTE.equals(name) ||
				PyCondaPackageManagerImpl.PYTHON.equals(name))
		{
			return false;
		}
		return true;
	}

	@Override
	protected boolean canInstallPackage(@Nonnull final InstalledPackage pyPackage)
	{
		return installEnabled();
	}

	@Override
	protected boolean installEnabled()
	{
		if(!PyPackageUtil.packageManagementEnabled(getSelectedSdk()))
		{
			return false;
		}

		return myHasManagement;
	}

	@Override
	protected boolean canUpgradePackage(InstalledPackage pyPackage)
	{
		if(!PyPackageUtil.packageManagementEnabled(getSelectedSdk()))
		{
			return false;
		}

		return myHasManagement && !PyCondaPackageManagerImpl.PYTHON.equals(pyPackage.getName());
	}
}
