/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.impl.testing;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.execution.test.sm.runner.SMTestLocator;

import jakarta.annotation.Nonnull;

/**
 * Test locators are injected with their protocol id to support new test locators
 *
 * @author Ilya.Kazakevich
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PythonTestLocator extends SMTestLocator {
  @Nonnull
  ExtensionPointName<PythonTestLocator> EP_NAME = ExtensionPointName.create(PythonTestLocator.class);

  @Nonnull
  String getProtocolId();
}
