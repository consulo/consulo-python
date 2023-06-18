/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.codeInsight.imports;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.jetbrains.annotations.Nls;
import javax.annotation.Nullable;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightSettings;

/**
 * @author yole
 */
public class PyAutoImportConfigurable implements Configurable
{
	private JPanel myMainPanel;
	private JRadioButton myRbFromImport;
	private JRadioButton myRbImport;
	private JCheckBox myShowImportPopupCheckBox;

	@Override
	public JComponent createComponent()
	{
		return myMainPanel;
	}

	@Override
	public void reset()
	{
		final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
		myRbFromImport.setSelected(settings.PREFER_FROM_IMPORT);
		myRbImport.setSelected(!settings.PREFER_FROM_IMPORT);
		myShowImportPopupCheckBox.setSelected(settings.SHOW_IMPORT_POPUP);
	}

	@Override
	public boolean isModified()
	{
		final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
		return settings.PREFER_FROM_IMPORT != myRbFromImport.isSelected() || settings.SHOW_IMPORT_POPUP != myShowImportPopupCheckBox.isSelected();
	}

	@Override
	public void apply() throws ConfigurationException
	{
		final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
		settings.PREFER_FROM_IMPORT = myRbFromImport.isSelected();
		settings.SHOW_IMPORT_POPUP = myShowImportPopupCheckBox.isSelected();
	}

	@Override
	public void disposeUIResources()
	{
	}

	@Nls
	@Override
	public String getDisplayName()
	{
		return null;
	}

	@Nullable
	@Override
	public String getHelpTopic()
	{
		return null;
	}
}
