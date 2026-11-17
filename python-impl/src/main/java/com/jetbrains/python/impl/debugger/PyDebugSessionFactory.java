// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import consulo.process.ExecutionException;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.content.bundle.Sdk;
import consulo.execution.debug.XDebugSession;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import org.jspecify.annotations.Nullable;

/**
 * @author Alexander Koshevoy
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class PyDebugSessionFactory {
    public static final ExtensionPointName<PyDebugSessionFactory> EP_NAME = ExtensionPointName.create(PyDebugSessionFactory.class);

    protected abstract boolean appliesTo(Sdk sdk);

    public abstract XDebugSession createSession(PythonCommandLineState state,
                                                         ExecutionEnvironment environment)
      throws ExecutionException;

    public static @Nullable PyDebugSessionFactory findExtension(@Nullable Sdk sdk) {
        if (sdk == null) {
            return null;
        }
        for (PyDebugSessionFactory sessionCreator : EP_NAME.getExtensions()) {
            if (sessionCreator.appliesTo(sdk)) {
                return sessionCreator;
            }
        }
        return null;
    }
}
