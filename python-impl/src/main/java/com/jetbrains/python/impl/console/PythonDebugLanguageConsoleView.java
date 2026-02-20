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

import consulo.application.ui.wm.IdeFocusManager;
import consulo.codeEditor.Editor;
import consulo.content.bundle.Sdk;
import consulo.execution.icon.ExecutionIconGroup;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.console.DuplexConsoleView;
import consulo.execution.ui.console.TextConsoleBuilderFactory;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author traff
 */
public class PythonDebugLanguageConsoleView extends DuplexConsoleView<ConsoleView, PythonConsoleView> implements PyCodeExecutor {

  public static final String DEBUG_CONSOLE_START_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))";

  public PythonDebugLanguageConsoleView(Project project, Sdk sdk, ConsoleView consoleView) {
    super(consoleView, new PythonConsoleView(project, "Python Console", sdk));

    enableConsole(!PyConsoleOptions.getInstance(project).isShowDebugConsoleByDefault());

    getSwitchConsoleActionPresentation().setIcon(ExecutionIconGroup.console());
    getSwitchConsoleActionPresentation().setTextValue(PyLocalize.runConfigurationShowCommandLineActionName());
  }

  public PythonDebugLanguageConsoleView(Project project, Sdk sdk) {
    this(project, sdk, TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole());
  }

  @Override
  public void executeCode(@Nonnull String code, @Nullable Editor e) {
    enableConsole(false);
    getPydevConsoleView().executeCode(code, e);
  }

  @Nonnull
  public PythonConsoleView getPydevConsoleView() {
    return getSecondaryConsoleView();
  }

  public ConsoleView getTextConsole() {
    return getPrimaryConsoleView();
  }

  @Override
  public void enableConsole(boolean primary) {
    super.enableConsole(primary);

    if (!primary && !isPrimaryConsoleEnabled()) {
      PythonConsoleView console = getPydevConsoleView();
      console.addConsoleFolding(true);
      console.showStartMessageForFirstExecution(DEBUG_CONSOLE_START_COMMAND);

      IdeFocusManager.findInstance().requestFocus(console.getConsoleEditor().getContentComponent(), true);
    }
  }
}
