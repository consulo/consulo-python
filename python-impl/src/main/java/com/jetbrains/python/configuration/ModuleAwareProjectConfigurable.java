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
package com.jetbrains.python.configuration;

import java.awt.CardLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.jetbrains.annotations.Nls;

import javax.annotation.Nullable;
import com.intellij.application.options.ModuleListCellRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

/**
 * @author yole
 */
@Deprecated
public abstract class ModuleAwareProjectConfigurable<T extends UnnamedConfigurable> implements SearchableConfigurable, Configurable.NoScroll
{
	@Nonnull
	private final Project myProject;
	private final String myDisplayName;
	private final String myHelpTopic;
	private final Map<Module, T> myModuleConfigurables = new HashMap<Module, T>();

	public ModuleAwareProjectConfigurable(@Nonnull Project project, String displayName, String helpTopic)
	{
		myProject = project;
		myDisplayName = displayName;
		myHelpTopic = helpTopic;
	}

	@Nls
	@Override
	public String getDisplayName()
	{
		return myDisplayName;
	}

	@Override
	public String getHelpTopic()
	{
		return myHelpTopic;
	}

	protected boolean isSuitableForModule(@Nonnull Module module)
	{
		return true;
	}

	@Override
	public JComponent createComponent()
	{
		if(myProject.isDefault())
		{
			T configurable = createDefaultProjectConfigurable();
			if(configurable != null)
			{
				myModuleConfigurables.put(null, configurable);
				return configurable.createComponent();
			}
		}
		final List<Module> modules = ContainerUtil.filter(ModuleManager.getInstance(myProject).getSortedModules(), new Condition<Module>()
		{
			@Override
			public boolean value(Module module)
			{
				return isSuitableForModule(module);
			}
		});
		if(modules.size() == 1)
		{
			Module module = modules.get(0);
			final T configurable = createModuleConfigurable(module);
			myModuleConfigurables.put(module, configurable);
			return configurable.createComponent();
		}
		final Splitter splitter = new Splitter(false, 0.25f);
		final JBList moduleList = new JBList(new CollectionListModel<Module>(modules));
		new ListSpeedSearch(moduleList, new Function<Object, String>()
		{
			@Override
			public String fun(Object o)
			{
				if(o instanceof Module)
				{
					return ((Module) o).getName();
				}
				return null;
			}
		});
		moduleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		moduleList.setCellRenderer(new ModuleListCellRenderer());
		splitter.setFirstComponent(new JBScrollPane(moduleList));
		final CardLayout layout = new CardLayout();
		final JPanel cardPanel = new JPanel(layout);
		splitter.setSecondComponent(cardPanel);
		for(Module module : modules)
		{
			final T configurable = createModuleConfigurable(module);
			myModuleConfigurables.put(module, configurable);
			final JComponent component = new JBScrollPane(configurable.createComponent());
			component.setBorder(new EmptyBorder(0, 0, 0, 0));
			cardPanel.add(component, module.getName());
		}
		moduleList.addListSelectionListener(new ListSelectionListener()
		{
			@Override
			public void valueChanged(ListSelectionEvent e)
			{
				final Module value = (Module) moduleList.getSelectedValue();
				layout.show(cardPanel, value.getName());
			}
		});
		if(modules.size() > 0)
		{
			moduleList.setSelectedIndex(0);
			layout.show(cardPanel, modules.get(0).getName());
		}
		return splitter;
	}

	@Nullable
	protected T createDefaultProjectConfigurable()
	{
		return null;
	}

	@Nonnull
	protected abstract T createModuleConfigurable(Module module);

	@Override
	public boolean isModified()
	{
		for(T configurable : myModuleConfigurables.values())
		{
			if(configurable.isModified())
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public void apply() throws ConfigurationException
	{
		for(T configurable : myModuleConfigurables.values())
		{
			configurable.apply();
		}
	}

	@Override
	public void reset()
	{
		for(T configurable : myModuleConfigurables.values())
		{
			configurable.reset();
		}
	}

	@Override
	public void disposeUIResources()
	{
		for(T configurable : myModuleConfigurables.values())
		{
			configurable.disposeUIResources();
		}
		myModuleConfigurables.clear();
	}

	@Nonnull
	@Override
	public String getId()
	{
		return getClass().getName();
	}

	@Override
	public Runnable enableSearch(String option)
	{
		return null;
	}

	@Nonnull
	protected final Project getProject()
	{
		return myProject;
	}
}