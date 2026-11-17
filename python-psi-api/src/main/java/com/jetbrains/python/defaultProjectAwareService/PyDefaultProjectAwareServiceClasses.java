// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
/*
 * Copyright 2013-2026 consulo.io
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
package com.jetbrains.python.defaultProjectAwareService;

import consulo.application.Application;
import consulo.module.Module;
import org.jspecify.annotations.Nullable;

/**
 * Storage for service app and module level inheritors
 */
public final class PyDefaultProjectAwareServiceClasses<
  STATE,
  SERVICE extends PyDefaultProjectAwareService<STATE, SERVICE, APP_SERVICE, MODULE_SERVICE>,
  APP_SERVICE extends SERVICE,
  MODULE_SERVICE extends SERVICE,
  MODULE_SERVICE_FACTORY extends PyModuleServiceFactory<MODULE_SERVICE>> {

  private final Class<APP_SERVICE> myAppServiceClass;

  private final Class<MODULE_SERVICE_FACTORY> myServiceFactory;

  public PyDefaultProjectAwareServiceClasses(Class<APP_SERVICE> appServiceClass,
                                             Class<MODULE_SERVICE_FACTORY> serviceFactory) {
    myAppServiceClass = appServiceClass;
    myServiceFactory = serviceFactory;
  }
  /**
   * Use it for "getInstance" function. Returns module-level if module is set, app level otherwise
   */
  public SERVICE getService(@Nullable Module module) {
    // TODO consulo: Module#canStoreSettings missing - JB: if (module == null || !module.canStoreSettings())
    if (module == null) {
      return getAppService();
    }
    return getModuleService(module);
  }

  public APP_SERVICE getAppService() {
    return Application.get().getInstance(myAppServiceClass);
  }

  public MODULE_SERVICE getModuleService(Module module) {
    return module.getProject().getInstance(myServiceFactory).getService(module);
  }

  public void copyFromAppToModule(Module module) {
    getModuleService(module).loadState(getAppService().getState());
  }
}
