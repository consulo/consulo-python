// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;

import java.util.List;

/**
 *
 * User : ktisha
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyReferenceResolveProvider {
    ExtensionPointName<PyReferenceResolveProvider> EP_NAME = ExtensionPointName.create(PyReferenceResolveProvider.class);

    /**
     * Allows to provide a custom resolve result for qualified expression
     */
    List<RatedResolveResult> resolveName(PyQualifiedExpression element, TypeEvalContext context);

    default boolean allowsForwardOutgoingReferencesInClass(PyQualifiedExpression element) {
        return false;
    }
}
