// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import consulo.language.psi.PsiPolyVariantReference;
import com.jetbrains.python.ast.PyAstReferenceOwner;
import com.jetbrains.python.psi.resolve.PyResolveContext;

public interface PyReferenceOwner extends PyAstReferenceOwner, PyElement {
    PsiPolyVariantReference getReference(PyResolveContext context);
}
