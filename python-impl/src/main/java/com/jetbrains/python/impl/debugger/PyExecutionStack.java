// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.debugger.PyStackFrameInfo;
import com.jetbrains.python.debugger.PyThreadInfo;
import consulo.application.Application;
import consulo.execution.debug.frame.HasThreadSuspend;
import consulo.execution.debug.frame.XExecutionStack;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


public class PyExecutionStack extends XExecutionStack implements HasThreadSuspend {

    private final PyDebugProcess myDebugProcess;
    private final PyThreadInfo myThreadInfo;
    private PyStackFrame myTopFrame;

    public PyExecutionStack(PyDebugProcess debugProcess, PyThreadInfo threadInfo) {
        super(threadInfo.getName()); //NON-NLS
        myDebugProcess = debugProcess;
        myThreadInfo = threadInfo;
    }

    public PyExecutionStack(PyDebugProcess debugProcess, PyThreadInfo threadInfo, @Nullable Image icon) {
        super(threadInfo.getName(), icon); //NON-NLS
        myDebugProcess = debugProcess;
        myThreadInfo = threadInfo;
    }

    @Override
    public boolean isSuspended() {
        return myThreadInfo.getState() == PyThreadInfo.State.SUSPENDED;
    }

    @Override
    public void suspendThread() {
        myDebugProcess.suspendThread(myThreadInfo);
    }

    @Override
    public void resumeThread() {
        myDebugProcess.resumeThread(myThreadInfo);
    }

    @Override
    public PyStackFrame getTopFrame() {
        if (myTopFrame == null) {
            List<PyStackFrameInfo> frames = myThreadInfo.getFrames();
            if (frames != null) {
                myTopFrame = convert(myDebugProcess, frames.get(0));
            }
        }
        return myTopFrame;
    }

    @Override
    public void computeStackFrames(XStackFrameContainer container) {
        // TODO consulo: XExecutionStack.computeStackFrames(int firstFrameIndex, XStackFrameContainer) missing, frames are always computed from the top
        int firstFrameIndex = 0;
        if (myThreadInfo.getState() != PyThreadInfo.State.SUSPENDED) {
            container.errorOccurred(PyLocalize.debuggerStackFramesNotAvailableInNonSuspendedState().get());
            return;
        }

        Application.get().executeOnPooledThread(() -> {
            List<PyStackFrameInfo> frames = myThreadInfo.getFrames();
            if (frames != null && firstFrameIndex <= frames.size() && !container.isObsolete()) {
                List<PyStackFrame> xFrames = new LinkedList<>();
                for (int i = firstFrameIndex; i < frames.size() && !container.isObsolete(); i++) {
                    xFrames.add(convert(myDebugProcess, frames.get(i)));
                }
                container.addStackFrames(xFrames, true);
            }
            else {
                container.addStackFrames(Collections.emptyList(), true);
            }
        });
    }

    PyThreadInfo getThreadInfo() {
        return myThreadInfo;
    }

    private static PyStackFrame convert(PyDebugProcess debugProcess, PyStackFrameInfo frameInfo) {
        return debugProcess.createStackFrame(frameInfo);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PyExecutionStack that = (PyExecutionStack)o;

        if (myThreadInfo != null ? !myThreadInfo.equals(that.myThreadInfo) : that.myThreadInfo != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return myThreadInfo != null ? myThreadInfo.hashCode() : 0;
    }

    public String getThreadId() {
        return myThreadInfo.getId();
    }
}
