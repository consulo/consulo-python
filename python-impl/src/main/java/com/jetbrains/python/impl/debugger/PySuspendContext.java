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

import com.jetbrains.python.debugger.PyThreadInfo;
import com.jetbrains.python.debugger.pydev.AbstractCommand;
import consulo.execution.debug.frame.XExecutionStack;
import consulo.execution.debug.frame.XSuspendContext;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;
import java.util.Collection;


public class PySuspendContext extends XSuspendContext {

    private final PyExecutionStack myActiveStack;
    private PyDebugProcess myDebugProcess;

    public PySuspendContext(@Nonnull final PyDebugProcess debugProcess, @Nonnull final PyThreadInfo threadInfo) {
        myDebugProcess = debugProcess;
        myActiveStack = new PyExecutionStack(debugProcess, threadInfo, getThreadIcon(threadInfo));
    }

    @Override
    @Nonnull
    public PyExecutionStack getActiveExecutionStack() {
        return myActiveStack;
    }

    @Nonnull
    public static Image getThreadIcon(@Nonnull PyThreadInfo threadInfo) {
        if ((threadInfo.getState() == PyThreadInfo.State.SUSPENDED) && (threadInfo.getStopReason() == AbstractCommand.SET_BREAKPOINT)) {
            return ExecutionDebugIconGroup.threadThreadatbreakpoint();
        }
        else {
            return ExecutionDebugIconGroup.threadThreadsuspended();
        }
    }

    @Nonnull
    @Override
    public XExecutionStack[] getExecutionStacks() {
        final Collection<PyThreadInfo> threads = myDebugProcess.getThreads();
        if (threads.size() < 1) {
            return XExecutionStack.EMPTY_ARRAY;
        }
        else {
            XExecutionStack[] stacks = new XExecutionStack[threads.size()];
            int i = 0;
            for (PyThreadInfo thread : threads) {
                stacks[i++] = new PyExecutionStack(myDebugProcess, thread, getThreadIcon(thread));
            }
            return stacks;
        }
    }

}
