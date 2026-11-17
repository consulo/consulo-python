// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;

import java.util.List;

/**
 * Allows to provide a custom list of keyword arguments for a function that uses **kwargs.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyKeywordArgumentProvider {
    ExtensionPointName<PyKeywordArgumentProvider> EP_NAME = ExtensionPointName.create(PyKeywordArgumentProvider.class);

    List<String> getKeywordArguments(PyFunction function, PyCallExpression callExpr);
}
