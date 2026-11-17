// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

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
 * JB: {@code ReSTService.ModuleService} (nested class).
 * Consulo: top-level class, because the injecting annotation processor does not support nested {@code @ServiceImpl} classes.
 */
// Consulo: there is no CustomImlComponentService, so the module-level service is a module component persisted in the module file
@State(name = ReSTService.MODULE_STATE_COMPONENT, storages = @Storage(file = StoragePathMacros.MODULE_FILE))
@ServiceAPI(ComponentScope.MODULE)
@ServiceImpl
@Singleton
public final class ReSTServiceModuleService extends ReSTService {
    @Inject
    public ReSTServiceModuleService(Module module) {
        super(module);
    }
}
