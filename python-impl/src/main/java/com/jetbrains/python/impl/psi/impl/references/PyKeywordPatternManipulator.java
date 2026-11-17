// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.references;

import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.psi.AbstractElementManipulator;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyKeywordPattern;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyKeywordPatternManipulator extends AbstractElementManipulator<PyKeywordPattern> {
    @Override
    public @Nullable PyKeywordPattern handleContentChange(PyKeywordPattern element,
                                                          TextRange range,
                                                          String newContent) throws IncorrectOperationException {
        if (element.getKeywordElement().getTextRangeInParent().equals(range)) {
            PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
            String newPatternText = newContent + "=None";
            PyKeywordPattern newPattern = (PyKeywordPattern)generator.createPatternFromText(LanguageLevel.forElement(element), newPatternText);
            element.getKeywordElement().replace(newPattern.getKeywordElement());
            return element;
        }
        return null;
    }

    @Override
    public Class<PyKeywordPattern> getElementClass() {
        return PyKeywordPattern.class;
    }
}
