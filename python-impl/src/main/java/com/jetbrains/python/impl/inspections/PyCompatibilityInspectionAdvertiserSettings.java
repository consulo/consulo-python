// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.project.Project;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
@State(name = "PythonCompatibilityInspectionAdvertiser", storages = @Storage(file = StoragePathMacros.PROJECT_FILE))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class PyCompatibilityInspectionAdvertiserSettings implements PersistentStateComponent<PyCompatibilityInspectionAdvertiserSettings> {
    public int version = 0;

    public static PyCompatibilityInspectionAdvertiserSettings getInstance(Project project) {
        return project.getInstance(PyCompatibilityInspectionAdvertiserSettings.class);
    }

    @Override
    public @Nullable PyCompatibilityInspectionAdvertiserSettings getState() {
        return this;
    }

    @Override
    public void loadState(PyCompatibilityInspectionAdvertiserSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
