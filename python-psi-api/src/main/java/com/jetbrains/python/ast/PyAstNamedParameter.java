/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.ast;

import com.jetbrains.python.ProtectionLevel;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.ParamHelperCore;
import com.jetbrains.python.ast.impl.PyUtilCore;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

/**
 * Represents a named parameter, as opposed to a tuple parameter.
 */
public interface PyAstNamedParameter extends PyAstParameter, PsiNamedElement, PsiNameIdentifierOwner, PyAstExpression, PyAstTypeCommentOwner,
                                             PyAstAnnotationOwner {
    @RequiredReadAction
    default @Nullable ASTNode getNameIdentifierNode() {
        return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
    }

    @Override
    @RequiredReadAction
    default @Nullable String getName() {
        ASTNode node = getNameIdentifierNode();
        return node != null ? node.getText() : null;
    }

    @Override
    @RequiredReadAction
    default int getTextOffset() {
        ASTNode node = getNameIdentifierNode();
        return node == null ? getNode().getStartOffset() : node.getTextRange().getStartOffset();
    }

    @Override
    @RequiredReadAction
    default @Nullable PsiElement getNameIdentifier() {
        ASTNode node = getNameIdentifierNode();
        return node == null ? null : node.getPsi();
    }

    @RequiredReadAction
    default boolean isPositionalContainer() {
        return getNode().findChildByType(PyTokenTypes.MULT) != null;
    }

    @RequiredReadAction
    default boolean isKeywordContainer() {
        return getNode().findChildByType(PyTokenTypes.EXP) != null;
    }

    @Override
    @RequiredReadAction
    default @Nullable PyAstExpression getDefaultValue() {
        ASTNode[] nodes = getNode().getChildren(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens());
        if (nodes.length > 0) {
            return (PyAstExpression)nodes[0].getPsi();
        }
        return null;
    }

    @Override
    @RequiredReadAction
    default boolean hasDefaultValue() {
        return getDefaultValue() != null;
    }

    @Override
    @RequiredReadAction
    default @Nullable String getDefaultValueText() {
        return ParamHelperCore.getDefaultValueText(getDefaultValue());
    }

    /**
     * @param includeDefaultValue if true, include the default value after an "=".
     * @return canonical representation of parameter.
     * Includes asterisks for *param and **param, and name.
     */
    @RequiredReadAction
    default String getRepr(boolean includeDefaultValue) {
        StringBuilder sb = new StringBuilder();

        sb.append(ParamHelperCore.getNameInSignature(this));

        if (includeDefaultValue) {
            sb.append(ObjectUtil.notNull(ParamHelperCore.getDefaultValuePartInSignature(getDefaultValueText(), false), ""));
        }

        return sb.toString();
    }

    @Override
    default PyAstNamedParameter getAsNamed() {
        return this;
    }

    @Override
    default @Nullable PyAstTupleParameter getAsTuple() {
        return null; // we're not a tuple
    }

    @Override
    @Nullable PyAstAnnotation getAnnotation();
    /**
     * Parameter is considered "keyword-only" if it appears after named or unnamed positional vararg parameter.
     * See PEP-3102 for more details.
     *
     * @return whether this parameter is keyword-only
     */
    @RequiredReadAction
    default boolean isKeywordOnly() {
        PyAstParameterList parameters = getStubOrPsiParentOfType(PyAstParameterList.class);
        if (parameters == null) {
            return false;
        }
        boolean varargSeen = false;
        for (PyAstParameter param : parameters.getParameters()) {
            if (param == this) {
                break;
            }
            PyAstNamedParameter named = param.getAsNamed();
            if ((named != null && named.isPositionalContainer()) || param instanceof PyAstSingleStarParameter) {
                varargSeen = true;
                break;
            }
        }
        return varargSeen;
    }

    @RequiredReadAction
    default boolean isPositionalOnly() {
        if (isSelf() ||
            isKeywordContainer() ||
                isPositionalContainer() ||
                this instanceof PyAstSingleStarParameter ||
                this instanceof PyAstSlashParameter) {
            return false;
        }
        PyAstParameterList parameters = getStubOrPsiParentOfType(PyAstParameterList.class);
        if (parameters == null) {
            return false;
        }
        boolean thisSeen = false;
        boolean allBeforeThisPrivate = true;
        for (PyAstParameter param : parameters.getParameters()) {
            if (param.isSelf()) {
                continue;
            }
            if (param instanceof PyAstSingleStarParameter ||
                (param instanceof PyAstNamedParameter np && (np.isPositionalContainer() || np.isKeywordContainer()))) {
                // None of the positional-only parameter kinds can follow these
                if (!thisSeen) {
                    return false;
                }
                // No need to continue, as `/` can't follow them either
                break;
            }
            // New-style positional-only syntax, decide purely by the position
            if (param instanceof PyAstSlashParameter) {
                return thisSeen;
            }
            if (param == this) {
                thisSeen = true;
            }
            else if (!thisSeen) {
                allBeforeThisPrivate &= param.getName() != null && param.getProtectionLevel() == ProtectionLevel.PRIVATE;
            }
        }
        // Legacy-style positional only
        return thisSeen && allBeforeThisPrivate && getName() != null && getProtectionLevel() == ProtectionLevel.PRIVATE;
    }

    @Override
    @RequiredReadAction
    default boolean isSelf() {
        if (isPositionalContainer() || isKeywordContainer()) {
            return false;
        }
        PyAstFunction function = getStubOrPsiParentOfType(PyAstFunction.class);
        if (function == null) {
            return false;
        }
        PyAstClass cls = function.getContainingClass();
        PyAstParameter[] parameters = function.getParameterList().getParameters();
        if (cls != null && parameters.length > 0 && parameters[0] == this) {
            if (PyUtilCore.isNewMethod(function)) {
                return true;
            }
            PyAstFunction.Modifier modifier = function.getModifier();
            if (modifier != PyAstFunction.Modifier.STATICMETHOD) {
                return true;
            }
        }
        return false;
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyNamedParameter(this);
    }

    @Override
    @RequiredReadAction
    default @Nullable PsiComment getTypeComment() {
        for (PsiElement next = getNextSibling(); next != null; next = next.getNextSibling()) {
            if (next.textContains('\n')) {
                break;
            }
            if (!(next instanceof PsiWhiteSpace)) {
                if (",".equals(next.getText())) {
                    continue;
                }
                if (next instanceof PsiComment && PyUtilCore.getTypeCommentValue(next.getText()) != null) {
                    return (PsiComment)next;
                }
                break;
            }
        }
        return null;
    }
}
