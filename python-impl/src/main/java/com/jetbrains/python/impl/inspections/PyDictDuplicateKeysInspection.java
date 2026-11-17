// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.PyRemoveDictKeyQuickFix;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyLiteralExpression;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
  * User: catherine
  * <p>
  * Inspection to detect using the same value as dictionary key twice.
  */
@ExtensionImpl
public final class PyDictDuplicateKeysInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameDuplicateKeys();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {

        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyDictLiteralExpression(PyDictLiteralExpression node) {
            if (node.isEmpty()) {
                return;
            }

            MultiMap<String, PsiElement> keyValueAndKeys = new MultiMap<>();
            for (PyKeyValueExpression element : node.getElements()) {
                Pair<PsiElement, String> keyAndValue = getDictLiteralKey(element);
                if (keyAndValue != null) {
                    keyValueAndKeys.putValue(keyAndValue.second, keyAndValue.first);
                }
            }

            registerProblems(keyValueAndKeys, new PyRemoveDictKeyQuickFix());
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            if (!isDict(node)) {
                return;
            }

            MultiMap<String, PsiElement> keyValueAndKeys = new MultiMap<>();
            for (PyExpression argument : node.getArguments()) {
                argument = PyPsiUtils.flattenParens(argument);

                if (argument instanceof PySequenceExpression) {
                    for (PyExpression element : ((PySequenceExpression)argument).getElements()) {
                        Pair<PsiElement, String> keyAndValue = getDictCallKey(element);
                        if (keyAndValue != null) {
                            keyValueAndKeys.putValue(keyAndValue.second, keyAndValue.first);
                        }
                    }
                }
                else if (argument instanceof PyKeywordArgument) {
                    Pair<PsiElement, String> keyAndValue = getDictCallKey(argument);
                    if (keyAndValue != null) {
                        keyValueAndKeys.putValue(keyAndValue.second, keyAndValue.first);
                    }
                }
            }

            registerProblems(keyValueAndKeys);
        }

        private @Nullable Pair<PsiElement, String> getDictLiteralKey(PyKeyValueExpression argument) {
            PyExpression key = argument.getKey();
            String keyValue = getKeyValue(key);
            return keyValue != null ? Pair.createNonNull(key, keyValue) : null;
        }

        private @Nullable String getKeyValue(PsiElement node) {
            if (node instanceof PyStringLiteralExpression) {
                return wrapStringKey(((PyStringLiteralExpression)node).getStringValue());
            }

            if (node instanceof PyNumericLiteralExpression) {
                BigDecimal value = ((PyNumericLiteralExpression)node).getBigDecimalValue();
                String keyValue = value.toPlainString();
                return !value.equals(BigDecimal.ZERO) &&
                       myTypeEvalContext.getType((PyNumericLiteralExpression)node) == PyBuiltinCache.getInstance(node).getComplexType()
                       ? keyValue + "j"
                       : keyValue;
            }

            return node instanceof PyLiteralExpression || node instanceof PyReferenceExpression ? node.getText() : null;
        }

        private void registerProblems(MultiMap<String, PsiElement> keyValueAndKeys, LocalQuickFix ... quickFixes) {
            for (Map.Entry<String, Collection<PsiElement>> entry : keyValueAndKeys.entrySet()) {
                String keyValue = entry.getKey();
                Collection<PsiElement> keys = entry.getValue();

                if (keys.size() > 1) {
                    for (PsiElement key : keys) {
                        String unquotedKey = unwrapStringKey(keyValue);
                        registerProblem(key, PyInspectionMessages.problemMessage(a -> PyLocalize.inspDuplicateKeysDictionaryContainsDuplicateKeys(a[0]), unquotedKey),
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, quickFixes);
                    }
                }
            }
        }

        private @Nullable Pair<PsiElement, String> getDictCallKey(@Nullable PyExpression argument) {
            if (argument instanceof PyParenthesizedExpression) {
                PyExpression expression = PyPsiUtils.flattenParens(argument);
                if (expression instanceof PyTupleExpression) {
                    PyExpression key = ((PyTupleExpression)expression).getElements()[0];
                    String keyValue = getKeyValue(key);
                    if (keyValue != null) {
                        return Pair.createNonNull(key, keyValue);
                    }
                }
            }

            if (argument instanceof PyKeywordArgument) {
                ASTNode node = ((PyKeywordArgument)argument).getKeywordNode();
                String keyValue = ((PyKeywordArgument)argument).getKeyword();
                if (node != null && keyValue != null) {
                    return Pair.createNonNull(node.getPsi(), wrapStringKey(keyValue));
                }
            }

            return null;
        }

        private static boolean isDict(PyCallExpression expression) {
            PyExpression callee = expression.getCallee();
            return callee != null && "dict".equals(callee.getText());
        }

        private static String wrapStringKey(String key) {
            return "'" + key + "'";
        }

        private static String unwrapStringKey(String key) {
            return StringUtil.unquoteString(key, '\'');
        }
    }
}
