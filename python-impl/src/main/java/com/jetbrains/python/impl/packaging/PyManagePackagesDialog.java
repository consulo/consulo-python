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

import com.jetbrains.python.impl.packaging.ui.PyInstalledPackagesPanel;
import com.jetbrains.python.impl.sdk.PreferredSdkComparator;
import com.jetbrains.python.impl.sdk.PySdkListCellRenderer;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.packaging.PyPackageManagers;
import consulo.content.bundle.Sdk;
import consulo.ide.impl.idea.webcore.packaging.PackagesNotificationPanel;
import consulo.project.Project;
import consulo.ui.ex.awt.CollectionComboBoxModel;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.LabeledComponent;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyManagePackagesDialog extends DialogWrapper
{
	private JPanel myMainPanel;

	public PyManagePackagesDialog(@Nonnull final Project project, @Nonnull Sdk sdk)
	{
		super(project, true);
		setTitle("Manage Python Packages");

		List<Sdk> sdks = PythonSdkType.getAllSdks();
		Collections.sort(sdks, new PreferredSdkComparator());
		final JComboBox sdkComboBox = new JComboBox(new CollectionComboBoxModel(sdks, sdk));
		sdkComboBox.setRenderer(new PySdkListCellRenderer(false));

		PackagesNotificationPanel notificationPanel = new PackagesNotificationPanel();
		final PyInstalledPackagesPanel packagesPanel = new PyInstalledPackagesPanel(project, notificationPanel);
		packagesPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		packagesPanel.updatePackages(PyPackageManagers.getInstance().getManagementService(project, sdk));
		packagesPanel.updateNotifications(sdk);

		myMainPanel = new JPanel(new BorderLayout());
		final LabeledComponent<JComboBox> sdkLabeledComponent = LabeledComponent.create(sdkComboBox, "Interpreter:");
		sdkLabeledComponent.setLabelLocation(BorderLayout.WEST);
		myMainPanel.add(sdkLabeledComponent, BorderLayout.NORTH);
		myMainPanel.add(packagesPanel, BorderLayout.CENTER);
		myMainPanel.add(notificationPanel.getComponent(), BorderLayout.SOUTH);

		sdkComboBox.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				Sdk sdk = (Sdk) sdkComboBox.getSelectedItem();
				packagesPanel.updatePackages(PyPackageManagers.getInstance().getManagementService(project, sdk));
				packagesPanel.updateNotifications(sdk);
			}
		});

		init();
		myMainPanel.setPreferredSize(new Dimension(JBUI.scale(900), JBUI.scale(700)));
		myMainPanel.setMinimumSize(new Dimension(JBUI.scale(900), JBUI.scale(700)));
	}

	@Override
	protected JComponent createCenterPanel()
	{
		return myMainPanel;
	}

	@Override
	protected String getDimensionServiceKey()
	{
		return "PyManagePackagesDialog";
	}

	@Nonnull
	@Override
	protected Action[] createActions()
	{
		return new Action[0];
	}
}
