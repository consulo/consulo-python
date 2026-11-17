// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev.dataviewer;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;
import org.jspecify.annotations.Nullable;

import java.io.File;

public class DataViewerCommandBuilder {
    private @Nullable RemoteDebugger myDebugger;
    private @Nullable String myThreadId;
    private @Nullable String myFrameId;

    private final PyDebugValue myVar;
    private final DataViewerCommandAction myAction;
    private final String @Nullable[] myArgs;

    public DataViewerCommandBuilder(PyDebugValue var, DataViewerCommandAction action, String @Nullable[] args) {
        myVar = var;
        myAction = action;
        myArgs = args;
    }

    public String @Nullable[] getArgs() {
        return myArgs;
    }

    public DataViewerCommandAction getAction() {
        return myAction;
    }

    public @Nullable RemoteDebugger getDebugger() {
        return myDebugger;
    }

    public @Nullable String getThreadId() {
        return myThreadId;
    }

    public @Nullable String getFrameId() {
        return myFrameId;
    }

    public PyDebugValue getVar() {
        return myVar;
    }

    public void setDebugger(RemoteDebugger debugger) {
        this.myDebugger = debugger;
    }

    public void setThreadId(String threadId) {
        this.myThreadId = threadId;
    }

    public void setFrameId(String frameId) {
        this.myFrameId = frameId;
    }

    public DataViewerCommand build() {
        assert myDebugger != null;
        assert myThreadId != null;
        assert myFrameId != null;

        return new DataViewerCommand(myDebugger, myThreadId, myFrameId, myVar, myAction, myArgs);
    }

    public static DataViewerCommandBuilder initExportCommand(PyDebugValue var, File target) {
        String[] args = { target.getAbsolutePath() };
        return new DataViewerCommandBuilder(var, DataViewerCommandAction.EXPORT, args);
    }
}
