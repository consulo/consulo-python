// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console.pydev;

import java.util.function.Function;

import java.util.List;

public interface ConsoleCommunication {
    List<PydevCompletionVariant> getCompletions(String text, String actualToken) throws Exception;

    String getDescription(String text) throws Exception;

    boolean isWaitingForInput();

    boolean isExecuting();

    boolean needsMore();

    void execInterpreter(ConsoleCodeFragment code, Function<InterpreterResponse, Object> callback);

    void interrupt();

    void addCommunicationListener(ConsoleCommunicationListener listener);

    void notifyCommandExecuted(boolean more);
    void notifyInputRequested();

    void notifyInputReceived();

    class ConsoleCodeFragment {
        private String myText;
        private final boolean myIsSingleLine;

        public ConsoleCodeFragment(String text, boolean isSingleLine) {
            myText = text;
            myIsSingleLine = isSingleLine;
        }

        public String getText() {
            return myText;
        }

        public void setText(String text) {
            myText = text;
        }

        public boolean isSingleLine() {
            return myIsSingleLine;
        }
    }
}
