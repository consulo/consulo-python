// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.console.actions;

import com.jetbrains.python.console.pydev.ConsoleCommunication;

/**
 * listener interface, required for rendering CommandQueue
 */
public interface CommandQueueListener {

    void removeCommand(ConsoleCommunication.ConsoleCodeFragment command);

    void addCommand(ConsoleCommunication.ConsoleCodeFragment command);

    void removeAll();

    void disableConsole();
}
