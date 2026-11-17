// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.defaultProjectAwareService.PyModuleServiceFactory;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.module.Module;
import jakarta.inject.Singleton;

/**
 * JB: {@code @Service(PROJECT) internal class ReSTServiceFactory : PyModuleServiceFactory<ReSTService.ModuleService>
 * by PyModuleServiceFactoryImpl(ReSTService::ModuleService)}.
 * <p>
 * Consulo: JB stores the module state through {@code CustomImlComponentService}, which Consulo lacks; here
 * {@link ReSTServiceModuleService} is a persisted module service, so it is taken from the module container
 * instead of being instantiated by {@code PyModuleServiceFactoryImpl}.
 */
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class ReSTServiceFactory implements PyModuleServiceFactory<ReSTServiceModuleService> {
    @Override
    public ReSTServiceModuleService getService(Module module) {
        return module.getInstance(ReSTServiceModuleService.class);
    }
}
