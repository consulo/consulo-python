// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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

import consulo.util.xml.serializer.annotation.Attribute;
import com.jetbrains.python.debugger.pydev.AddExceptionBreakpointCommand;
import com.jetbrains.python.debugger.pydev.ExceptionBreakpointCommand;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;
import org.jspecify.annotations.Nullable;

public class PyExceptionBreakpointProperties extends ExceptionBreakpointProperties<PyExceptionBreakpointProperties> {
    // Consulo-specific: "notify always" (on each raise) policy, JB has no equivalent
    @Attribute("notifyAlways")
    public boolean myNotifyAlways;
    @Attribute("notifyOnlyOnFirst")
    public boolean myNotifyOnlyOnFirst;
    @Attribute("notifyOnTerminate")
    public boolean myNotifyOnTerminate;
    @Attribute("ignoreLibraries")
    public boolean myIgnoreLibraries;
    public @Nullable String myCondition;
    public @Nullable String myLogExpression;


    @SuppressWarnings({"UnusedDeclaration"})
    public PyExceptionBreakpointProperties() {
    }

    public PyExceptionBreakpointProperties(String exception) {
        myException = exception;
        myNotifyOnTerminate = true;
        myIgnoreLibraries = false;
        myCondition = null;
        myLogExpression = null;
    }

    @Override
    public PyExceptionBreakpointProperties getState() {
        return this;
    }

    @Override
    public void loadState(PyExceptionBreakpointProperties state) {
        myException = state.myException;
        myNotifyAlways = state.myNotifyAlways;
        myNotifyOnlyOnFirst = state.myNotifyOnlyOnFirst;
        myNotifyOnTerminate = state.myNotifyOnTerminate;
        myIgnoreLibraries = state.myIgnoreLibraries;
    }

    // Consulo-specific
    public boolean isNotifyAlways() {
        return myNotifyAlways;
    }

    // Consulo-specific
    public void setNotifyAlways(boolean notifyAlways) {
        myNotifyAlways = notifyAlways;
    }

    public boolean isNotifyOnTerminate() {
        return myNotifyOnTerminate;
    }

    public void setNotifyOnTerminate(boolean notifyOnTerminate) {
        myNotifyOnTerminate = notifyOnTerminate;
    }

    public boolean isNotifyOnlyOnFirst() {
        return myNotifyOnlyOnFirst;
    }

    public void setNotifyOnlyOnFirst(boolean notifyOnlyOnFirst) {
        myNotifyOnlyOnFirst = notifyOnlyOnFirst;
    }

    public void setIgnoreLibraries(boolean ignoreLibraries) {
        myIgnoreLibraries = ignoreLibraries;
    }

    public boolean isIgnoreLibraries() {
        return myIgnoreLibraries;
    }

    public @Nullable String getCondition() {
        return myCondition;
    }

    @Override
    public void setCondition(@Nullable String condition) {
        myCondition = condition;
    }

    public @Nullable String getLogExpression() {
        return myLogExpression;
    }

    @Override
    public void setLogExpression(@Nullable String logExpression) {
        myLogExpression = logExpression;
    }

    @Override
    public String getExceptionBreakpointId() {
        return "python-" + myException;
    }

    @Override
    public ExceptionBreakpointCommand createAddCommand(RemoteDebugger debugger) {
        return ExceptionBreakpointCommand.addExceptionBreakpointCommand(debugger,
                                                                        getExceptionBreakpointId(),
                                                                        getCondition(),
                                                                        getLogExpression(),
                                                                        new AddExceptionBreakpointCommand.ExceptionBreakpointNotifyPolicy(
                                                                          // Consulo-specific: the "notify always" policy goes first
                                                                          isNotifyAlways(), isNotifyOnTerminate(), isNotifyOnlyOnFirst(), isIgnoreLibraries()));
    }

    @Override
    public ExceptionBreakpointCommand createRemoveCommand(RemoteDebugger debugger) {
        return ExceptionBreakpointCommand.removeExceptionBreakpointCommand(debugger, getExceptionBreakpointId());
    }
}
