// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeRepresentation;

import com.jetbrains.python.impl.psi.PyFileElementType;

public final class PyTypeRepresentationFileElementType extends PyFileElementType {
    public static final PyTypeRepresentationFileElementType INSTANCE = new PyTypeRepresentationFileElementType();

    private PyTypeRepresentationFileElementType() {
        super(PyTypeRepresentationDialect.INSTANCE);
    }

    @Override
    public String getExternalId() {
        return "PyTypeRepresentation.ID";
    }
}
