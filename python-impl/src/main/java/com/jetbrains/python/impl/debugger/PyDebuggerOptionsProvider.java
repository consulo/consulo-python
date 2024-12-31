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
package com.jetbrains.python.impl.debugger;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;

/**
 * @author traff
 */
@State(
  name = "PyDebuggerOptionsProvider",
  storages = {
    @Storage(file = StoragePathMacros.WORKSPACE_FILE)
  })
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class PyDebuggerOptionsProvider implements PersistentStateComponent<PyDebuggerOptionsProvider.State> {
  private State myState = new State();

  @Nonnull
  private final Project myProject;

  @Inject
  public PyDebuggerOptionsProvider(@Nonnull Project project) {
    myProject = project;
  }

  public static PyDebuggerOptionsProvider getInstance(Project project) {
    return ServiceManager.getService(project, PyDebuggerOptionsProvider.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState.myAttachToSubprocess = state.myAttachToSubprocess;
    myState.mySaveCallSignatures = state.mySaveCallSignatures;
    myState.mySupportGeventDebugging = state.mySupportGeventDebugging;
    myState.mySupportQtDebugging = state.mySupportQtDebugging;
  }

  public static class State {
    public boolean myAttachToSubprocess = true;
    public boolean mySaveCallSignatures = false;
    public boolean mySupportGeventDebugging = false;
    public boolean mySupportQtDebugging = true;
  }


  public boolean isAttachToSubprocess() {
    return myState.myAttachToSubprocess;
  }

  public void setAttachToSubprocess(boolean attachToSubprocess) {
    myState.myAttachToSubprocess = attachToSubprocess;
  }

  public boolean isSaveCallSignatures() {
    return myState.mySaveCallSignatures;
  }

  public void setSaveCallSignatures(boolean saveCallSignatures) {
    myState.mySaveCallSignatures = saveCallSignatures;
  }

  public boolean isSupportGeventDebugging() {
    return myState.mySupportGeventDebugging;
  }

  public void setSupportGeventDebugging(boolean supportGeventDebugging) {
    myState.mySupportGeventDebugging = supportGeventDebugging;
  }

  public boolean isSupportQtDebugging() {
    return myState.mySupportQtDebugging;
  }

  public void setSupportQtDebugging(boolean supportQtDebugging) {
    myState.mySupportQtDebugging = supportQtDebugging;
  }
}

