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
package com.jetbrains.python.impl.packaging;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.*;
import consulo.ide.ServiceManager;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Lists;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

/**
 * User: catherine
 */
@State(name = "PyPackageService", storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/py_packages.xml", roamingType = RoamingType.DISABLED))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public class PyPackageService implements PersistentStateComponent<PyPackageService> {
  public Map<String, Boolean> sdkToUsersite = ContainerUtil.newConcurrentMap();
  public List<String> additionalRepositories = Lists.newLockFreeCopyOnWriteList();
  public String virtualEnvBasePath;
  public Boolean PYPI_REMOVED = false;

  public long LAST_TIME_CHECKED = 0;

  @Override
  public PyPackageService getState() {
    return this;
  }

  @Override
  public void loadState(PyPackageService state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public void addSdkToUserSite(String sdk, boolean useUsersite) {
    sdkToUsersite.put(sdk, useUsersite);
  }

  public void addRepository(String repository) {
    if (repository == null) {
      return;
    }
    if (PyPIPackageUtil.isPyPIRepository(repository)) {
      PYPI_REMOVED = false;
    }
    else {
      if (!repository.endsWith("/")) {
        repository += "/";
      }
      additionalRepositories.add(repository);
    }
  }

  public void removeRepository(String repository) {
    if (additionalRepositories.contains(repository)) {
      additionalRepositories.remove(repository);
    }
    else if (PyPIPackageUtil.isPyPIRepository(repository)) {
      PYPI_REMOVED = true;
    }
  }

  public boolean useUserSite(String sdk) {
    if (sdkToUsersite.containsKey(sdk)) {
      return sdkToUsersite.get(sdk);
    }
    return false;
  }

  public static PyPackageService getInstance() {
    return ServiceManager.getService(PyPackageService.class);
  }

  public String getVirtualEnvBasePath() {
    return virtualEnvBasePath;
  }

  public void setVirtualEnvBasePath(String virtualEnvBasePath) {
    this.virtualEnvBasePath = virtualEnvBasePath;
  }
}
