/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.jetbrains.python.console.pydev.ConsoleCommunication;
import consulo.execution.ui.console.language.ProcessBackedConsoleExecuteActionHandler;
import consulo.process.ProcessHandler;

/**
 * Created by kirylch on 4/12/2017.
 */
public abstract class PythonConsoleExecuteActionHandler extends ProcessBackedConsoleExecuteActionHandler {
    public PythonConsoleExecuteActionHandler(ProcessHandler processHandler, boolean preserveMarkup) {
        super(processHandler, preserveMarkup);
    }

    @Override
    public abstract void processLine(String line);

    public abstract boolean checkSingleLine(String text);

    public abstract String getCantExecuteMessage();

    public abstract boolean canExecuteNow();

    public abstract boolean isEnabled();

    public abstract void setEnabled(boolean enabled);

    public abstract ConsoleCommunication getConsoleCommunication();

    public abstract void updateConsoleState();
}
