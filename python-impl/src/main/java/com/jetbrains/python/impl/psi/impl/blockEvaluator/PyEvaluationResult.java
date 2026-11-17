// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.blockEvaluator;

import com.jetbrains.python.psi.PyExpression;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Ilya.Kazakevich
 */
@SuppressWarnings("PackageVisibleField") // Package-only class
class PyEvaluationResult {
    Map<String, Object> myNamespace = new HashMap<>();
    Map<String, List<PyExpression>> myDeclarations = new HashMap<>();

    List<PyExpression> getDeclarations(String name) {
        List<PyExpression> expressions = myDeclarations.get(name);
        return (expressions != null) ? expressions : Collections.emptyList();
    }
}
