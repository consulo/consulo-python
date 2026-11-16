// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi;

import com.jetbrains.python.PyTokenTypes;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;
import static com.jetbrains.python.ast.PyAstElementKt.findChildByType;

/**
 * Represents a replacement field that can appear either inside an f-string literal itself or inside
 * a format specifier of another replacement field, e.g. {@code {  expr  !s:{width}}} and {@code {width}}
 * parts of the f-string {@code f'{  expr  !s:{width}}'}.
 * <p>
 * Note that opening and closing braces are represented with dedicated
 * {@link com.jetbrains.python.PyTokenTypes#FSTRING_FRAGMENT_START} and {@link com.jetbrains.python.PyTokenTypes#FSTRING_FRAGMENT_END}
 * tokens instead of regular types for curly braces.
 */
// TODO consulo: JB extends com.jetbrains.python.ast.PyAstFStringFragment (python-ast layer not ported),
//  its default methods are placed here
public interface PyFStringFragment extends PyElement {

    /**
     * Returns the primary expression of this fragment, i.e. the one that appears after the opening brace and
     * before a type conversion, a format specifier or a closing curly brace. Nested expressions inside
     * fragments of format specifier can be accessed as primary expressions of nested fragments retrieved with
     * {@code getFormatPart().getFragments()}.
     */
    default @Nullable PyExpression getExpression() {
        return findChildByClass(this, PyExpression.class);
    }

    /**
     * Returns a text range that covers the primary expression together with any preceding and trailing whitespaces.
     * <p>
     * For instance, for the fragment {@code {  expr  !s:{width}}} it's {@code (1, 9)}, though the range {@link #getExpression()}
     * covers only {@code (3, 7)}.
     */
    default TextRange getExpressionContentRange() {
        PsiElement endAnchor = ObjectUtil.coalesce(getTypeConversion(), getFormatPart(), getClosingBrace());
        return TextRange.create(1, endAnchor != null ? endAnchor.getStartOffsetInParent() : getTextLength());
    }

    /**
     * Returns an optional type conversion part of a replacement field, presumably either "!r", "!s" or "!a".
     * <p>
     * For instance, for the fragment {@code {  expr  !s:{width}}} it's {@code !s}.
     */
    default @Nullable PsiElement getTypeConversion() {
        return findChildByType(this, PyTokenTypes.FSTRING_FRAGMENT_TYPE_CONVERSION);
    }

    /**
     * Returns an optional format specifier part of a replacement field. It always starts with a colon and spans up to
     * the closing brace of the fragment itself.
     * <p>
     * For instance, for the fragment {@code {  expr  !s:{width}}} it's {@code !s:{width}}.
     */
    default @Nullable PyFStringFragmentFormatPart getFormatPart() {
        return findChildByClass(this, PyFStringFragmentFormatPart.class);
    }

    default @Nullable PsiElement getClosingBrace() {
        return findChildByType(this, PyTokenTypes.RBRACE);
    }
}
