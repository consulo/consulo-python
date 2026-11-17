// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.util.registry.Registry;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.logging.Logger;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * represents `typing.Any` and `Unknown`/untyped
 */
public abstract sealed class PyAnyType implements PyType permits PyAnyType.Any, PyAnyType.Unknown {
    private static final Logger LOG = Logger.getInstance(PyAnyType.class);

    /**
     * The maximum number of characters of user code that one detail line can hold.
     */
    private static final int MAX_SNIPPET_LENGTH = 200;

    /**
     * The number of trailing characters that a shortened detail line keeps.
     */
    private static final int SNIPPET_SUFFIX_LENGTH = 40;

    /**
     * The single gate for `Any`/`Unknown` support. Read it through {@link #isEnabled()}.
     */
    public static final String REGISTRY_KEY = "python.type.any";

    public static final class Any extends PyAnyType {
        public static final Any INSTANCE = new Any();

        private Any() {
            super(PyNames.ANY_TYPE);
        }
    }

    public static final class Unknown extends PyAnyType {
        public static final Unknown INSTANCE = new Unknown();

        private Unknown() {
            super(PyNames.UNKNOWN_TYPE);
        }
    }

    private final String myName;

    private PyAnyType(String name) {
        myName = name;
    }

    @Override
    public String getName() {
        return myName;
    }

    @Override
    public List<RatedResolveResult> resolveMember(
        String name,
        @Nullable PyExpression location,
        AccessDirection direction,
        PyResolveContext resolveContext
    ) {
        return Collections.emptyList();
    }

    @Override
    public Object[] getCompletionVariants(@Nullable String completionPrefix, PsiElement location, ProcessingContext context) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public void assertValid(@Nullable String message) {
    }

    @Override
    public <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (this instanceof Any) {
            return visitor.visitAnyType();
        }
        return visitor.visitUnknownType();
    }

    @Override
    public String toString() {
        return myName;
    }

    public static boolean isEnabled() {
        return Registry.is(REGISTRY_KEY, true);
    }

    public static void validate(@Nullable PyType it) {
        validate(it, null);
    }

    /**
     * Guards the `Any`/`Unknown` invariant: with the support enabled a {@link PyType} must never be raw `null`, and
     * with the support disabled a {@link PyAnyType} must never leak out.
     * <p>
     * A violation fails the test in a unit test. In a running IDE it goes to the log as an error, which an EAP
     * build then reports to the exception analyzer. The user gets no notification, because the user can do
     * nothing about the problem. The check runs only in an EAP or an internal build.
     */
    public static void validate(@Nullable PyType it, @Nullable PsiElement context) {
        Application application = ApplicationManager.getApplication();
        if (application == null) {
            return;
        }
        boolean isTest = application.isUnitTestMode();
        // TODO consulo: Application.isEAP() missing
        if (!isTest && !application.isInternal()) {
            return;
        }

        String problem;
        if (isEnabled() && it == null) {
            problem = "A Python type was `null` while `Any`/`Unknown` support was enabled.";
        }
        else if (!isEnabled() && it instanceof PyAnyType) {
            problem = "A Python type was `PyAnyType` while `Any`/`Unknown` support was disabled.";
        }
        else {
            return;
        }

        // Build the context only for a real violation. It reads PSI text, which the hot path must not pay for.
        String[] details = describeContext(context);
        if (isTest) {
            throw new AssertionError(problem + "\n" + String.join("\n", details));
        }

        // `problem` stays the message, so that the exception analyzer groups the reports by the broken
        // invariant. The stack trace shows which inference path broke it, and the details show on what code.
        LOG.error(problem, new Throwable(problem), details);
    }

    public static @Nullable Any getAny() {
        return isEnabled() ? Any.INSTANCE : null;
    }

    public static @Nullable Unknown getUnknown() {
        return isEnabled() ? Unknown.INSTANCE : null;
    }

    /**
     * Describes the element whose inference broke the invariant, in enough detail to reproduce the problem.
     * A single element text is too little, because the element is often one reference.
     */
    private static String[] describeContext(@Nullable PsiElement context) {
        if (context == null) {
            return new String[]{"context: not available"};
        }
        if (!context.isValid()) {
            return new String[]{"context: an invalid " + context.getClass().getName()};
        }

        PsiFile file = context.getContainingFile();
        PyStatement statement = PsiTreeUtil.getParentOfType(context, PyStatement.class, false);
        PsiElement owner = PsiTreeUtil.getParentOfType(context, PyFunction.class, PyClass.class);
        String ownerName = owner instanceof PyQualifiedNameOwner qualifiedNameOwner ? qualifiedNameOwner.getQualifiedName() : null;
        return new String[]{
            "element      : " + context.getClass().getName() + " at " + (file != null ? file.getName() : null) + ":" +
                context.getTextOffset(),
            "psi path     : " + psiPath(context),
            "owner        : " + (ownerName != null ? ownerName : "<module level>"),
            "element text : " + abbreviate(context.getText()),
            "statement    : " + abbreviate(statement != null ? statement.getText() : null),
        };
    }

    private static String psiPath(PsiElement element) {
        List<String> names = new ArrayList<>();
        PsiElement current = element;
        while (current != null && !(current instanceof PsiFile) && names.size() < 12) {
            names.add(current.getClass().getSimpleName());
            current = current.getParent();
        }
        return String.join(" < ", names);
    }

    private static String abbreviate(@Nullable String text) {
        return text == null ? "<none>" : StringUtil.shortenTextWithEllipsis(text, MAX_SNIPPET_LENGTH, SNIPPET_SUFFIX_LENGTH, true);
    }
}
