// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.impl.run.PythonRunConfiguration;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.codeEditor.RealEditor;
import consulo.dataContext.DataContext;
import consulo.execution.ui.ExecutionConsole;
import consulo.execution.ui.RunContentDescriptor;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyExecuteConsoleCustomizer {
    static PyExecuteConsoleCustomizer getInstance() {
        return Application.get().getExtensionPoint(PyExecuteConsoleCustomizer.class).getExtensionList().getFirst();
    }

    /**
     * Return true if `virtualFile` supports execution in custom run descriptor. This descriptor will be used for executing the whole file
     * or a code fragment from it
     */
    default boolean isCustomDescriptorSupported(VirtualFile virtualFile) {
        return false;
    }

    /**
     * Return type of a custom run descriptor, which will be used for executing virtualFile or a code fragment from it
     */
    default @Nullable DescriptorType getCustomDescriptorType(VirtualFile virtualFile) {
        return null;
    }

    /**
     * Return existing run descriptor, if a file's custom descriptor type is `DescriptorType.EXISTING`
     */
    default @Nullable RunContentDescriptor getExistingDescriptor(VirtualFile virtualFile) {
        return null;
    }

    /**
     * Update custom descriptor value and type for `virtualFile`
     */
    default void updateDescriptor(VirtualFile virtualFile, DescriptorType type, @Nullable RunContentDescriptor descriptor) {
    }

    /**
     * Notify about new name set for custom run descriptor
     */
    default void descriptorNameUpdated(RunContentDescriptor descriptor, String newName) {
        ExecutionConsole console = descriptor.getExecutionConsole();
        if (console instanceof PythonConsoleView pythonConsoleView) {
            pythonConsoleView.setCommandQueueTitle(newName);
        }
    }

    /**
     * Return run descriptor name
     */
    default String getDescriptorName(RunContentDescriptor descriptor) {
        return descriptor.getDisplayName();
    }

    /**
     * Return a run configuration created from the context
     */
    default @Nullable PythonRunConfiguration getContextConfig(DataContext dataContext) {
        return null;
    }

    /**
     * Return true if console is starting and schedule command execution
     */
    default boolean isConsoleStarting(@Nullable VirtualFile virtualFile, @Nullable String commandText) {
        return false;
    }

    /**
     * Notify that runner started execution, but console process will be started later
     */
    default void notifyRunnerStart(VirtualFile virtualFile, PydevConsoleRunner runner) {
    }

    default boolean isHorizontalAndUnitedToolbar() {
        return false;
    }

    default void notifySciCellGutterExecuted(RealEditor editor, String actionId) {
    }
}
