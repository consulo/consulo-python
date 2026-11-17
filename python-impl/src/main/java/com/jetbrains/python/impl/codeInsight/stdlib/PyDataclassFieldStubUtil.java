// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.*;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared helpers for building dataclass field stubs (stdlib / attrs / third-party).
 */
// @ApiStatus.Internal
public final class PyDataclassFieldStubUtil {
    private PyDataclassFieldStubUtil() {
    }

    /**
     * Returns the field-initializer call for {@code field} (the direct assigned value) or {@code null} if it is not a call.
     */
    public static @Nullable PyCallExpression fieldInitializerCall(PyTargetExpression field) {
        return field.findAssignedValue() instanceof PyCallExpression call ? call : null;
    }

    /**
     * The locally resolved qualified names of {@code call}'s callee, as dotted strings.
     */
    public static List<String> calleeQualifiedNames(PyCallExpression call) {
        if (!(call.getCallee() instanceof PyReferenceExpression callee)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (QualifiedName it : PyResolveUtil.resolveImportedElementQNameLocally(callee)) {
            result.add(it.toString());
        }
        return result;
    }

    /**
     * Reads the field-initializer arguments shared by every framework.
     * <p>
     * {@code usePositionalDefault} mirrors transform-like frameworks (`dataclass_transform` / Pydantic),
     * where the first positional argument is the default value.
     */
    public static @Nullable FieldCallArgs parseCommonFieldCallArgs(
        PyCallExpression call,
        boolean usePositionalDefault
    ) {
        QualifiedName qualifiedName = call.getCallee() instanceof PyReferenceExpression callee ? callee.asQualifiedName() : null;
        if (qualifiedName == null) {
            return null;
        }
        PyExpression positionalDefault = null;
        for (PyExpression it : call.getArguments()) {
            if (!(it instanceof PyKeywordArgument)) {
                positionalDefault = it;
                break;
            }
        }
        PyExpression defaultArgument = call.getKeywordArgument("default");
        return new FieldCallArgs(
            qualifiedName,
            PyEvaluator.evaluateAsBooleanNoResolve(call.getKeywordArgument("init"), true),
            PyEvaluator.evaluateAsBooleanNoResolve(call.getKeywordArgument("kw_only")),
            defaultArgument != null ? defaultArgument : usePositionalDefault ? positionalDefault : null,
            call.getKeywordArgument("default_factory"),
            call.getKeywordArgument("factory"),
            call.getKeywordArgument("alias") instanceof PyStringLiteralExpression alias ? alias.getStringValue() : null
        );
    }

    /**
     * Raw field-initializer arguments common to all dataclass-like frameworks.
     */
    // @ApiStatus.Internal
    public static final class FieldCallArgs {
        private final QualifiedName qualifiedName;
        private final boolean initValue;
        private final @Nullable Boolean kwOnly;
        private final @Nullable PyExpression defaultValue;
        private final @Nullable PyExpression defaultFactory;
        private final @Nullable PyExpression factory;
        private final @Nullable String alias;

        public FieldCallArgs(QualifiedName qualifiedName,
                             boolean initValue,
                             @Nullable Boolean kwOnly,
                             @Nullable PyExpression defaultValue,
                             @Nullable PyExpression defaultFactory,
                             @Nullable PyExpression factory,
                             @Nullable String alias) {
            this.qualifiedName = qualifiedName;
            this.initValue = initValue;
            this.kwOnly = kwOnly;
            this.defaultValue = defaultValue;
            this.defaultFactory = defaultFactory;
            this.factory = factory;
            this.alias = alias;
        }

        public QualifiedName getQualifiedName() {
            return qualifiedName;
        }

        public boolean getInitValue() {
            return initValue;
        }

        public @Nullable Boolean getKwOnly() {
            return kwOnly;
        }

        public @Nullable PyExpression getDefault() {
            return defaultValue;
        }

        public @Nullable PyExpression getDefaultFactory() {
            return defaultFactory;
        }

        public @Nullable PyExpression getFactory() {
            return factory;
        }

        public @Nullable String getAlias() {
            return alias;
        }
    }
}
