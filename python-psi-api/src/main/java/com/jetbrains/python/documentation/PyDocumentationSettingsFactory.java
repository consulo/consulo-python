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

import com.jetbrains.python.defaultProjectAwareService.PyModuleServiceFactory;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.module.Module;
import jakarta.inject.Singleton;

/**
 * JB: {@code @Service(PROJECT) internal class PyDocumentationSettingsFactory : PyModuleServiceFactory<ModuleService>
 * by PyModuleServiceFactoryImpl(PyDocumentationSettings::ModuleService)}.
 * <p>
 * Consulo: JB stores the module state through {@code CustomImlComponentService}, which Consulo lacks; here
 * {@link PyDocumentationSettingsModuleService} is a persisted module service, so it is taken from the module container
 * instead of being instantiated by {@code PyModuleServiceFactoryImpl}.
 */
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class PyDocumentationSettingsFactory implements PyModuleServiceFactory<PyDocumentationSettingsModuleService> {
  @Override
  public PyDocumentationSettingsModuleService getService(Module module) {
    return module.getInstance(PyDocumentationSettingsModuleService.class);
  }
}
