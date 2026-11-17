// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.python.regexp.impl.internal;

import consulo.language.Language;
import consulo.language.psi.PsiElement;
import org.intellij.lang.regexp.RegExpCapability;

import java.util.EnumSet;

/**
 * Top-level declarations of {@code PythonMrabRegexp.kt}.
 */
public final class PythonMrabRegexpKt {
    static final EnumSet<RegExpCapability> CAPABILITIES = EnumSet.of(
        RegExpCapability.DANGLING_METACHARACTERS,
        RegExpCapability.OCTAL_NO_LEADING_ZERO,
        RegExpCapability.OMIT_NUMBERS_IN_QUANTIFIERS,
        RegExpCapability.OMIT_BOTH_NUMBERS_IN_QUANTIFIERS,
        RegExpCapability.MIN_OCTAL_3_DIGITS,
        // TODO consulo: RegExpCapability.SHORTHAND_CLASS_RANGE_START_ERROR missing
        // TODO consulo: RegExpCapability.SHORTHAND_CLASS_RANGE_END_ERROR missing
        RegExpCapability.ALLOW_HORIZONTAL_WHITESPACE_CLASS,
        RegExpCapability.POSIX_BRACKET_EXPRESSIONS,
        RegExpCapability.UNICODE_CATEGORY_SHORTHAND,
        RegExpCapability.CARET_NEGATED_PROPERTIES,
        RegExpCapability.PROPERTY_VALUES,
        RegExpCapability.PCRE_BACK_REFERENCES,
        RegExpCapability.PCRE_NUMBERED_GROUP_REF,
        RegExpCapability.PCRE_CONDITIONS
    );

    static final EnumSet<RegExpCapability> VERBOSE_CAPABILITIES = createVerboseCapabilities();

    private PythonMrabRegexpKt() {
    }

    private static EnumSet<RegExpCapability> createVerboseCapabilities() {
        EnumSet<RegExpCapability> capabilities = EnumSet.copyOf(CAPABILITIES);
        capabilities.add(RegExpCapability.COMMENT_MODE);
        return capabilities;
    }

    static boolean isMrabRegexp(PsiElement context) {
        Language language = context.getContainingFile().getLanguage();
        return language == PythonMrabRegexpLanguage.INSTANCE || language == PythonMrabVerboseRegexpLanguage.INSTANCE;
    }
}
