// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.typeignore;

import com.jetbrains.python.impl.inspections.PyIgnoreCommentUtil;
import com.jetbrains.python.impl.inspections.PyTypeCheckerSuppressionCode;

import java.util.HashSet;
import java.util.Set;

/**
 * Semantic resolution of the codes listed in a {@code # type: ignore} / {@code # pycharm: ignore} comment: which PyCharm
 * inspection (if any) a code names. Shared by {@link TypeIgnoreInspectionSuppressor}, {@link PyTypeIgnoreWithoutCodeInspection},
 * {@link PyUnknownIgnoreCodeInspection} and {@link PyIgnoreCodeCompletionContributor}.
 */
final class PyIgnoreCodeResolver {
    private static final Set<String> GRANULAR_TYPE_CHECKER_CODES = createGranularCodes();

    private PyIgnoreCodeResolver() {
    }

    private static Set<String> createGranularCodes() {
        Set<String> result = new HashSet<>();
        for (PyTypeCheckerSuppressionCode code : PyTypeCheckerSuppressionCode.values()) {
            result.add(code.getId());
        }
        return result;
    }

    /**
     * What a single code of an ignore comment names.
     */
    sealed interface Resolution {
        /**
         * A granular {@link PyTypeCheckerSuppressionCode} such as {@code unsupported-operator}. The type checker applies it.
         */
        record Granular() implements Resolution {
            static final Granular INSTANCE = new Granular();
        }

        /**
         * A whole inspection, by its suppress id or its kebab-case alias.
         */
        record Inspection(String suppressId) implements Resolution {
        }

        /**
         * A PyCharm code that names nothing. It has the {@code pycharm:} namespace, or it comes from {@code # pycharm: ignore}.
         * A name that looks like a suppress id, such as {@code PyFoo}, is never unknown. It can belong to an inspection of
         * another edition or of a disabled plugin.
         */
        record Unknown() implements Resolution {
            static final Unknown INSTANCE = new Unknown();
        }

        /**
         * A bare code of another tool, such as mypy's {@code attr-defined}.
         */
        record Foreign() implements Resolution {
            static final Foreign INSTANCE = new Foreign();
        }
    }

    private static boolean isGranular(String name) {
        return GRANULAR_TYPE_CHECKER_CODES.contains(name);
    }

    static Resolution resolve(PyIgnoreCommentUtil.CodeRef ref) {
        if (isGranular(ref.name())) {
            return Resolution.Granular.INSTANCE;
        }
        PyTypeIgnoreSuppressIds ids = PyTypeIgnoreSuppressIds.getInstance();
        String suppressId = ids.isKnownSuppressId(ref.name()) ? ref.name() : ids.resolveKebabAlias(ref.name());
        if (suppressId != null) {
            return new Resolution.Inspection(suppressId);
        }
        if (!ref.pycharmNamespaced()) {
            return Resolution.Foreign.INSTANCE;
        }
        if (looksLikeSuppressId(ref.name())) {
            return new Resolution.Inspection(ref.name());
        }
        return Resolution.Unknown.INSTANCE;
    }

    private static boolean looksLikeSuppressId(String name) {
        return name.length() > 2 && name.startsWith("Py") && Character.isUpperCase(name.charAt(2));
    }

    /**
     * {@code true} if {@code parsed} names at least one PyCharm code. An unknown PyCharm code counts, because
     * {@link PyUnknownIgnoreCodeInspection} reports it.
     */
    static boolean namesPyCharmCode(PyIgnoreCommentUtil.ParsedIgnore parsed) {
        return PyIgnoreCommentUtil.codeRefs(parsed).stream().anyMatch(it -> !(resolve(it) instanceof Resolution.Foreign));
    }
}
