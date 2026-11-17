// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.psi.PyElement;
import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.PsiReference;
import jakarta.inject.Singleton;

@ServiceImpl
@Singleton
public final class CodeStylePyImportCollectorFactory implements PyImportCollectorFactory {
    @Override
    public PyImportCollector create(PyElement node, PsiReference reference, String refText) {
        return new CodeStylePyImportCollector(node, reference, refText);
    }
}
