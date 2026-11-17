// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeRepresentation;

import com.jetbrains.python.impl.codeInsight.typeRepresentation.psi.PyFunctionTypeRepresentation;
import com.jetbrains.python.impl.codeInsight.typeRepresentation.psi.PyNamedParameterTypeRepresentation;
import com.jetbrains.python.impl.codeInsight.typeRepresentation.psi.PyParameterListRepresentation;
import com.jetbrains.python.impl.psi.impl.PyElementImpl;
import com.jetbrains.python.psi.PyElementType;
import consulo.language.ast.IElementType;

public final class PyTypeRepresentationElementTypes {
    public static final PyElementType FUNCTION_SIGNATURE = new PyElementType("FUNCTION_SIGNATURE", node -> new PyFunctionTypeRepresentation(node));
    public static final PyElementType PARAMETER_TYPE_LIST = new PyElementType("PARAMETER_TYPE_LIST", node -> new PyParameterListRepresentation(node));
    public static final PyElementType NAMED_PARAMETER_TYPE =
        new PyElementType("NAMED_PARAMETER_TYPE", node -> new PyNamedParameterTypeRepresentation(node));
    public static final IElementType PLACEHOLDER = new PyElementType("PLACEHOLDER", node -> new PyElementImpl(node));

    private PyTypeRepresentationElementTypes() {
    }
}
