// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console.actions;

import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.impl.console.PydevConsoleCommunication;
import com.jetbrains.python.impl.console.PydevConsoleExecuteActionHandler;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.project.Project;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Service for command queue in Python console.
 * It has own listener(CommandQueueListener myListener), which it notifies about the changes in the command queue.
 */
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class CommandQueueForPythonConsoleService {
    private final Map<ConsoleCommunication, CommandQueueListener> myListeners = new ConcurrentHashMap<>();
    private final Map<ConsoleCommunication, Queue<ConsoleCommunication.ConsoleCodeFragment>> myQueues = new ConcurrentHashMap<>();
    private final Map<ConsoleCommunication, PydevConsoleExecuteActionHandler> myHandlers = new ConcurrentHashMap<>();

    private static final String STUB = "pass";

    public static CommandQueueForPythonConsoleService getInstance(Project project) {
        return project.getInstance(CommandQueueForPythonConsoleService.class);
    }

    private @Nullable Queue<ConsoleCommunication.ConsoleCodeFragment> getQueue(ConsoleCommunication consoleComm) {
        return myQueues.get(consoleComm);
    }

    /**
     * Add new listener that is responsible for drawing the command queue.
     *
     * @param consoleComm a console communication corresponding to a console instance for listening events
     * @param listener    instance of a listener which will receive notifications from the service, responsible for the correct rendering of the panel
     */
    public synchronized void addListener(ConsoleCommunication consoleComm, CommandQueueListener listener) {
        myListeners.put(consoleComm, listener);
    }

    public synchronized void removeListener(ConsoleCommunication consoleComm) {
        myListeners.remove(consoleComm);
    }

    public synchronized ConsoleCommunication.@Nullable ConsoleCodeFragment getFirstCommand(ConsoleCommunication consoleComm) {
        Queue<ConsoleCommunication.ConsoleCodeFragment> queue = getQueue(consoleComm);
        if (queue == null) {
            return null;
        }
        for (ConsoleCommunication.ConsoleCodeFragment elem : queue) {
            if (!elem.getText().equals(STUB)) {
                return elem;
            }
        }
        return null;
    }

    public boolean isEmpty(ConsoleCommunication consoleComm) {
        if (consoleComm instanceof PydevConsoleCommunication) {
            if (((PydevConsoleCommunication) consoleComm).isCommunicationClosed()) {
                return true;
            }
        }
        Queue<ConsoleCommunication.ConsoleCodeFragment> queue = getQueue(consoleComm);
        if (queue == null) {
            return true;
        }
        return queue.isEmpty();
    }

    public synchronized boolean isOneElement(ConsoleCommunication consoleComm) {
        Queue<ConsoleCommunication.ConsoleCodeFragment> queue = getQueue(consoleComm);
        if (queue == null) {
            return false;
        }
        return queue.size() == 1;
    }

    public synchronized boolean isTwoElement(ConsoleCommunication consoleComm) {
        Queue<ConsoleCommunication.ConsoleCodeFragment> queue = getQueue(consoleComm);
        if (queue == null) {
            return false;
        }
        return queue.size() == 2;
    }

    /**
     * Remove commands from queue and notify CommandQueueListener.
     *
     * @param consoleComm       a console communication corresponding to a console instance
     * @param exceptionOccurred if true then an exception occurred while executing the command, we should clear the queue else remove one command
     */
    public synchronized void removeCommand(ConsoleCommunication consoleComm, boolean exceptionOccurred) {
        Queue<ConsoleCommunication.ConsoleCodeFragment> queue = getQueue(consoleComm);
        if (queue == null) {
            return;
        }
        if (!queue.isEmpty()) {
            if (exceptionOccurred) {
                int value = queue.size();
                if (value > 1) {
                    myHandlers.get(consoleComm).decreaseInputPromptCount(value - 1);
                }

                queue.clear();
                CommandQueueListener listener = myListeners.get(consoleComm);
                if (listener != null) {
                    listener.removeAll();
                }
            }
            else {
                ConsoleCommunication.ConsoleCodeFragment command = queue.remove();
                if (!command.getText().isBlank() && !command.getText().equals(STUB)) {
                    myListeners.get(consoleComm).removeCommand(command);
                }

                if (!queue.isEmpty()) {
                    execCommand(consoleComm, queue.peek());
                }
            }
        }
    }

    /**
     * Calls if user remove command from queue using the button.
     * When using IPython, in order not to break the numbering of cells, inserts `pass` into the queue instead of a `codeFragment`.
     *
     * @param consoleComm  a console communication corresponding to a console instance
     * @param codeFragment a text of the command that a user deleted
     */
    public synchronized void removeCommand(ConsoleCommunication consoleComm, ConsoleCommunication.ConsoleCodeFragment codeFragment) {
        Queue<ConsoleCommunication.ConsoleCodeFragment> queue = getQueue(consoleComm);
        if (queue == null) {
            return;
        }
        if (!queue.isEmpty()) {
            for (ConsoleCommunication.ConsoleCodeFragment code : queue) {
                if (code.equals(codeFragment)) {
                    code.setText(STUB);
                    break;
                }
            }
        }
    }

    public synchronized void addNewCommand(
        PydevConsoleExecuteActionHandler pydevConsoleExecuteActionHandler,
        ConsoleCommunication.ConsoleCodeFragment code
    ) {
        ConsoleCommunication console = pydevConsoleExecuteActionHandler.getConsoleCommunication();
        if (console.isWaitingForInput()) {
            execCommand(console, code);
            return;
        }
        if (!myQueues.containsKey(console)) {
            myQueues.put(console, new ConcurrentLinkedDeque<>());
            myHandlers.put(console, pydevConsoleExecuteActionHandler);
        }
        Queue<ConsoleCommunication.ConsoleCodeFragment> queue = getQueue(console);
        if (queue == null) {
            return;
        }
        if (!code.getText().isBlank()) {
            queue.add(code);
            myListeners.get(console).addCommand(code);
        }

        if (queue.size() == 1) {
            execCommand(console, code);
        }

        pydevConsoleExecuteActionHandler.updateConsoleState();
    }

    /**
     * Disable Command Queue for all Python Consoles.
     * Notify listeners that queues are disabled.
     */
    public synchronized void disableCommandQueue() {
        for (Queue<ConsoleCommunication.ConsoleCodeFragment> queue : myQueues.values()) {
            if (queue != null) {
                queue.clear();
            }
        }

        for (CommandQueueListener listener : myListeners.values()) {
            if (listener != null) {
                listener.disableConsole();
            }
        }
    }

    private static void execCommand(ConsoleCommunication comm, ConsoleCommunication.ConsoleCodeFragment code) {
        comm.execInterpreter(code, ignored -> null);
    }
}
