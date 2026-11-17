// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.psi.PyImportElement;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;


public interface PyImportElementStub extends StubElement<PyImportElement> {
    @Nullable
    QualifiedName getImportedQName();

    String getAsName();
}
