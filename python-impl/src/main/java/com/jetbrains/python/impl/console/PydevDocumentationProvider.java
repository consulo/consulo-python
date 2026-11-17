// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.PythonRuntimeService;
import com.jetbrains.python.impl.console.completion.PydevConsoleElement;
import com.jetbrains.python.impl.console.completion.PydevConsoleReference;
import com.jetbrains.python.impl.documentation.PyDocumentationBuilder;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.editor.documentation.AbstractDocumentationProvider;
import consulo.language.editor.documentation.LanguageDocumentationProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PydevDocumentationProvider extends AbstractDocumentationProvider implements LanguageDocumentationProvider {

    @Override
    public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
        if (object instanceof PydevConsoleElement) {
            return (PydevConsoleElement) object;
        }
        return super.getDocumentationElementForLookupItem(psiManager, object, element);
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        // Process PydevConsoleElement case
        if (element instanceof PydevConsoleElement pydevElement) {
            return PydevConsoleElement.generateDoc(pydevElement).toString();
        }
        return null;
    }

    @RequiredReadAction
    public static @Nullable String createDoc(PsiElement element, PsiElement originalElement) {
        PyReferenceExpression expression = PsiTreeUtil.getNonStrictParentOfType(originalElement, PyReferenceExpression.class);
        if (expression == null) {
            expression = PsiTreeUtil.getNonStrictParentOfType(element, PyReferenceExpression.class);
            if (expression == null) {
                return null;
            }
        }
        // Indicates that we are inside console, not a lookup element!
        PydevConsoleReference consoleRef = PyUtil.as(expression.getReference(), PydevConsoleReference.class);
        if (consoleRef == null) { //shouldn't really happen!
            return null;
        }
        PyElement documentationElement = consoleRef.getDocumentationElement();
        if (documentationElement == null) {
            return null;
        }

        return new PyDocumentationBuilder(documentationElement, null).build();
    }

    @Override
    @RequiredReadAction
    public @Nullable PsiElement getCustomDocumentationElement(
        Editor editor,
        PsiFile file,
        @Nullable PsiElement contextElement,
        int targetOffset
    ) {
        if (contextElement != null && PythonRuntimeService.getInstance().isInPydevConsole(contextElement)) {
            IElementType elementType = contextElement.getNode().getElementType();
            if (PythonDialectsTokenSetProvider.getInstance().getKeywordTokens().contains(elementType)) {
                return contextElement;
            }
            PsiElement parent = contextElement.getParent();
            if (parent instanceof PyArgumentList && (PyTokenTypes.LPAR == elementType || PyTokenTypes.RPAR == elementType)) {
                PyCallExpression expression = PsiTreeUtil.getParentOfType(contextElement, PyCallExpression.class);
                if (expression != null) {
                    return expression.getCallee();
                }
            }
        }
        return null;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
