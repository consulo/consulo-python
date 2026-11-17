// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.execution.debug.frame.XDebuggerTreeNodeHyperlink;
import consulo.execution.debug.frame.XValueNode;
import org.jspecify.annotations.Nullable;

/**
 * Adds a hyperlink to the variables of the debug tool window, a "View as Image" action for example.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyDebugValueAdditionalHyperlinkProvider {
    @Nullable XDebuggerTreeNodeHyperlink provideHyperlink(PyDebugValue debugValue, XValueNode valueNode);

    static void computeHyperlink(PyDebugValue debugValue, XValueNode valueNode) {
        Application.get().getExtensionPoint(PyDebugValueAdditionalHyperlinkProvider.class).forEach(provider -> {
            XDebuggerTreeNodeHyperlink hyperlink = provider.provideHyperlink(debugValue, valueNode);
            if (hyperlink != null) {
                valueNode.addAdditionalHyperlink(hyperlink);
            }
        });
    }
}
