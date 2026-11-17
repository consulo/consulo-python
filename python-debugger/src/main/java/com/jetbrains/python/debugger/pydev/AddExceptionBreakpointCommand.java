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
package com.jetbrains.python.debugger.pydev;

import org.jspecify.annotations.Nullable;

public class AddExceptionBreakpointCommand extends ExceptionBreakpointCommand {
    final ExceptionBreakpointNotifyPolicy myNotifyPolicy;
    final @Nullable String myCondition;
    final @Nullable String myLogExpression;

    public AddExceptionBreakpointCommand(RemoteDebugger debugger,
                                         String exception,
                                         @Nullable String condition,
                                         @Nullable String logExpression,
                                         ExceptionBreakpointNotifyPolicy notifyPolicy) {
        super(debugger, ADD_EXCEPTION_BREAKPOINT, exception);
        myNotifyPolicy = notifyPolicy;
        myCondition = condition;
        myLogExpression = logExpression;
    }

    @Override
    protected void buildPayload(Payload payload) {
        super.buildPayload(payload);
        payload.add(buildCondition(myCondition))
          .add(buildCondition(myLogExpression))
          // Consulo-specific: pydevd notify-on-raise values: 1 - on each raise, 2 - only on the first raise, 0 - not on raise
          .add(myNotifyPolicy.isNotifyOnlyOnFirst() ? 2 : myNotifyPolicy.isNotifyAlways() ? 1 : 0)
          .add(myNotifyPolicy.isNotifyOnTerminate())
          .add(myNotifyPolicy.isIgnoreLibraries());
    }

    public static class ExceptionBreakpointNotifyPolicy {
        // Consulo-specific: "notify always" (on each raise) policy, JB has no equivalent
        private final boolean myNotifyAlways;
        private final boolean myNotifyOnTerminate;
        private final boolean myNotifyOnlyOnFirst;
        private final boolean myIgnoreLibraries;

        /**
         * Consulo-specific: JB has {@code (notifyOnTerminate, notifyOnlyOnFirst, ignoreLibraries)} only,
         * Consulo additionally sends the "notify always" (on each raise) policy.
         */
        public ExceptionBreakpointNotifyPolicy(boolean notifyAlways,
                                               boolean notifyOnTerminate,
                                               boolean notifyOnlyOnFirst,
                                               boolean ignoreLibraries) {
            myNotifyAlways = notifyAlways;
            myNotifyOnTerminate = notifyOnTerminate;
            myNotifyOnlyOnFirst = notifyOnlyOnFirst;
            myIgnoreLibraries = ignoreLibraries;
        }

        public boolean isNotifyAlways() {
            return myNotifyAlways;
        }

        public boolean isNotifyOnTerminate() {
            return myNotifyOnTerminate;
        }

        public boolean isNotifyOnlyOnFirst() {
            return myNotifyOnlyOnFirst;
        }

        public boolean isIgnoreLibraries() {
            return myIgnoreLibraries;
        }
    }
}
