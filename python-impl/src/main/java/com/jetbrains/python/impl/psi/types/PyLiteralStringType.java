// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import consulo.application.util.registry.Registry;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static com.jetbrains.python.PyLanguageFacadeKt.getEffectiveLanguageLevel;

/**
 * Represents an arbitrary string literal
 *
 * @see <a href="https://peps.python.org/pep-0675/">PEP 675 - Arbitrary Literal String Type</a>
 */
public class PyLiteralStringType extends PyClassTypeImpl {
    private final PyClass cls;

    private PyLiteralStringType(PyClass cls) {
        super(cls, false);
        this.cls = cls;
    }

    public PyClass getCls() {
        return cls;
    }

    @Override
    public String getName() {
        return "LiteralString";
    }

    @Override
    public String toString() {
        return "PyLiteralStringType";
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }

        if (!(other instanceof PyLiteralStringType otherLiteralString)) {
            return false;
        }
        return cls.equals(otherLiteralString.cls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), cls);
    }

    @Override
    public <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyLiteralStringType(this);
        }
        return visitor.visitPyClassType(this);
    }

    public static @Nullable PyClassType create(PsiElement anchor) {
        PyClassType strType = PyBuiltinCache.getInstance(anchor).getStrType();
        if (Registry.is("python.type.hints.literal.string", true) && strType != null) {
            if (anchor instanceof PyElement && getEffectiveLanguageLevel(anchor.getContainingFile()).isPy3K()) {
                return new PyLiteralStringType(strType.getPyClass());
            }
        }
        return strType;
    }

    public static boolean match(PyLiteralStringType expected, PyClassType actual) {
        return actual instanceof PyLiteralStringType || actual instanceof PyLiteralType && expected.getPyClass().equals(actual.getPyClass());
    }
}
