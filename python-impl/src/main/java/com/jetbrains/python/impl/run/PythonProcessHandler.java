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

import consulo.platform.Platform;
import consulo.process.ExecutionException;
import consulo.process.ProcessConsoleType;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.cmd.GeneralCommandLine;

/**
 * @author traff
 */
public class PythonProcessHandler {
  public static ProcessHandler createProcessHandler(GeneralCommandLine commandLine) throws ExecutionException {
    return ProcessHandlerBuilder.create(commandLine)
                                .killable()
                                .colored()
                                .shouldDestroyProcessRecursively(true)
                                .build();
  }

  public static ProcessHandler createDefaultProcessHandler(GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException {
    if (withMediator && Platform.current().os().isWindows()) {
      return ProcessHandlerBuilder.create(commandLine)
                                  .killable()
                                  .colored()
                                  .consoleType(ProcessConsoleType.EXTERNAL_EMULATION)
                                  .shouldDestroyProcessRecursively(true)
                                  .build();
    }
    else {
      return createProcessHandler(commandLine);
    }
  }
}
