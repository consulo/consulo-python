// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.ContributedReferenceHost;
import consulo.language.psi.FileContextUtil;
import consulo.language.psi.PsiLiteralValue;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceService;
import consulo.language.psi.ReferenceProvidersRegistry;
import consulo.localize.LocalizeValue;
import consulo.navigation.ItemPresentation;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class PyStringLiteralExpressionImpl extends PyElementImpl
    implements PyStringLiteralExpression, PsiLiteralValue, ContributedReferenceHost {

    private volatile @Nullable String myStringValue;
    private volatile @Nullable List<TextRange> myValueTextRanges;
    private volatile @Nullable List<Pair<TextRange, String>> myDecodedFragments;

    public PyStringLiteralExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyStringLiteralExpression(this);
    }

    @Override
    public void subtreeChanged() {
        super.subtreeChanged();
        myStringValue = null;
        myValueTextRanges = null;
        myDecodedFragments = null;
    }

    @Override
    @RequiredReadAction
    public List<TextRange> getStringValueTextRanges() {
        List<TextRange> result = myValueTextRanges;
        if (result == null) {
            myValueTextRanges = result = PyStringLiteralExpression.super.getStringValueTextRanges();
        }
        return result;
    }

    // TODO consulo: no JB counterpart (removed upstream in favor of PyStringElement#getContentRange()), kept for existing Consulo callers
    public static TextRange getNodeTextRange(String text) {
        int startOffset = getPrefixLength(text);
        int delimiterLength = 1;
        String afterPrefix = text.substring(startOffset);
        if (afterPrefix.startsWith("\"\"\"") || afterPrefix.startsWith("'''")) {
            delimiterLength = 3;
        }
        String delimiter = text.substring(startOffset, startOffset + delimiterLength);
        startOffset += delimiterLength;
        int endOffset = text.length();
        if (text.substring(startOffset).endsWith(delimiter)) {
            endOffset -= delimiterLength;
        }
        return new TextRange(startOffset, endOffset);
    }

    // TODO consulo: no JB counterpart (removed upstream in favor of PyStringLiteralUtil#getPrefixLength()), kept for existing Consulo callers
    public static int getPrefixLength(String text) {
        return PyStringLiteralCoreUtil.getPrefixEndOffset(text, 0);
    }

    @Override
    @RequiredReadAction
    public List<Pair<TextRange, String>> getDecodedFragments() {
        List<Pair<TextRange, String>> result = myDecodedFragments;
        if (result == null) {
            myDecodedFragments = result = PyStringLiteralExpression.super.getDecodedFragments();
        }
        return result;
    }

    @Override
    @RequiredReadAction
    public boolean isInterpolated() {
        return getStringElements().stream()
            .filter(PyFormattedStringElement.class::isInstance)
            .map(PyFormattedStringElement.class::cast)
            .anyMatch(element -> !element.getFragments().isEmpty());
    }

    @Override
    @RequiredReadAction
    public String getStringValue() {
        //ASTNode child = getNode().getFirstChildNode();
        //assert child != null;
        String result = myStringValue;
        if (result == null) {
            myStringValue = result = PyStringLiteralExpression.super.getStringValue();
        }
        return result;
    }

    @Override
    @RequiredReadAction
    public @Nullable Object getValue() {
        return getStringValue();
    }

    @Override
    @RequiredReadAction
    public String toString() {
        return super.toString() + ": " + getStringValue();
    }

    @Override
    @RequiredReadAction
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        // TODO consulo: JB returns PyAnyType.getUnknown() when the type is null (the project has no builtins), PyAnyType is not ported yet
        return getBuiltinOrLiteralType();
    }

    @RequiredReadAction
    private @Nullable PyType getBuiltinOrLiteralType() {
        PyFile file = PyUtil.as(FileContextUtil.getContextFile(this), PyFile.class);
        PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(file == null ? this : file);
        LanguageLevel languageLevel = file == null ? LanguageLevel.forElement(this) : file.getLanguageLevel();

        ASTNode firstNode = ContainerUtil.getFirstItem(getStringNodes());
        if (firstNode != null) {
            if (firstNode.getElementType() == PyElementTypes.FSTRING_NODE) {
                String prefix = PyStringLiteralCoreUtil.getPrefix(firstNode.getText());
                if (PyStringLiteralUtil.isTemplatePrefix(prefix)) {
                    if (languageLevel.isOlderThan(LanguageLevel.PYTHON314)) {
                        return builtinCache.getStrType();
                    }
                    PyClassType templateClassType = getTemplateClassType();
                    if (templateClassType != null) {
                        return templateClassType;
                    }
                }
                // f-strings can't have "b" prefix, so they are always unicode
                return builtinCache.getUnicodeType(languageLevel);
            }
            else if (firstNode.getElementType() == PyTokenTypes.DOCSTRING) {
                return builtinCache.getStrType();
            }
            else if (((PyStringElement) firstNode).isBytes()) {
                return builtinCache.getBytesType(languageLevel);
            }

            IElementType type = PythonHighlightingLexer.convertStringType(
                firstNode.getElementType(),
                firstNode.getText(),
                languageLevel,
                (file != null && file.hasImportFromFuture(FutureFeature.UNICODE_LITERALS))
            );
            if (PyTokenTypes.UNICODE_NODES.contains(type)) {
                // TODO consulo: JB returns PyLiteralType.stringLiteral(this, getStringValue()) for Python 3
                //  when PyLiteralType.inferLiteralTypeForLiteralExpressions() is on, PyLiteralType is not ported yet
                return builtinCache.getUnicodeType(languageLevel);
            }
        }
        return builtinCache.getStrType();
    }

    private @Nullable PyClassType getTemplateClassType() {
        PyPsiFacade facade = PyPsiFacade.getInstance(getProject());
        PyClass templateClass = facade.createClassByQName(PyNames.TEMPLATELIB_TEMPLATE, getContainingFile());
        if (templateClass != null) {
            return facade.createClassType(templateClass, false);
        }
        return null;
    }

    @Override
    public final PsiReference[] getReferences() {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
    }

    @Override
    public @Nullable PsiReference getReference() {
        return ArrayUtil.getFirstElement(getReferences());
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            @RequiredReadAction
            public LocalizeValue getPresentableText() {
                return LocalizeValue.of(getStringValue());
            }

            @Override
            public @Nullable String getLocationString() {
                String packageForFile = PyElementPresentation.getPackageForFile(getContainingFile());
                return packageForFile != null ? String.format("(%s)", packageForFile) : null;
            }

            @Override
            public Image getIcon() {
                return PlatformIconGroup.nodesVariable();
            }
        };
    }

    @Override
    @RequiredReadAction
    public int valueOffsetToTextOffset(int valueOffset) {
        return createLiteralTextEscaper().getOffsetInHost(valueOffset, getStringValueTextRange());
    }
}
