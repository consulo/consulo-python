// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.psi.PyElement;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.language.psi.PsiReference;

@ServiceAPI(ComponentScope.APPLICATION)
public interface PyImportCollectorFactory {
    static PyImportCollectorFactory getInstance() {
        return Application.get().getInstance(PyImportCollectorFactory.class);
    }

    PyImportCollector create(PyElement node, PsiReference reference, String refText);
}
