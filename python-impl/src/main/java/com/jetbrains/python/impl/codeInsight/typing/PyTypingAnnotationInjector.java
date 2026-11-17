// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typing;

import com.jetbrains.python.impl.codeInsight.PyInjectionUtil;
import com.jetbrains.python.impl.codeInsight.PyInjectorBase;
import com.jetbrains.python.impl.codeInsight.functionTypeComments.PyFunctionTypeAnnotationDialect;
import com.jetbrains.python.impl.codeInsight.typeHints.PyTypeHintDialect;
import com.jetbrains.python.impl.psi.impl.stubs.PyTypingAliasStubType;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.inject.MultiHostRegistrar;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Injects fragments for type annotations either in string literals (quoted annotations containing forward references) or
 * in type comments starting with <tt># type:</tt>.
 * <p>
 * Consulo: a {@code MultiHostInjector} is registered per host element class, see {@link PyTypingAnnotationStringLiteralInjector}
 * and {@link PyTypingAnnotationCommentInjector}. JB registers it for {@link PyInjectionUtil#ELEMENTS_TO_INJECT_IN}, but only
 * string literal and comment contexts can produce an injection.
 */
public abstract class PyTypingAnnotationInjector extends PyInjectorBase {
    @Override
    @RequiredReadAction
    protected PyInjectionUtil.InjectionResult registerInjection(MultiHostRegistrar registrar, PsiElement context) {
        // Handles only string literals containing quoted types
        Language language = getInjectedLanguage(context);
        if (language != null) {
            PsiElement element = PyInjectionUtil.getLargestStringLiteral(context);
            if (element != null) {
                if (language == PyTypeHintDialect.INSTANCE && element.getText().contains("\n")) {
                    return PyInjectionUtil.registerStringLiteralInjectionWithParenthesis(element, registrar, language);
                }
                else {
                    return PyInjectionUtil.registerStringLiteralInjection(element, registrar, language);
                }
            }
        }

        if (context instanceof PsiComment &&
            context instanceof PsiLanguageInjectionHost host &&
            context.getContainingFile() instanceof PyFile) {
            return registerCommentInjection(registrar, host);
        }
        return PyInjectionUtil.InjectionResult.EMPTY;
    }

    @Override
    @RequiredReadAction
    public @Nullable Language getInjectedLanguage(PsiElement context) {
        if (!(context instanceof PyStringLiteralExpression stringLiteral)) {
            return null;
        }

        // Walk up the PSI tree to find the innermost decisive type boundary.
        // Inner boundaries (e.g. call expression) shadow outer ones.
        TypeAnnotationPosition position = classifyAnnotationPosition(stringLiteral);
        if (position == TypeAnnotationPosition.TYPE_FORM) {
            return PyTypeHintDialect.INSTANCE;
        }
        if (position == TypeAnnotationPosition.NON_TYPE_FORM) {
            return null;
        }
        // no boundary found; fall through to secondary rules

        // bare annotation context — x: "T", def foo(x: Optional["T"]) -> "T": ...
        if (PsiTreeUtil.getParentOfType(stringLiteral, PyAnnotation.class, true, PyCallExpression.class) != null &&
            isTypingAnnotation(stringLiteral.getStringValue())) {
            return PyTypeHintDialect.INSTANCE;
        }
        if (isInsideValueOfExplicitTypeAnnotation(stringLiteral)) {
            return PyTypeHintDialect.INSTANCE;
        }
        if (isInsideValueOfTypeAliasStatement(stringLiteral) && isTypingAnnotation(stringLiteral.getStringValue())) {
            return PyTypeHintDialect.INSTANCE;
        }

        return null;
    }

    /**
     * Whether a string literal is in a TypeForm (type annotation) position or a NonTypeForm position.
     * Null means no decisive ancestor was found.
     */
    private enum TypeAnnotationPosition {
        TYPE_FORM,
        NON_TYPE_FORM
    }

    /**
     * Walks up the PSI tree from {@code literal} and returns the innermost explicit TypeForm/NonTypeForm
     * boundary decision, or null if no decisive boundary is found.
     * <p>
     * Decisive boundaries are constructs that explicitly route their sub-expressions into type or
     * non-type positions: {@code Annotated}, {@code Literal}, {@code cast}, {@code TypeVar}, {@code NewType}, {@code assert_type},
     * class base lists, and PEP 695 {@code PyTypeParameter} bounds.
     * <p>
     * All other expressions (generic subscriptions like {@code List[...]}, attribute access, etc.) are
     * transparent: they pass through the context from their parent.
     */
    @RequiredReadAction
    private static @Nullable TypeAnnotationPosition classifyAnnotationPosition(PyStringLiteralExpression literal) {
        for (PsiElement ancestor = literal.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            TypeAnnotationPosition position = classifyFromAncestor(literal, ancestor);
            if (position != null) {
                return position;
            }
        }
        return null;
    }

    /**
     * Returns the {@link TypeAnnotationPosition} that {@code ancestor} establishes for {@code element}, or null if
     * {@code ancestor} is transparent and does not route type vs. non-type positions.
     */
    @RequiredReadAction
    private static @Nullable TypeAnnotationPosition classifyFromAncestor(PsiElement element, PsiElement ancestor) {
        if (ancestor instanceof PySubscriptionExpression subscription) {
            return classifySubscriptionPosition(element, subscription);
        }
        // Class bases are type positions: `class A(list["int"]): ...`
        // But strings nested inside non-type-routing calls are not: `class A(namedtuple('C', 'x y')): ...`
        if (ancestor instanceof PyArgumentList) {
            if (!(ancestor.getParent() instanceof PyClass)) {
                return null;
            }
            for (PsiElement it = element.getParent(); it != null && it != ancestor; it = it.getParent()) {
                if (it instanceof PyKeywordArgument || it instanceof PyCallExpression) {
                    return TypeAnnotationPosition.NON_TYPE_FORM;
                }
            }
            return TypeAnnotationPosition.TYPE_FORM;
        }
        // PEP 695 new-style TypeVar bound: `type X[T: "bound"] = ...`, `def foo[T: "bound"](): ...`
        if (ancestor instanceof PyTypeParameter) {
            return TypeAnnotationPosition.TYPE_FORM;
        }
        if (ancestor instanceof PyCallExpression call) {
            return classifyCallSitePosition(element, call);
        }
        return null;
    }

    @RequiredReadAction
    private static @Nullable TypeAnnotationPosition classifySubscriptionPosition(PsiElement element, PySubscriptionExpression subscription) {
        if (!(subscription.getQualifier() instanceof PyReferenceExpression qualifier)) {
            return null;
        }
        List<String> resolved = resolveLocally(qualifier);
        // Annotated[T, meta...]: T is TypeForm, all metadata args are NonTypeForm
        if (resolved.contains(PyTypingTypeProvider.ANNOTATED) || resolved.contains(PyTypingTypeProvider.ANNOTATED_EXT)) {
            PyExpression index = subscription.getIndexExpression();
            if (index == null) {
                return TypeAnnotationPosition.NON_TYPE_FORM;
            }
            if (!(index instanceof PyTupleExpression tuple)) {
                return TypeAnnotationPosition.TYPE_FORM; // single arg — it must be the type arg
            }
            PyExpression[] elements = tuple.getElements();
            if (elements.length == 0) {
                return TypeAnnotationPosition.NON_TYPE_FORM;
            }
            PyExpression firstArg = elements[0];
            return PsiTreeUtil.isAncestor(firstArg, element, false) ? TypeAnnotationPosition.TYPE_FORM : TypeAnnotationPosition.NON_TYPE_FORM;
        }
        // Literal[v, ...]: no argument is a type
        if (resolved.contains(PyTypingTypeProvider.LITERAL) || resolved.contains(PyTypingTypeProvider.LITERAL_EXT)) {
            return TypeAnnotationPosition.NON_TYPE_FORM;
        }
        // Other subscriptions (List[...], Optional[...], Union[...], etc.) are transparent
        return null;
    }

    @RequiredReadAction
    private static @Nullable TypeAnnotationPosition classifyCallSitePosition(PsiElement element, PyCallExpression call) {
        if (!(call.getCallee() instanceof PyReferenceExpression callee)) {
            return null;
        }
        List<String> resolved = resolveLocally(callee);
        PyArgumentList argList = call.getArgumentList();
        if (argList == null) {
            return null;
        }

        // cast(T, v) — first positional arg (or `typ=`) is TypeForm
        if (resolved.contains(PyTypingTypeProvider.CAST) || resolved.contains(PyTypingTypeProvider.CAST_EXT)) {
            PyExpression typeArg = argList.getKeywordArgument("typ");
            if (typeArg == null) {
                PyExpression[] arguments = argList.getArguments();
                typeArg = arguments.length > 0 ? arguments[0] : null;
            }
            return typeArg != null && PsiTreeUtil.isAncestor(typeArg, element, false)
                ? TypeAnnotationPosition.TYPE_FORM
                : TypeAnnotationPosition.NON_TYPE_FORM;
        }

        // TypeVar(name, *constraints, bound=T, default=T) — name is NonTypeForm, rest are TypeForm
        if (resolved.contains(PyTypingTypeProvider.TYPE_VAR) || resolved.contains(PyTypingTypeProvider.TYPE_VAR_EXT)) {
            PyExpression[] args = argList.getArguments();
            // First positional arg is the TypeVar name — not a type
            if (args.length > 0 && !(args[0] instanceof PyKeywordArgument) &&
                PsiTreeUtil.isAncestor(args[0], element, false)) {
                return TypeAnnotationPosition.NON_TYPE_FORM;
            }
            // Remaining positional args are constraints
            for (int i = 1; i < args.length; i++) {
                PyExpression arg = args[i];
                if (arg instanceof PyKeywordArgument) {
                    break;
                }
                if (PsiTreeUtil.isAncestor(arg, element, false)) {
                    return TypeAnnotationPosition.TYPE_FORM;
                }
            }
            // Keyword args `bound` and `default` (PEP 696) are types
            for (String kw : List.of("bound", "default")) {
                PyKeywordArgument keywordArgument = argList.getKeywordArgument(kw);
                PyExpression value = keywordArgument != null ? keywordArgument.getValueExpression() : null;
                if (value != null && PsiTreeUtil.isAncestor(value, element, false)) {
                    return TypeAnnotationPosition.TYPE_FORM;
                }
            }
            return TypeAnnotationPosition.NON_TYPE_FORM;
        }

        // NewType(name, T) — second positional arg is TypeForm
        if (resolved.contains(PyTypingTypeProvider.NEW_TYPE)) {
            PyExpression[] args = argList.getArguments();
            return args.length >= 2 && PsiTreeUtil.isAncestor(args[1], element, false)
                ? TypeAnnotationPosition.TYPE_FORM
                : TypeAnnotationPosition.NON_TYPE_FORM;
        }

        // assert_type(v, T) — second positional arg is TypeForm
        if (resolved.contains(PyTypingTypeProvider.ASSERT_TYPE)) {
            PyExpression[] args = argList.getArguments();
            return args.length >= 2 && PsiTreeUtil.isAncestor(args[1], element, false)
                ? TypeAnnotationPosition.TYPE_FORM
                : TypeAnnotationPosition.NON_TYPE_FORM;
        }

        // TypeForm(T) — the single positional argument is a type expression (PEP 747)
        if (resolved.contains(PyTypingTypeProvider.TYPE_FORM) || resolved.contains(PyTypingTypeProvider.TYPE_FORM_EXT)) {
            PyExpression[] args = argList.getArguments();
            PyExpression firstArg = args.length > 0 ? args[0] : null;
            return firstArg != null && PsiTreeUtil.isAncestor(firstArg, element, false)
                ? TypeAnnotationPosition.TYPE_FORM
                : TypeAnnotationPosition.NON_TYPE_FORM;
        }

        // For unknown calls, strings nested inside generic subscriptions are type arguments:
        // `x: call(set['str'])` — 'str' is the type arg of set[...] and should inject.
        // But only when the call itself is in an annotation context — otherwise something like
        // `print(A["foo"])` would incorrectly inject.
        boolean insideSubscription = false;
        for (PsiElement it = element.getParent(); it != null && it != call; it = it.getParent()) {
            if (it instanceof PySubscriptionExpression) {
                insideSubscription = true;
                break;
            }
        }
        if (insideSubscription && PsiTreeUtil.getParentOfType(call, PyAnnotation.class) != null) {
            return TypeAnnotationPosition.TYPE_FORM;
        }
        return null;
    }

    @RequiredReadAction
    private static boolean isInsideValueOfExplicitTypeAnnotation(PyStringLiteralExpression literal) {
        PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(literal, PyAssignmentStatement.class);
        if (assignment == null || !PsiTreeUtil.isAncestor(assignment.getAssignedValue(), literal, false)) {
            return false;
        }
        return PyTypingTypeProvider.isExplicitTypeAlias(assignment, TypeEvalContext.codeAnalysis(literal.getProject(), literal.getContainingFile()));
    }

    /**
     * Whether the string is the value of a {@code type} statement, or is nested in it only through the type expression syntax,
     * such as {@code type X = list["dict[str, T]"] | None}.
     */
    private static boolean isInsideValueOfTypeAliasStatement(PyStringLiteralExpression literal) {
        for (PsiElement ancestor = literal.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof PyTypeAliasStatement typeAliasStatement) {
                PyExpression typeExpression = typeAliasStatement.getTypeExpression();
                return typeExpression != null && PsiTreeUtil.isAncestor(typeExpression, literal, false);
            }
            else if (ancestor instanceof PySubscriptionExpression ||
                ancestor instanceof PyTupleExpression ||
                ancestor instanceof PyListLiteralExpression ||
                ancestor instanceof PyParenthesizedExpression ||
                ancestor instanceof PyBinaryExpression) {
                continue;
            }
            else {
                return false;
            }
        }
        return false;
    }

    @RequiredReadAction
    private static PyInjectionUtil.InjectionResult registerCommentInjection(MultiHostRegistrar registrar, PsiLanguageInjectionHost host) {
        String text = host.getText();
        String annotationText = PyTypingTypeProvider.getTypeCommentValue(text);
        if (annotationText != null) {
            Language language;
            if (PyTypingTypeProvider.TYPE_IGNORE_PATTERN.matcher(text).matches() ||
                PyTypingTypeProvider.TYPE_IGNORE_UNCLOSED_PATTERN.matcher(text).matches()) {
                language = null;
            }
            else if (isFunctionTypeComment(host)) {
                language = PyFunctionTypeAnnotationDialect.INSTANCE;
            }
            else {
                language = PyTypeHintDialect.INSTANCE;
            }
            if (language != null) {
                registrar.startInjecting(language);
                TextRange range = Objects.requireNonNull(PyTypingTypeProvider.getTypeCommentValueRange(text));
                registrar.addPlace("", "", host, range);
                registrar.doneInjecting();
                return new PyInjectionUtil.InjectionResult(true, true);
            }
        }
        return PyInjectionUtil.InjectionResult.EMPTY;
    }

    private static List<String> resolveLocally(PyReferenceExpression expression) {
        return PyResolveUtil.resolveImportedElementQNameLocally(expression).stream().map(Object::toString).toList();
    }

    private static boolean isFunctionTypeComment(PsiElement element) {
        PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        return function != null && function.getTypeComment() == element;
    }

    private static boolean isTypingAnnotation(String s) {
        return PyTypingAliasStubType.RE_TYPE_HINT_LIKE_STRING.matcher(s).matches();
    }
}
