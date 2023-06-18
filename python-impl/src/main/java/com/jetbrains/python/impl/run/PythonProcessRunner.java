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

package com.jetbrains.python.impl.run;

import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;

/**
 * @author traff
 */
public class PythonProcessRunner {
  private PythonProcessRunner() {
  }

  public static ProcessHandler createProcess(GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException {
    PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(commandLine.getExePath());
    if (flavor == null) {
      return PythonProcessHandler.createDefaultProcessHandler(commandLine, withMediator);
    }
    else {
      return flavor.createProcessHandler(commandLine, withMediator);
    }
  }

  public static ProcessHandler createProcess(GeneralCommandLine commandLine) throws ExecutionException {
    return createProcess(commandLine, false);
  }

  public static ProcessHandler createProcessHandlingCtrlC(GeneralCommandLine commandLine) throws ExecutionException {
    return createProcess(commandLine, true);
  }
}
