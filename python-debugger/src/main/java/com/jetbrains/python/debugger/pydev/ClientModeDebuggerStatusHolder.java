// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @deprecated The single-port/client-mode launch path is no longer wired up; nothing in the IDE constructs this any more.
 */
@Deprecated
class ClientModeDebuggerStatusHolder {
    /**
     * The status of the debugger looking from IDE side.
     *
     * @see ClientModeMultiProcessDebugger
     */
    private enum ClientModeDebuggerStatus {
        /**
         * The status before trying to connect to the main process.
         *
         * @see ClientModeMultiProcessDebugger#waitForConnect
         */
        INITIAL,

        /**
         * The status after requesting to establish the connection with the main
         * process but before the connection succeeded or failed.
         *
         * @see ClientModeMultiProcessDebugger#waitForConnect
         */
        CONNECTING,

        /**
         * Indicates that this {@link ClientModeMultiProcessDebugger} has connected
         * at least to one (the main one) Python debugging process.
         * <p>
         * <i>As Python debugging process does not start the underlying Python
         * script before we send him {@link RunCommand} so the first process we
         * connected to is the main Python script process.</i>
         */
        CONNECTED,

        /**
         * Corresponds to one of the two alternatives:
         * <ul>
         * <li>the debugger script process is terminated;</li>
         * <li>the user requested to detach IDE from the debugger script.</li>
         * </ul>
         */
        DISCONNECTION_INITIATED
    }

    private final ReentrantLock myLock = new ReentrantLock();

    private final Condition myCondition = myLock.newCondition();

    /**
     * Guarded by {@link #myLock}.
     */
    private ClientModeDebuggerStatus myStatus = ClientModeDebuggerStatus.INITIAL;

    /**
     * Switches the current status from "initial" to "connecting".
     *
     * @throws IllegalStateException if the current status is not initial
     */
    public void onConnecting() {
        myLock.lock();
        try {
            if (myStatus != ClientModeDebuggerStatus.INITIAL) {
                throw new IllegalStateException();
            }

            myStatus = ClientModeDebuggerStatus.CONNECTING;

            myCondition.signalAll();
        }
        finally {
            myLock.unlock();
        }
    }

    /**
     * Switches the current status from "connecting" to "connected". Returns
     * whether the switch succeeded.
     */
    public boolean onConnected() {
        myLock.lock();
        try {
            if (myStatus == ClientModeDebuggerStatus.CONNECTING) {
                myStatus = ClientModeDebuggerStatus.CONNECTED;

                myCondition.signalAll();

                return true;
            }
            else {
                return false;
            }
        }
        finally {
            myLock.unlock();
        }
    }

    /**
     * Switches the current status to "disconnecting" unconditionally.
     */
    public void onDisconnectionInitiated() {
        myLock.lock();
        try {
            myStatus = ClientModeDebuggerStatus.DISCONNECTION_INITIATED;

            myCondition.signalAll();
        }
        finally {
            myLock.unlock();
        }
    }

    /**
     * Causes the current thread to wait until the status is changed from
     * "connecting" to "connected" or "disconnected".
     * <p>
     * Returns {@code true} if the status becomes "connected" and {@code false} otherwise.
     *
     * @throws IllegalStateException if the current state is "initial"
     */
    public boolean awaitWhileConnecting() throws InterruptedException {
        myLock.lock();
        try {
            if (myStatus == ClientModeDebuggerStatus.INITIAL) {
                throw new IllegalStateException();
            }

            while (myStatus == ClientModeDebuggerStatus.CONNECTING) {
                myCondition.await();
            }

            return myStatus == ClientModeDebuggerStatus.CONNECTED;
        }
        finally {
            myLock.unlock();
        }
    }
}
