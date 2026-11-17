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

import consulo.component.persist.PersistentStateComponent;
import consulo.module.Module;
import consulo.util.xml.serializer.XmlSerializerUtil;
import org.jspecify.annotations.Nullable;

/**
 * @param <STATE> class with service state
 * @param <SERVICE> service itself
 * @param <APP_SERVICE> service inheritor that uses {@link consulo.component.persist.State} annotation to store state on app level
 * @param <MODULE_SERVICE> same for module level
 */
public abstract class PyDefaultProjectAwareService<
  STATE,
  SERVICE extends PyDefaultProjectAwareService<STATE, SERVICE, APP_SERVICE, MODULE_SERVICE>,
  APP_SERVICE extends SERVICE,
  MODULE_SERVICE extends SERVICE> implements PersistentStateComponent<STATE> {

  private final STATE myCurrentState;

  protected final @Nullable String myModuleComponentName;
  protected final @Nullable Module myModule;
  protected final @Nullable Class<STATE> myStateClass;

  protected PyDefaultProjectAwareService(STATE defaultState) {
    myCurrentState = defaultState;
    myModuleComponentName = null;
    myModule = null;
    myStateClass = null;
  }

  protected PyDefaultProjectAwareService(STATE defaultState, String componentName, Class<STATE> stateClass, Module module) {
    myCurrentState = defaultState;
    myModuleComponentName = componentName;
    myModule = module;
    myStateClass = stateClass;
  }

  @Override
  public STATE getState() {
    if (myModule == null) {
      return myCurrentState;
    }
    // TODO consulo: CustomImlComponentService missing - JB reads the module state via
    // CustomImlComponentService.getInstance(myModule.getProject()).getComponentValue(myModule, myModuleComponentName, myStateClass);
    // in Consulo the module-level service is persisted itself (module file storage)
    return myCurrentState;
  }

  @Override
  public void loadState(STATE state) {
    if (myModule == null) {
      XmlSerializerUtil.copyBean(state, myCurrentState);
      return;
    }

    // TODO consulo: CustomImlComponentService missing - JB writes the module state via
    // WriteAction.run(() -> CustomImlComponentService.getInstance(myModule.getProject()).setComponentValueBlocking(myModule, myModuleComponentName, state));
    XmlSerializerUtil.copyBean(state, myCurrentState);
  }
}
