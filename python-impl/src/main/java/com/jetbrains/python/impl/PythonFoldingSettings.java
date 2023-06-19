/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@State(
  name = "PythonFoldingSettings",
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/editor.codeinsight.xml"))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public class PythonFoldingSettings implements PersistentStateComponent<PythonFoldingSettings> {
  public boolean COLLAPSE_LONG_STRINGS;
  public boolean COLLAPSE_LONG_COLLECTIONS;
  public boolean COLLAPSE_SEQUENTIAL_COMMENTS;

  @Nullable
  @Override
  public PythonFoldingSettings getState() {
    return this;
  }

  @Nonnull
  public static PythonFoldingSettings getInstance() {
    return ServiceManager.getService(PythonFoldingSettings.class);
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

}
