// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.impl.codeInsight.stdlib.PyAttrsDataclassType;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassResolver;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassTransformType;
import com.jetbrains.python.impl.codeInsight.stdlib.PyStdlibDataclassType;
import com.jetbrains.python.psi.PyExpression;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Data describing dataclass.
 * <p>
 * A parameter has default value if it is omitted or its value could not be evaluated.
 * A parameter has {@code null} expression if it is omitted or is taken from a stub.
 */
public class PyDataclassParameters {
    private final boolean myInit;
    private final boolean myRepr;
    private final boolean myEq;
    private final boolean myOrder;
    private final boolean myUnsafeHash;
    private final @Nullable Boolean myFrozen;
    private final boolean myMatchArgs;
    private final boolean myKwOnly;
    private final boolean mySlots;
    private final @Nullable PyExpression myInitArgument;
    private final @Nullable PyExpression myReprArgument;
    private final @Nullable PyExpression myEqArgument;
    private final @Nullable PyExpression myOrderArgument;
    private final @Nullable PyExpression myUnsafeHashArgument;
    private final @Nullable PyExpression myFrozenArgument;
    private final @Nullable PyExpression myMatchArgsArgument;
    private final @Nullable PyExpression myKwOnlyArgument;
    private final @Nullable PyExpression mySlotsArgument;
    private final Type myType;
    private final Map<String, PyExpression> myOthers;
    private final List<QualifiedName> myFieldSpecifiers;

    public PyDataclassParameters(boolean init,
                                 boolean repr,
                                 boolean eq,
                                 boolean order,
                                 boolean unsafeHash,
                                 @Nullable Boolean frozen,
                                 boolean matchArgs,
                                 boolean kwOnly,
                                 boolean slots,
                                 @Nullable PyExpression initArgument,
                                 @Nullable PyExpression reprArgument,
                                 @Nullable PyExpression eqArgument,
                                 @Nullable PyExpression orderArgument,
                                 @Nullable PyExpression unsafeHashArgument,
                                 @Nullable PyExpression frozenArgument,
                                 @Nullable PyExpression matchArgsArgument,
                                 @Nullable PyExpression kwOnlyArgument,
                                 @Nullable PyExpression slotsArgument,
                                 Type type,
                                 Map<String, PyExpression> others) {
        this(init, repr, eq, order, unsafeHash, frozen, matchArgs, kwOnly, slots,
            initArgument, reprArgument, eqArgument, orderArgument, unsafeHashArgument, frozenArgument, matchArgsArgument, kwOnlyArgument,
            slotsArgument, type, others, List.of());
    }

    public PyDataclassParameters(boolean init,
                                 boolean repr,
                                 boolean eq,
                                 boolean order,
                                 boolean unsafeHash,
                                 @Nullable Boolean frozen,
                                 boolean matchArgs,
                                 boolean kwOnly,
                                 boolean slots,
                                 @Nullable PyExpression initArgument,
                                 @Nullable PyExpression reprArgument,
                                 @Nullable PyExpression eqArgument,
                                 @Nullable PyExpression orderArgument,
                                 @Nullable PyExpression unsafeHashArgument,
                                 @Nullable PyExpression frozenArgument,
                                 @Nullable PyExpression matchArgsArgument,
                                 @Nullable PyExpression kwOnlyArgument,
                                 @Nullable PyExpression slotsArgument,
                                 Type type,
                                 Map<String, PyExpression> others,
                                 List<QualifiedName> fieldSpecifiers) {
        myInit = init;
        myRepr = repr;
        myEq = eq;
        myOrder = order;
        myUnsafeHash = unsafeHash;
        myFrozen = frozen;
        myMatchArgs = matchArgs;
        myKwOnly = kwOnly;
        mySlots = slots;
        myInitArgument = initArgument;
        myReprArgument = reprArgument;
        myEqArgument = eqArgument;
        myOrderArgument = orderArgument;
        myUnsafeHashArgument = unsafeHashArgument;
        myFrozenArgument = frozenArgument;
        myMatchArgsArgument = matchArgsArgument;
        myKwOnlyArgument = kwOnlyArgument;
        mySlotsArgument = slotsArgument;
        myType = type;
        myOthers = others;
        myFieldSpecifiers = fieldSpecifiers;
    }

    public boolean getInit() {
        return myInit;
    }

    public boolean getRepr() {
        return myRepr;
    }

    public boolean getEq() {
        return myEq;
    }

    public boolean getOrder() {
        return myOrder;
    }

    public boolean getUnsafeHash() {
        return myUnsafeHash;
    }

    public @Nullable Boolean getFrozen() {
        return myFrozen;
    }

    public boolean getMatchArgs() {
        return myMatchArgs;
    }

    public boolean getKwOnly() {
        return myKwOnly;
    }

    public boolean getSlots() {
        return mySlots;
    }

    public @Nullable PyExpression getInitArgument() {
        return myInitArgument;
    }

    public @Nullable PyExpression getReprArgument() {
        return myReprArgument;
    }

    public @Nullable PyExpression getEqArgument() {
        return myEqArgument;
    }

    public @Nullable PyExpression getOrderArgument() {
        return myOrderArgument;
    }

    public @Nullable PyExpression getUnsafeHashArgument() {
        return myUnsafeHashArgument;
    }

    public @Nullable PyExpression getFrozenArgument() {
        return myFrozenArgument;
    }

    public @Nullable PyExpression getMatchArgsArgument() {
        return myMatchArgsArgument;
    }

    public @Nullable PyExpression getKwOnlyArgument() {
        return myKwOnlyArgument;
    }

    public @Nullable PyExpression getSlotsArgument() {
        return mySlotsArgument;
    }

    public Type getType() {
        return myType;
    }

    public Map<String, PyExpression> getOthers() {
        return myOthers;
    }

    public List<QualifiedName> getFieldSpecifiers() {
        return myFieldSpecifiers;
    }

    public interface Type {
        String getName();

        default @Nullable PyDataclassResolver getResolver() {
            return null;
        }

        default boolean isDataclassTransformBased() {
            return false;
        }

        /**
         * @deprecated Use the concrete Type singleton (PyStdlibDataclassType, PyAttrsDataclassType, PyDataclassTransformType) instead.
         */
        @Deprecated
        default @Nullable PredefinedType getAsPredefinedType() {
            return null;
        }
    }

    /**
     * @deprecated Use PyStdlibDataclassType, PyAttrsDataclassType or PyDataclassTransformType instead.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public enum PredefinedType implements Type {
        STD, ATTRS, DATACLASS_TRANSFORM;

        @Override
        public String getName() {
            return name();
        }

        @Override
        public @Nullable PyDataclassResolver getResolver() {
            return switch (this) {
                case STD -> PyStdlibDataclassType.INSTANCE.getResolver();
                case ATTRS -> PyAttrsDataclassType.INSTANCE.getResolver();
                case DATACLASS_TRANSFORM -> PyDataclassTransformType.INSTANCE.getResolver();
            };
        }

        @Override
        public boolean isDataclassTransformBased() {
            return this == DATACLASS_TRANSFORM;
        }

        @Override
        public @Nullable PredefinedType getAsPredefinedType() {
            return this;
        }
    }
}
