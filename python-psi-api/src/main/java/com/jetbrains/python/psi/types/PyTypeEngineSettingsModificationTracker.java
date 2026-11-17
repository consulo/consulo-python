// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.util.SimpleModificationTracker;
import consulo.project.Project;
import jakarta.inject.Singleton;

/**
 * Modification tracker that should be incremented when the engine that a new type context picks can change.
 * That happens when the user switches between type engines, and when the server of the selected external
 * engine starts or stops. Caches that depend on the engine (like TypeEvalContextCache) are then invalidated.
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class PyTypeEngineSettingsModificationTracker extends SimpleModificationTracker {
    public static PyTypeEngineSettingsModificationTracker getInstance(Project project) {
        return project.getInstance(PyTypeEngineSettingsModificationTracker.class);
    }
}
