// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyTokenTypes;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.util.QualifiedName;

import java.util.ArrayList;
import java.util.List;

public interface PyAstImportStatement extends PyAstImportStatementBase, PyAstImplicitImportNameDefiner {
    @Override
    @RequiredReadAction
    default List<String> getFullyQualifiedObjectNames() {
        return getImportElementNames(getImportElements());
    }

    /**
     * @return {@code true} iff this is a {@code lazy import ...} statement (PEP 810, Python 3.15+).
     */
    @RequiredReadAction
    default boolean isLazy() {
        return getNode().findChildByType(PyTokenTypes.LAZY_KEYWORD) != null;
    }

    /**
     * Returns list of qualified names of import elements filtering out nulls
     * @param elements import elements
     * @return list of qualified names
     */
    static List<String> getImportElementNames(PyAstImportElement... elements) {
        List<String> result = new ArrayList<>(elements.length);
        for (PyAstImportElement element : elements) {
            QualifiedName qName = element.getImportedQName();
            if (qName != null) {
                result.add(qName.toString());
            }
        }
        return result;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyImportStatement(this);
    }
}
