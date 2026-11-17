// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.editor;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.RoamingType;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.inject.Singleton;

@State(name = "RestSettings", storages = @Storage(value = "other.xml", roamingType = RoamingType.DISABLED))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public class RestSettings implements PersistentStateComponent<RestSettings> {
  // TODO consulo: JBCefApp missing, JCEF is never supported
  private String myCurrentPanel = RestConfigurable.SWING;

  public String getCurrentPanel() {
    return myCurrentPanel;
  }

  public void setCurrentPanel(String currentPanel) {
    myCurrentPanel = currentPanel;
  }

  public static RestSettings getInstance() {
    return Application.get().getInstance(RestSettings.class);
  }


  @Override
  public RestSettings getState() {
    return this;
  }

  @Override
  public void loadState(RestSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
