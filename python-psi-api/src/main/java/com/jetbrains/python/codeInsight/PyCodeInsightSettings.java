// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.inject.Singleton;


@State(name = "PyCodeInsightSettings", storages = @Storage("other.xml"))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public class PyCodeInsightSettings implements PersistentStateComponent<PyCodeInsightSettings> {
  public static PyCodeInsightSettings getInstance() {
    return Application.get().getInstance(PyCodeInsightSettings.class);
  }

  public boolean PREFER_FROM_IMPORT = true;
  public boolean SHOW_IMPORT_POPUP;
  public boolean HIGHLIGHT_UNUSED_IMPORTS = true;

  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION;
  public boolean RENAME_SEARCH_NON_CODE_FOR_FUNCTION;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_CLASS;
  public boolean RENAME_SEARCH_NON_CODE_FOR_CLASS;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
  public boolean RENAME_SEARCH_NON_CODE_FOR_VARIABLE;

  public boolean RENAME_CLASS_CONTAINING_FILE = true;
  public boolean RENAME_CLASS_INHERITORS = true;
  public boolean RENAME_PARAMETERS_IN_HIERARCHY = true;
  
  public boolean INSERT_SELF_FOR_METHODS = true;

  public boolean INSERT_TYPE_DOCSTUB;

  public boolean PARENTHESISE_ON_ENTER = true;
  public boolean INCLUDE_IMPORTABLE_NAMES_IN_BASIC_COMPLETION = true;
  public boolean COPY_TYPE_ANNOTATIONS_FROM_STUBS = true;

  @Override
  public PyCodeInsightSettings getState() {
    return this;
  }

  @Override
  public void loadState(PyCodeInsightSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
