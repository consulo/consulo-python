// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstQualifiedElement;
import org.jspecify.annotations.Nullable;

/**
 * Represents an optionally qualified element with an associated name. Most of such elements are expressions and extend
 * {@link PyQualifiedExpression}, but one notable exception is {@link PyAugAssignmentStatement}.
 * In practice, using this interface means "any construct referring to something", i.e. having a {@link consulo.language.psi.PsiReference} on it.
 * <p>
 * Don't mix this interface up with {@link PyQualifiedNameOwner}, which means "some declaration having a name".
 * Typically, this is what {@link PyQualifiedElement} refers to.
 *
 * @see PyQualifiedNameOwner
 */
public interface PyQualifiedElement extends PyAstQualifiedElement, PyElement {
    @Override
    @Nullable
    PyExpression getQualifier();
}
