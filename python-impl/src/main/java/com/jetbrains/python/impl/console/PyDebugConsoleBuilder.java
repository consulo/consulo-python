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

package com.jetbrains.python.impl.console;

import com.jetbrains.python.impl.run.PythonTracebackFilter;
import consulo.content.bundle.Sdk;
import consulo.execution.ui.console.ConsoleState;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.console.Filter;
import consulo.execution.ui.console.TextConsoleBuilder;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;

/**
 * @author traff
 */
public class PyDebugConsoleBuilder extends TextConsoleBuilder {
  private final Project myProject;
  private final ArrayList<Filter> myFilters = new ArrayList<>();
  private final Sdk mySdk;

  public PyDebugConsoleBuilder(Project project, @Nullable Sdk sdk) {
    myProject = project;
    this.mySdk = sdk;
  }

  public ConsoleView getConsole() {
    ConsoleView consoleView = createConsole();
    for (Filter filter : myFilters) {
      consoleView.addMessageFilter(filter);
    }
    return consoleView;
  }

  protected ConsoleView createConsole() {
    PythonDebugLanguageConsoleView consoleView = new PythonDebugLanguageConsoleView(myProject, mySdk);
    consoleView.addMessageFilter(new PythonTracebackFilter(myProject));
    return consoleView;
  }

  public void addFilter(Filter filter) {
    myFilters.add(filter);
  }

  @Override
  public void setViewer(boolean isViewer) {
  }

  @Override
  public void setState(@Nonnull ConsoleState consoleState) {

  }

  @Override
  public void setUsePredefinedMessageFilter(boolean b) {

  }

}
