package com.jetbrains.python.ast;

/**
 * Describes a generalized expression, possibly typed.
 */
public interface PyAstExpression extends PyAstTypedElement {
    PyAstExpression[] EMPTY_ARRAY = new PyAstExpression[0];
}
