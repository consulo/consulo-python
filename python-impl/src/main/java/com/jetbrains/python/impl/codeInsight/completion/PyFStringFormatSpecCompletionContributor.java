// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.codeInsight.fstrings.PyFormatSpec;
import com.jetbrains.python.impl.codeInsight.fstrings.PyFormatSpecCatalog;
import com.jetbrains.python.impl.codeInsight.fstrings.PyFormatSpecCategory;
import com.jetbrains.python.impl.codeInsight.fstrings.PyFormatSpecComponentKind;
import com.jetbrains.python.impl.codeInsight.fstrings.PyFormatSpecOption;
import com.jetbrains.python.impl.psi.PyStringDunderUtil;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyFStringFragmentFormatPart;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static com.jetbrains.python.impl.codeInsight.fstrings.PyFormatSpecOptionKt.FORMAT_SPEC_OPTION_KEY;
import static com.jetbrains.python.impl.psi.PyStringDunderUtil.isAllowedFormatOverride;
import static com.jetbrains.python.impl.psi.types.PyTypeUtil.asUnionSequence;
import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * Provides code completion for f-string format specifications.
 * <p>
 * Offers completions after the colon in f-string format parts, e.g., f"{x:<caret>}"
 * Shows valid format spec options based on the expression type.
 */
@ExtensionImpl
public class PyFStringFormatSpecCompletionContributor extends CompletionContributor implements DumbAware {

    // The value types come from the shared catalog, so completion, the annotator and the inspections
    // agree on what supports the format mini-language.
    private static final Set<String> NUMERIC_TYPE_NAMES = PyStringDunderUtil.KNOWN_COMPLEX_TYPES;

    private static final Set<String> STRING_TYPE_NAMES = Set.of(PyNames.FQN.STR);

    private static final Set<String> DATETIME_TYPE_NAMES = Set.of(PyNames.FQN.DATE, PyNames.FQN.DATETIME, PyNames.FQN.TIME);

    // Specs offered when the value type is a plain string: alignment, precision and the string type.
    private static final Set<String> STRING_SPECS = Set.of("<", ">", "^", ".", "s");

    // Specs offered when the value type is unknown: a small, broadly-applicable subset.
    private static final Set<String> UNKNOWN_SPECS = Set.of(".", "d", "f", "s", "<", ">");

    public PyFStringFormatSpecCompletionContributor() {
        extend(CompletionType.BASIC, psiElement().inside(PyFStringFragmentFormatPart.class),
            new CompletionProvider() {
                @Override
                @RequiredReadAction
                public void addCompletions(CompletionParameters parameters,
                                           ProcessingContext context,
                                           CompletionResultSet result) {
                    PsiElement position = parameters.getPosition();
                    PyFStringFragmentFormatPart formatPart = PsiTreeUtil.getParentOfType(position, PyFStringFragmentFormatPart.class);
                    if (formatPart == null) {
                        return;
                    }
                    PyFStringFragment fragment = PsiTreeUtil.getParentOfType(formatPart, PyFStringFragment.class);
                    if (fragment == null) {
                        return;
                    }
                    PyExpression expression = fragment.getExpression();
                    if (expression == null) {
                        return;
                    }

                    // Bail out if the caret is inside a nested replacement field (e.g. the `width` in
                    // f"{x:{width}.2f}"): that expression has its own completions and must not get format
                    // specs mixed into them.
                    PyFStringFragment enclosingFragment = PsiTreeUtil.getParentOfType(position, PyFStringFragment.class);
                    if (enclosingFragment != null && PsiTreeUtil.isAncestor(formatPart, enclosingFragment, false)) {
                        return;
                    }

                    // The spec text between the colon and the caret, with a nested replacement field left out.
                    String textBeforeCaret = getTextBeforeCaret(formatPart);
                    String existingText = textBeforeCaret != null ? textBeforeCaret : "";

                    // A type with a custom __format__ whose format_spec is annotated with a string Literal[...]
                    // fully determines the valid specs: offer only those literal values and suppress the generic
                    // specs that don't apply to it (PY-84261). Type conversions (!s/!r/!a) format the resulting
                    // str instead, so they bypass this.
                    if (fragment.getTypeConversion() == null) {
                        List<String> literalSpecs = getLiteralFormatSpecs(expression);
                        if (literalSpecs != null) {
                            // The literal value is matched as a whole, so narrow by what has already been typed.
                            CompletionResultSet literalResult = result.withPrefixMatcher(existingText);
                            for (String it : literalSpecs) {
                                literalResult.addElement(createLiteralSpecElement(it));
                            }
                            return;
                        }
                    }

                    addFormatSpecCompletions(result, expression, fragment, existingText);
                }
            });
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    /**
     * Returns the literal spec text between the format start (the colon) and the caret, or `null` when the
     * format part has no format start. A nested replacement field and the closing brace are left out, so an
     * identifier such as the `width` in `f"{x:{width}.2f}"` is never read as spec characters.
     */
    private static @Nullable String getTextBeforeCaret(PyFStringFragmentFormatPart formatPart) {
        boolean afterFormatStart = false;
        StringBuilder text = new StringBuilder();

        for (ASTNode child : formatPart.getNode().getChildren(null)) {
            if (child.getElementType() == PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START) {
                afterFormatStart = true;
                continue;
            }
            if (!afterFormatStart ||
                child.getElementType() == PyElementTypes.FSTRING_FRAGMENT ||
                child.getElementType() == PyTokenTypes.FSTRING_FRAGMENT_END) {
                continue;
            }

            // The dummy identifier that the platform inserts marks the caret, so the text stops there.
            String childText = child.getText();
            int dummyIndex = childText.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);
            if (dummyIndex >= 0) {
                text.append(childText, 0, dummyIndex);
                return text.toString();
            }
            text.append(childText);
        }

        return afterFormatStart ? text.toString() : null;
    }

    private static void addFormatSpecCompletions(CompletionResultSet result,
                                                 PyExpression expression,
                                                 PyFStringFragment fragment,
                                                 String existingText) {
        ExpressionType expressionType = getExpressionType(expression, fragment);
        Predicate<PyFormatSpecOption> applies = appliesTo(expressionType);

        if (existingText.isEmpty()) {
            // Immediately after the colon: offer every option that applies to the value type.
            addOptions(result, filter(PyFormatSpecCatalog.getOptions(), applies));
            return;
        }

        if (expressionType == ExpressionType.DATETIME) {
            // A datetime spec is a free-form strftime string, so a directive can follow any text. A trailing
            // '%' already starts the next directive, so it becomes the prefix and the directive replaces it.
            String prefix = existingText.endsWith("%") ? "%" : "";
            addOptions(result.withPrefixMatcher(prefix), PyFormatSpecCatalog.of(PyFormatSpecCategory.DATETIME));
            return;
        }

        PyFormatSpec typed = PyFormatSpec.parse(existingText, false);

        // A presentation type ends the spec, so nothing more can follow it.
        if (typed.getHasPresentationType()) {
            return;
        }

        // Use an empty prefix matcher because the offered options extend the typed text instead of matching
        // it (e.g., after ".2" we want to offer "f").
        CompletionResultSet extending = result.withPrefixMatcher("");

        // After a precision (e.g. .2) or a width (e.g. r>3), only a presentation type can follow.
        if (typed.has(PyFormatSpecComponentKind.PRECISION_MARK) || typed.has(PyFormatSpecComponentKind.WIDTH)) {
            addOptions(extending, filter(PyFormatSpecCatalog.of(PyFormatSpecCategory.TYPE), applies));
            return;
        }

        // General case - offer the common options that apply to the value type.
        addOptions(extending, filter(PyFormatSpecCatalog.getOptions(), it -> UNKNOWN_SPECS.contains(it.getSpec()) && applies.test(it)));
    }

    private static List<PyFormatSpecOption> filter(List<PyFormatSpecOption> options, Predicate<PyFormatSpecOption> predicate) {
        List<PyFormatSpecOption> result = new ArrayList<>();
        for (PyFormatSpecOption option : options) {
            if (predicate.test(option)) {
                result.add(option);
            }
        }
        return result;
    }

    /**
     * The options that apply to a value of {@code expressionType}.
     */
    private static Predicate<PyFormatSpecOption> appliesTo(ExpressionType expressionType) {
        return switch (expressionType) {
            case STRING -> option -> STRING_SPECS.contains(option.getSpec());
            // A string presentation type ('s') is invalid for numbers, so it is excluded here.
            case NUMERIC -> option -> option.getCategory() != PyFormatSpecCategory.DATETIME && !option.getSpec().equals("s");
            case DATETIME -> option -> option.getCategory() == PyFormatSpecCategory.DATETIME;
            case UNKNOWN -> option -> UNKNOWN_SPECS.contains(option.getSpec());
        };
    }

    private static void addOptions(CompletionResultSet result, List<PyFormatSpecOption> options) {
        for (PyFormatSpecOption it : options) {
            result.addElement(createFormatSpecElement(it));
        }
    }

    private static LookupElement createFormatSpecElement(PyFormatSpecOption option) {
        LookupElementBuilder builder = LookupElementBuilder.create(option.getSpec())
            .withTypeText(option.getShortDescription().get(), true);
        builder.putUserData(FORMAT_SPEC_OPTION_KEY, option);
        return builder;
    }

    private static LookupElement createLiteralSpecElement(String value) {
        return LookupElementBuilder.create(value)
            .withTypeText(PyLocalize.fstringFormatSpecCompletionLiteralTypeText().get(), true);
    }

    /**
     * If the value's type defines a custom `__format__` whose `format_spec` parameter is annotated with a string
     * `Literal[...]` (e.g. `def __format__(self, format_spec: Literal["foo", "bar"], /) -> str`), returns the accepted
     * literal values. Returns `null` when no such annotation applies (including the builtin `str` `format_spec` of
     * `object.__format__` and numeric types), so the caller falls back to the generic format specs. PY-84261.
     * <p>
     * For a union value type every member must contribute string literal specs, otherwise the generic specs are used.
     */
    private static @Nullable List<String> getLiteralFormatSpecs(PyExpression expression) {
        TypeEvalContext context = TypeEvalContext.codeCompletion(expression.getProject(), expression.getContainingFile());
        PyType type = context.getType(expression);
        if (type == null) {
            return null;
        }

        Set<String> values = new LinkedHashSet<>();
        for (PyType member : asUnionSequence(type)) {
            if (!(member instanceof PyClassType classType)) {
                return null;
            }
            PyFunction formatMethod = classType.getPyClass().findMethodByName(PyNames.DUNDER_FORMAT, true, context);
            if (formatMethod == null) {
                return null;
            }
            // Parameters are [self, format_spec]; the positional-only '/' marker is not a PyNamedParameter.
            List<PyNamedParameter> namedParameters = new ArrayList<>();
            for (PyParameter parameter : formatMethod.getParameterList().getParameters()) {
                if (parameter instanceof PyNamedParameter namedParameter) {
                    namedParameters.add(namedParameter);
                }
            }
            PyNamedParameter specParam = namedParameters.size() > 1 ? namedParameters.get(1) : null;
            if (specParam == null) {
                return null;
            }
            PyType specType = context.getType(specParam);
            List<@Nullable String> specValues = null;
            if (specType != null) {
                specValues = new ArrayList<>();
                for (PyType it : asUnionSequence(specType)) {
                    specValues.add(it instanceof PyLiteralType literalType ? literalType.getStringValue() : null);
                }
            }
            if (specValues == null || specValues.isEmpty() || specValues.contains(null)) {
                return null;
            }
            for (String it : specValues) {
                values.add(it);
            }
        }
        return values.isEmpty() ? null : new ArrayList<>(values);
    }

    private enum ExpressionType {
        STRING,
        NUMERIC,
        DATETIME,
        UNKNOWN
    }

    private static ExpressionType getExpressionType(PyExpression expression, PyFStringFragment fragment) {
        // Conversion modifiers (!s, !r, !a) always produce a string before formatting.
        if (fragment.getTypeConversion() != null) {
            return ExpressionType.STRING;
        }

        TypeEvalContext context = TypeEvalContext.codeCompletion(expression.getProject(), expression.getContainingFile());
        PyType type = context.getType(expression);
        if (type == null) {
            return ExpressionType.UNKNOWN;
        }

        // The match walks the ancestors, so a subclass such as `bool` counts as its numeric base.
        List<PyType> members = new ArrayList<>();
        for (PyType it : asUnionSequence(type)) {
            if (it != null) {
                members.add(it);
            }
        }
        if (members.stream().anyMatch(it -> isAllowedFormatOverride(it, NUMERIC_TYPE_NAMES, context))) {
            return ExpressionType.NUMERIC;
        }
        if (members.stream().anyMatch(it -> isAllowedFormatOverride(it, STRING_TYPE_NAMES, context))) {
            return ExpressionType.STRING;
        }
        if (members.stream().anyMatch(it -> isAllowedFormatOverride(it, DATETIME_TYPE_NAMES, context))) {
            return ExpressionType.DATETIME;
        }
        return ExpressionType.UNKNOWN;
    }
}
