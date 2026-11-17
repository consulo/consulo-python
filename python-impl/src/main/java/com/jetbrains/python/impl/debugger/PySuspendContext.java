// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.debugger.PyThreadInfo;

import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.execution.debug.frame.XExecutionStack;
import consulo.execution.debug.frame.XSuspendContext;
import com.jetbrains.python.debugger.pydev.AbstractCommand;

import consulo.ui.image.Image;

import java.util.Collection;


public class PySuspendContext extends XSuspendContext {

    private final PyExecutionStack myActiveStack;
    private final PyDebugProcess myDebugProcess;

    public PySuspendContext(PyDebugProcess debugProcess, PyThreadInfo threadInfo) {
        myDebugProcess = debugProcess;
        myActiveStack = new PyExecutionStack(debugProcess, threadInfo, getThreadIcon(threadInfo));
    }

    @Override
    public PyExecutionStack getActiveExecutionStack() {
        return myActiveStack;
    }

    public static Image getThreadIcon(PyThreadInfo threadInfo) {
        if ((threadInfo.getState() == PyThreadInfo.State.SUSPENDED) && (threadInfo.getStopReason() == AbstractCommand.SET_BREAKPOINT)) {
            return ExecutionDebugIconGroup.threadThreadatbreakpoint();
        }
        else {
            return ExecutionDebugIconGroup.threadThreadsuspended();
        }
    }

    @Override
    public XExecutionStack[] getExecutionStacks() {
        Collection<PyThreadInfo> threads = myDebugProcess.getThreads();
        if (threads.isEmpty()) {
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
