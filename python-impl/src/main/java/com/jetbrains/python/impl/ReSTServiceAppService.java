// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.RoamingType;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import jakarta.inject.Singleton;

/**
 * JB: {@code ReSTService.AppService} (nested class).
 * Consulo: top-level class, because the injecting annotation processor does not support nested {@code @ServiceImpl} classes.
 */
@State(name = "AppReSTService", storages = @Storage(value = "ReSTService.xml", roamingType = RoamingType.DISABLED))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public final class ReSTServiceAppService extends ReSTService {
}
