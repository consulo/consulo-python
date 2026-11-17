// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

@State(name = "PythonFoldingSettings", storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/editor.codeinsight.xml"))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public class PythonFoldingSettings implements PersistentStateComponent<PythonFoldingSettings> {
    public boolean COLLAPSE_LONG_STRINGS;
    public boolean COLLAPSE_LONG_COLLECTIONS;
    public boolean COLLAPSE_SEQUENTIAL_COMMENTS;
    public boolean COLLAPSE_TYPE_ANNOTATIONS;

    @Override
    public @Nullable PythonFoldingSettings getState() {
        return this;
    }

    public static PythonFoldingSettings getInstance() {
        return Application.get().getInstance(PythonFoldingSettings.class);
    }

    @Override
    public void loadState(PythonFoldingSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public boolean isCollapseLongStrings() {
        return COLLAPSE_LONG_STRINGS;
    }

    public boolean isCollapseLongCollections() {
        return COLLAPSE_LONG_COLLECTIONS;
    }

    public boolean isCollapseSequentialComments() {
        return COLLAPSE_SEQUENTIAL_COMMENTS;
    }

    public boolean isCollapseTypeAnnotations() {
        return COLLAPSE_TYPE_ANNOTATIONS;
    }
}
