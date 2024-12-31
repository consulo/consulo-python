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

package com.jetbrains.python.impl.packaging;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.module.Module;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;

/**
 * @author vlan
 */
@State(name = "PackageRequirementsSettings",
  storages = {@Storage(file = "$MODULE_FILE$")})
@ServiceAPI(ComponentScope.MODULE)
@ServiceImpl
@Singleton
public class PyPackageRequirementsSettings implements PersistentStateComponent<PyPackageRequirementsSettings> {
  public static final String DEFAULT_REQUIREMENTS_PATH = "requirements.txt";

  @Nonnull
  private String myRequirementsPath = DEFAULT_REQUIREMENTS_PATH;

  @Nonnull
  @Override
  public PyPackageRequirementsSettings getState() {
    return this;
  }

  @Override
  public void loadState(@Nonnull PyPackageRequirementsSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Nonnull
  public String getRequirementsPath() {
    return myRequirementsPath;
  }

  public void setRequirementsPath(@Nonnull String path) {
    myRequirementsPath = path;
  }

  @Nonnull
  public static PyPackageRequirementsSettings getInstance(@Nonnull Module module) {
    return module.getInstance(PyPackageRequirementsSettings.class);
  }
}
