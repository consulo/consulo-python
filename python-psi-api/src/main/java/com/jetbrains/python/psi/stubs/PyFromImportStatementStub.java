// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.psi.PyFromImportStatement;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;


public interface PyFromImportStatementStub extends StubElement<PyFromImportStatement>, PyVersionSpecificStub {
    @Nullable
    QualifiedName getImportSourceQName();

    /**
     * @deprecated Use stub-based {@link PyFromImportStatement#getStarImportElement()} instead.
     */
    @Deprecated(forRemoval = true)
    boolean isStarImport();

    int getRelativeLevel();
}
