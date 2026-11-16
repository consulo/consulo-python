// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.impl.psi.LazyParseablePsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import org.jspecify.annotations.Nullable;

public class PyLazyParseablePsiElement extends LazyParseablePsiElement implements PyElement {

    public PyLazyParseablePsiElement(IElementType type,
                                     @Nullable CharSequence buffer) {
        super(type, buffer);
    }

    @Override
    public void accept(PsiElementVisitor visitor) {
        PyUtil.verboseOnly(() -> PyPsiUtils.assertValid(this));
        if (visitor instanceof PyElementVisitor) {
            acceptPyVisitor(((PyElementVisitor) visitor));
        }
        else {
            super.accept(visitor);
        }
    }

    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyElement(this);
    }

    @Override
    public PythonLanguage getLanguage() {
        return (PythonLanguage) PythonFileType.INSTANCE.getLanguage();
    }

    @Override
    public @Nullable PsiReference findReferenceAt(int offset) {
        return PyBaseElementImpl.findReferenceAt(this, offset);
    }

    /**
     * Consulo: JB inherits this method from {@code com.jetbrains.python.ast.PyAstElement#childrenToPsi},
     * the python-ast layer is not ported, so it is declared here as in {@link PyBaseElementImpl}.
     */
    @RequiredReadAction
    protected <T extends PyElement> T[] childrenToPsi(TokenSet filterSet, T[] array) {
        ASTNode[] nodes = getNode().getChildren(filterSet);
        return PyPsiUtils.nodesToPsi(nodes, array);
    }
}
