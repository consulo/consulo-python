// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.psi.stub.StubElement;

public class PyElementImpl extends PyBaseElementImpl<StubElement> {
    public PyElementImpl(ASTNode astNode) {
        super(astNode);
    }
}
