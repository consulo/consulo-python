/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.debugger.settings;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.configurable.IdeaSimpleConfigurable;
import consulo.execution.debug.setting.DebuggerSettingsCategory;
import consulo.execution.debug.setting.XDebuggerSettings;
import consulo.project.Project;
import consulo.util.collection.SmartList;
import consulo.util.xml.serializer.XmlSerializerUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Collections.singletonList;


@ExtensionImpl
public class PyDebuggerSettings extends XDebuggerSettings<PyDebuggerSettings> implements Supplier<PyDebuggerSettings> {
  private boolean myLibrariesFilterEnabled;
  private boolean mySteppingFiltersEnabled;
  private
  @Nonnull
  List<PySteppingFilter> mySteppingFilters;
  public static final String FILTERS_DIVIDER = ";";
  private boolean myWatchReturnValues = false;
  private boolean mySimplifiedView = true;

  public PyDebuggerSettings() {
    super("python");
    mySteppingFilters = new SmartList<>();
  }

  public boolean isWatchReturnValues() {
    return myWatchReturnValues;
  }

  public void setWatchReturnValues(boolean watchReturnValues) {
    myWatchReturnValues = watchReturnValues;
  }

  public boolean isSimplifiedView() {
    return mySimplifiedView;
  }

  public void setSimplifiedView(boolean simplifiedView) {
    mySimplifiedView = simplifiedView;
  }

  public static PyDebuggerSettings getInstance() {
    return getInstance(PyDebuggerSettings.class);
  }

  public boolean isLibrariesFilterEnabled() {
    return myLibrariesFilterEnabled;
  }

  public void setLibrariesFilterEnabled(boolean librariesFilterEnabled) {
    myLibrariesFilterEnabled = librariesFilterEnabled;
  }

  public boolean isSteppingFiltersEnabled() {
    return mySteppingFiltersEnabled;
  }

  public void setSteppingFiltersEnabled(boolean steppingFiltersEnabled) {
    mySteppingFiltersEnabled = steppingFiltersEnabled;
  }

  @Nonnull
  public List<PySteppingFilter> getSteppingFilters() {
    return mySteppingFilters;
  }

  @Nonnull
  public String getSteppingFiltersForProject(@Nonnull Project project) {
    StringBuilder sb = new StringBuilder();
    for (PySteppingFilter filter : mySteppingFilters) {
      if (filter.isEnabled()) {
        sb.append(filter.getAbsolutePlatformIndependentFilter(project)).append(FILTERS_DIVIDER);
      }
    }
    return sb.toString();
  }

  public void setSteppingFilters(@Nonnull List<PySteppingFilter> steppingFilters) {
    mySteppingFilters = steppingFilters;
  }

  @Nullable
  @Override
  public PyDebuggerSettings getState() {
    return this;
  }

  @Override
  public void loadState(PyDebuggerSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Nonnull
  @Override
  public Collection<? extends Configurable> createConfigurables(@Nonnull DebuggerSettingsCategory category) {
    switch (category) {
      case STEPPING:
        return singletonList(IdeaSimpleConfigurable.create("python.debug.configurable",
                                                           "Python",
                                                           PyDebuggerSteppingConfigurableUi.class,
                                                           this));
      default:
        return Collections.emptyList();
    }
  }

  @Override
  public PyDebuggerSettings get() {
    return this;
  }
}
