// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package com.jetbrains.python.documentation;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.module.Module;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * JB: {@code PyDocumentationSettings.ModuleService} (nested class).
 * Consulo: top-level class, because the injecting annotation processor does not support nested {@code @ServiceImpl} classes.
 */
// Consulo: there is no CustomImlComponentService, so the module-level service is a module component persisted in the module file
@State(name = PyDocumentationSettings.MODULE_STATE_COMPONENT, storages = @Storage(file = StoragePathMacros.MODULE_FILE))
@ServiceAPI(ComponentScope.MODULE)
@ServiceImpl
@Singleton
public final class PyDocumentationSettingsModuleService extends PyDocumentationSettings {
  @Inject
  public PyDocumentationSettingsModuleService(Module module) {
    super(module);
  }
}
