/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.console;

import consulo.application.dumb.DumbAware;
import consulo.project.Project;
import consulo.ui.ex.toolWindow.ToolWindow;

import jakarta.annotation.Nonnull;

/**
 * @author traff
 */
public class PythonConsoleToolWindowFactory implements DumbAware {
  public static final String ID = "Python Console";

  //@Override
  public void createToolWindowContent(final @Nonnull Project project, final @Nonnull ToolWindow toolWindow) {
    PydevConsoleRunner runner = PythonConsoleRunnerFactory.getInstance().createConsoleRunner(project, null);
    runner.runSync();
  }
}
