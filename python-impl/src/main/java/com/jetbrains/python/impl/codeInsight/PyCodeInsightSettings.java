/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.codeInsight;

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

/**
 * @author yole
 */
@State(
  name="PyCodeInsightSettings",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/other.xml"
    )}
)
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public class PyCodeInsightSettings implements PersistentStateComponent<PyCodeInsightSettings> {
  public static PyCodeInsightSettings getInstance() {
    return ServiceManager.getService(PyCodeInsightSettings.class);
  }

  public boolean PREFER_FROM_IMPORT = true;
  public boolean SHOW_IMPORT_POPUP = false;
  public boolean HIGHLIGHT_UNUSED_IMPORTS = true;

  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION = false;
  public boolean RENAME_SEARCH_NON_CODE_FOR_FUNCTION = false;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = false;
  public boolean RENAME_SEARCH_NON_CODE_FOR_CLASS = false;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = false;
  public boolean RENAME_SEARCH_NON_CODE_FOR_VARIABLE = false;

  public boolean DJANGO_AUTOINSERT_TAG_CLOSE = true;

  public boolean RENAME_CLASS_CONTAINING_FILE = true;
  public boolean RENAME_CLASS_INHERITORS = true;
  public boolean RENAME_PARAMETERS_IN_HIERARCHY = true;

  public boolean INSERT_BACKSLASH_ON_WRAP = true;
  public boolean INSERT_SELF_FOR_METHODS = true;

  public boolean INSERT_TYPE_DOCSTUB = false;

  public PyCodeInsightSettings getState() {
    return this;
  }

  public void loadState(PyCodeInsightSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
