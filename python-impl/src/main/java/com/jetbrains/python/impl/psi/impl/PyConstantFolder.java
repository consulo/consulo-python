// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementType;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/**
 * The operations of Python on {@link PyConstantValue}. An operand of {@code null} is not known. An operation gives {@code null} when its operands
 * do not give a value or when the folder does not know the operation.
 */
public final class PyConstantFolder {
    private PyConstantFolder() {
    }

    /**
     * Applies a binary {@code operator}. {@code and} and {@code or} take {@code bool} operands only, and they give a value when the known operand decides it,
     * as {@code True or x} does.
     */
    public static @Nullable PyConstantValue binary(PyElementType operator, @Nullable PyConstantValue left, @Nullable PyConstantValue right) {
        if (operator == PyTokenTypes.AND_KEYWORD || operator == PyTokenTypes.OR_KEYWORD) {
            return logical(
                operator == PyTokenTypes.OR_KEYWORD,
                left instanceof PyConstantValue.Bool leftBool ? leftBool : null,
                right instanceof PyConstantValue.Bool rightBool ? rightBool : null
            );
        }
        if (left instanceof PyConstantValue.Str leftStr && right instanceof PyConstantValue.Str rightStr) {
            return strBinary(operator, leftStr.value(), rightStr.value());
        }
        if (left instanceof PyConstantValue.Int leftInt && right instanceof PyConstantValue.Int rightInt) {
            return intBinary(operator, leftInt.value(), rightInt.value());
        }
        return null;
    }

    /**
     * Returns {@code true} for a prefix {@code operator} that {@link #prefix} can apply. A caller can then skip the evaluation of the operand
     * for another operator.
     */
    public static boolean isPrefixOperator(PyElementType operator) {
        return operator == PyTokenTypes.NOT_KEYWORD || operator == PyTokenTypes.MINUS || operator == PyTokenTypes.PLUS;
    }

    /**
     * Applies a prefix {@code operator}: {@code not} to a {@code bool}, and {@code -} or {@code +} to an {@code int}.
     */
    public static @Nullable PyConstantValue prefix(PyElementType operator, @Nullable PyConstantValue operand) {
        if (operator == PyTokenTypes.NOT_KEYWORD && operand instanceof PyConstantValue.Bool bool) {
            return new PyConstantValue.Bool(!bool.value());
        }
        if (operator == PyTokenTypes.MINUS && operand instanceof PyConstantValue.Int intValue) {
            return new PyConstantValue.Int(intValue.value().negate());
        }
        if (operator == PyTokenTypes.PLUS && operand instanceof PyConstantValue.Int) {
            return operand;
        }
        return null;
    }

    /**
     * Returns the result of {@code bool(value)}.
     */
    public static boolean isTruthy(PyConstantValue value) {
        return switch (value) {
            case PyConstantValue.Str str -> !str.value().isEmpty();
            case PyConstantValue.Int intValue -> intValue.value().signum() != 0;
            case PyConstantValue.Bool bool -> bool.value();
        };
    }

    private static PyConstantValue.@Nullable Bool logical(boolean isOr, PyConstantValue.@Nullable Bool left, PyConstantValue.@Nullable Bool right) {
        if ((left != null && left.value() == isOr) || (right != null && right.value() == isOr)) {
            return new PyConstantValue.Bool(isOr);
        }
        if (left != null && right != null) {
            return new PyConstantValue.Bool(!isOr);
        }
        return null;
    }

    private static @Nullable PyConstantValue strBinary(PyElementType operator, String left, String right) {
        if (operator == PyTokenTypes.PLUS) {
            return new PyConstantValue.Str(left + right);
        }
        return null;
    }

    private static @Nullable PyConstantValue intBinary(PyElementType operator, BigInteger left, BigInteger right) {
        if (operator == PyTokenTypes.PLUS) {
            return new PyConstantValue.Int(left.add(right));
        }
        if (operator == PyTokenTypes.MINUS) {
            return new PyConstantValue.Int(left.subtract(right));
        }
        if (operator == PyTokenTypes.MULT) {
            return new PyConstantValue.Int(left.multiply(right));
        }
        if (operator == PyTokenTypes.FLOORDIV) {
            return right.signum() == 0 ? null : new PyConstantValue.Int(floorDivide(left, right));
        }
        if (operator == PyTokenTypes.LT) {
            return new PyConstantValue.Bool(left.compareTo(right) < 0);
        }
        if (operator == PyTokenTypes.LE) {
            return new PyConstantValue.Bool(left.compareTo(right) <= 0);
        }
        if (operator == PyTokenTypes.GT) {
            return new PyConstantValue.Bool(left.compareTo(right) > 0);
        }
        if (operator == PyTokenTypes.GE) {
            return new PyConstantValue.Bool(left.compareTo(right) >= 0);
        }
        if (operator == PyTokenTypes.EQEQ) {
            return new PyConstantValue.Bool(left.compareTo(right) == 0);
        }
        if (operator == PyTokenTypes.NE) {
            return new PyConstantValue.Bool(left.compareTo(right) != 0);
        }
        return null;
    }

    /**
     * Rounds the quotient down, as the {@code //} operator of Python does. {@link BigInteger#divide} rounds it toward zero.
     */
    private static BigInteger floorDivide(BigInteger dividend, BigInteger divisor) {
        BigInteger[] quotientAndRemainder = dividend.divideAndRemainder(divisor);
        BigInteger quotient = quotientAndRemainder[0];
        BigInteger remainder = quotientAndRemainder[1];
        return remainder.signum() * divisor.signum() < 0 ? quotient.subtract(BigInteger.ONE) : quotient;
    }
}
