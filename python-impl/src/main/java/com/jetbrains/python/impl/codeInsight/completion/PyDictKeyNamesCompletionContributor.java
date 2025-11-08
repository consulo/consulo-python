/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * Complete known keys for dictionaries
 *
 * @author catherine
 */
@ExtensionImpl
public class PyDictKeyNamesCompletionContributor extends CompletionContributor {
    public PyDictKeyNamesCompletionContributor() {
        extend(
            CompletionType.BASIC,
            psiElement().inside(PySubscriptionExpression.class),
            (parameters, context, result) -> {
                PsiElement original = parameters.getOriginalPosition();
                int offset = parameters.getOffset();
                if (original == null) {
                    return;
                }
                CompletionResultSet dictCompletion = createResult(original, result, offset);

                PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(original, PySubscriptionExpression.class);
                if (subscription == null) {
                    return;
                }
                PsiElement operand = subscription.getOperand();
                PsiReference reference = operand.getReference();
                if (reference != null && reference.resolve() instanceof PyTargetExpression targetExpr) {
                    PyDictLiteralExpression dict = PsiTreeUtil.getNextSiblingOfType(targetExpr, PyDictLiteralExpression.class);
                    if (dict != null) {
                        addDictLiteralKeys(dict, dictCompletion);
                        PsiFile file = parameters.getOriginalFile();
                        addAdditionalKeys(file, operand, dictCompletion);
                    }
                    PyCallExpression dictConstructor = PsiTreeUtil.getNextSiblingOfType(targetExpr, PyCallExpression.class);
                    if (dictConstructor != null) {
                        addDictConstructorKeys(dictConstructor, dictCompletion);
                        PsiFile file = parameters.getOriginalFile();
                        addAdditionalKeys(file, operand, dictCompletion);
                    }
                }
            }
        );
    }

    /**
     * create completion result with prefix matcher if needed
     *
     * @param original is original element
     * @param result   is initial completion result
     * @param offset
     * @return
     */
    @RequiredReadAction
    private static CompletionResultSet createResult(@Nonnull PsiElement original, @Nonnull CompletionResultSet result, int offset) {
        PyStringLiteralExpression prevElement = PsiTreeUtil.getPrevSiblingOfType(original, PyStringLiteralExpression.class);
        if (prevElement != null) {
            ASTNode prevNode = prevElement.getNode();
            if (prevNode != null) {
                if (prevNode.getElementType() != PyTokenTypes.LBRACKET) {
                    return result.withPrefixMatcher(findPrefix(prevElement, offset));
                }
            }
        }
        if (original.getParent() instanceof PyStringLiteralExpression stringLiteralExpr) {
            return result.withPrefixMatcher(findPrefix(stringLiteralExpr, offset));
        }
        PyNumericLiteralExpression number =
            PsiTreeUtil.findElementOfClassAtOffset(original.getContainingFile(), offset - 1, PyNumericLiteralExpression.class, false);
        if (number != null) {
            return result.withPrefixMatcher(findPrefix(number, offset));
        }
        return result;
    }

    /**
     * finds prefix. For *'str'* returns just *'str*.
     *
     * @param element to find prefix of
     * @return prefix
     */
    @RequiredReadAction
    private static String findPrefix(PyElement element, int offset) {
        return TextRange.create(element.getTextRange().getStartOffset(), offset).substring(element.getContainingFile().getText());
    }

    /**
     * add keys to completion result from dict constructor
     */
    @RequiredReadAction
    private static void addDictConstructorKeys(PyCallExpression dictConstructor, CompletionResultSet result) {
        PyExpression callee = dictConstructor.getCallee();
        if (callee == null) {
            return;
        }
        String name = callee.getText();
        if ("dict".equals(name)) {
            TypeEvalContext context = TypeEvalContext.codeCompletion(callee.getProject(), callee.getContainingFile());
            PyType type = context.getType(dictConstructor);
            if (type != null && type.isBuiltin()) {
                PyArgumentList list = dictConstructor.getArgumentList();
                if (list == null) {
                    return;
                }
                PyExpression[] argumentList = list.getArguments();
                for (PyExpression argument : argumentList) {
                    if (argument instanceof PyKeywordArgument) {
                        result.addElement(createElement("'" + ((PyKeywordArgument) argument).getKeyword() + "'"));
                    }
                }
            }
        }
    }

    /**
     * add keys from assignment statements
     * For instance, dictionary['b']=b
     *
     * @param file    to get additional keys
     * @param operand is operand of origin element
     * @param result  is completion result set
     */
    @RequiredReadAction
    private static void addAdditionalKeys(PsiFile file, PsiElement operand, CompletionResultSet result) {
        PySubscriptionExpression[] subscriptionExpressions = PyUtil.getAllChildrenOfType(file, PySubscriptionExpression.class);
        for (PySubscriptionExpression expr : subscriptionExpressions) {
            if (expr.getOperand().getText().equals(operand.getText())
                && expr.getParent() instanceof PyAssignmentStatement assignment
                && expr.equals(assignment.getLeftHandSideExpression())) {
                PyExpression key = expr.getIndexExpression();
                if (key != null) {
                    boolean addHandler = PsiTreeUtil.findElementOfClassAtRange(
                        file,
                        key.getTextRange().getStartOffset(),
                        key.getTextRange().getEndOffset(),
                        PyStringLiteralExpression.class
                    ) != null;
                    result.addElement(createElement(key.getText(), addHandler));
                }
            }
        }
    }

    /**
     * add keys from dict literal expression
     */
    @RequiredReadAction
    public static void addDictLiteralKeys(PyDictLiteralExpression dict, CompletionResultSet result) {
        PyKeyValueExpression[] keyValues = dict.getElements();
        for (PyKeyValueExpression expression : keyValues) {
            boolean addHandler = PsiTreeUtil.findElementOfClassAtRange(
                dict.getContainingFile(),
                expression.getTextRange().getStartOffset(),
                expression.getTextRange().getEndOffset(),
                PyStringLiteralExpression.class
            ) != null;
            result.addElement(createElement(expression.getKey().getText(), addHandler));
        }
    }

    private static LookupElementBuilder createElement(String key) {
        return createElement(key, true);
    }

    private static LookupElementBuilder createElement(String key, boolean addHandler) {
        LookupElementBuilder item;
        item = LookupElementBuilder.create(key).withTypeText("dict key").withIcon(PlatformIconGroup.nodesParameter());

        if (addHandler) {
            item = item.withInsertHandler((context, item1) -> {
                PyStringLiteralExpression str = PsiTreeUtil.findElementOfClassAtOffset(
                    context.getFile(),
                    context.getStartOffset(),
                    PyStringLiteralExpression.class,
                    false
                );
                if (str != null) {
                    boolean isDictKeys = PsiTreeUtil.getParentOfType(str, PySubscriptionExpression.class) != null;
                    if (isDictKeys) {
                        int off = context.getStartOffset() + str.getTextLength();
                        PsiElement element = context.getFile().findElementAt(off);
                        boolean atRBrace = element == null || element.getNode().getElementType() == PyTokenTypes.RBRACKET;
                        String text = str.getText();
                        boolean badQuoting = !(StringUtil.startsWithChar(text, '\'') && StringUtil.endsWithChar(text, '\''))
                            && !(StringUtil.startsWithChar(text, '"') && StringUtil.endsWithChar(text, '"'));
                        if (badQuoting || !atRBrace) {
                            Document document = context.getEditor().getDocument();
                            int offset = context.getTailOffset();
                            document.deleteString(offset - 1, offset);
                        }
                    }
                }
            });
        }
        return item;
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
