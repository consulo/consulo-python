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
package com.jetbrains.python.impl.configuration;

import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.SearchableConfigurable;
import consulo.configurable.UnnamedConfigurable;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.ui.awt.ModuleListCellRenderer;
import consulo.project.Project;
import consulo.ui.ex.awt.CollectionListModel;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.ui.ex.awt.Splitter;
import consulo.ui.ex.awt.speedSearch.ListSpeedSearch;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Condition;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
@Deprecated
public abstract class ModuleAwareProjectConfigurable<T extends UnnamedConfigurable> implements SearchableConfigurable, Configurable.NoScroll {
  @Nonnull
  private final Project myProject;
  private final String myDisplayName;
  private final String myHelpTopic;
  private final Map<Module, T> myModuleConfigurables = new HashMap<Module, T>();

  public ModuleAwareProjectConfigurable(@Nonnull Project project, String displayName, String helpTopic) {
    myProject = project;
    myDisplayName = displayName;
    myHelpTopic = helpTopic;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public String getHelpTopic() {
    return myHelpTopic;
  }

  protected boolean isSuitableForModule(@Nonnull Module module) {
    return true;
  }

  @Override
  public JComponent createComponent() {
    if (myProject.isDefault()) {
      T configurable = createDefaultProjectConfigurable();
      if (configurable != null) {
        myModuleConfigurables.put(null, configurable);
        return configurable.createComponent();
      }
    }
    final List<Module> modules = ContainerUtil.filter(ModuleManager.getInstance(myProject).getSortedModules(), new Condition<Module>() {
      @Override
      public boolean value(Module module) {
        return isSuitableForModule(module);
      }
    });
    if (modules.size() == 1) {
      Module module = modules.get(0);
      final T configurable = createModuleConfigurable(module);
      myModuleConfigurables.put(module, configurable);
      return configurable.createComponent();
    }
    final Splitter splitter = new Splitter(false, 0.25f);
    final JBList moduleList = new JBList(new CollectionListModel<Module>(modules));
    new ListSpeedSearch(moduleList, o -> {
      if (o instanceof Module) {
        return ((Module)o).getName();
      }
      return null;
    });
    moduleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    moduleList.setCellRenderer(new ModuleListCellRenderer());
    splitter.setFirstComponent(new JBScrollPane(moduleList));
    final CardLayout layout = new CardLayout();
    final JPanel cardPanel = new JPanel(layout);
    splitter.setSecondComponent(cardPanel);
    for (Module module : modules) {
      final T configurable = createModuleConfigurable(module);
      myModuleConfigurables.put(module, configurable);
      final JComponent component = new JBScrollPane(configurable.createComponent());
      component.setBorder(new EmptyBorder(0, 0, 0, 0));
      cardPanel.add(component, module.getName());
    }
    moduleList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        final Module value = (Module)moduleList.getSelectedValue();
        layout.show(cardPanel, value.getName());
      }
    });
    if (modules.size() > 0) {
      moduleList.setSelectedIndex(0);
      layout.show(cardPanel, modules.get(0).getName());
    }
    return splitter;
  }

  @Nullable
  protected T createDefaultProjectConfigurable() {
    return null;
  }

  @Nonnull
  protected abstract T createModuleConfigurable(Module module);

  @Override
  public boolean isModified() {
    for (T configurable : myModuleConfigurables.values()) {
      if (configurable.isModified()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    for (T configurable : myModuleConfigurables.values()) {
      configurable.apply();
    }
  }

  @Override
  public void reset() {
    for (T configurable : myModuleConfigurables.values()) {
      configurable.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    for (T configurable : myModuleConfigurables.values()) {
      configurable.disposeUIResources();
    }
    myModuleConfigurables.clear();
  }

  @Nonnull
  @Override
  public String getId() {
    return getClass().getName();
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nonnull
  protected final Project getProject() {
    return myProject;
  }
}
