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

import com.jetbrains.python.impl.run.PythonCommandLineState;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.content.bundle.Sdk;
import consulo.execution.debug.XDebugSession;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.process.ExecutionException;
import org.jetbrains.annotations.Contract;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Alexander Koshevoy
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class PyDebugSessionFactory {
  public static final ExtensionPointName<PyDebugSessionFactory> EP_NAME = ExtensionPointName.create(PyDebugSessionFactory.class);

  protected abstract boolean appliesTo(@Nonnull Sdk sdk);

  @Nonnull
  public abstract XDebugSession createSession(@Nonnull PyDebugRunner runner,
                                              @Nonnull PythonCommandLineState state,
                                              @Nonnull ExecutionEnvironment environment) throws ExecutionException;

  @Contract("null -> null")
  @Nullable
  public static PyDebugSessionFactory findExtension(@Nullable Sdk sdk) {
    if (sdk == null) {
      return null;
    }
    for (PyDebugSessionFactory sessionCreator : EP_NAME.getExtensions()) {
      if (sessionCreator.appliesTo(sdk)) {
        return sessionCreator;
      }
    }
    return null;
  }
}
