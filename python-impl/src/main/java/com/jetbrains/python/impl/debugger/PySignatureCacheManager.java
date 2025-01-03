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

package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author traff
 */
@ServiceAPI(ComponentScope.PROJECT)
public abstract class PySignatureCacheManager {
  public static PySignatureCacheManager getInstance(Project project) {
    return ServiceManager.getService(project, PySignatureCacheManager.class);
  }

  public abstract void recordSignature(@Nonnull PySignature signature);

  @Nullable
  public abstract String findParameterType(@Nonnull PyFunction function, @Nonnull String name);

  @Nullable
  public abstract PySignature findSignature(@Nonnull PyFunction function);

  public abstract void clearCache();
}
