// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import org.jspecify.annotations.Nullable;

/**
 * Abstract function parameter; may cover either a named parameter or a tuple of parameters.
 * @see com.jetbrains.python.psi.impl.ParamHelper
 */
public interface PyAstParameter extends PyAstElement {

    /**
     * @return the named parameter which is represented by this parameter, or null if the parameter is a tuple.
     */
    @Nullable PyAstNamedParameter getAsNamed();

    /**
     * @return the tuple parameter which is represented by this parameter, or null if the parameter is named.
     */
    @Nullable PyAstTupleParameter getAsTuple();

    @Nullable PyAstExpression getDefaultValue();

    boolean hasDefaultValue();

    @Nullable String getDefaultValueText();

    /**
     * @return true if the parameter is the 'self' parameter of an instance attribute function or a function
     * annotated with @classmethod
     */
    boolean isSelf();
}
