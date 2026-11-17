// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.stubs.PyStarImportElementStub;

public class PyStarImportElementStubImpl extends StubBase<PyStarImportElement> implements PyStarImportElementStub {
    protected PyStarImportElementStubImpl(StubElement parent) {
        super(parent, PyStubElementTypes.STAR_IMPORT_ELEMENT);
    }
}
