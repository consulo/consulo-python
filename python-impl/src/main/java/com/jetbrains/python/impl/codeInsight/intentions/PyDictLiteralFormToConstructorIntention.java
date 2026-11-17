// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * User: catherine
 * <p>
 * Intention to convert dict literal expression to dict constructor if the keys are all string constants on a literal dict.
 * For instance,
 * {} -> dict
 * {'a': 3, 'b': 5} -> dict(a=3, b=5)
 * {a: 3, b: 5} -> no transformation
 */
// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyDictLiteralFormToConstructorIntention", categories = "Python", fileExtensions = "py")
public final class PyDictLiteralFormToConstructorIntention extends PsiElementBaseIntentionAction {
    public PyDictLiteralFormToConstructorIntention() {
        setText(PyLocalize.intnConvertDictLiteralToDictConstructor());
    }

    private static @Nullable PyDictLiteralExpression findElement(PsiElement leaf) {
        PsiElement current = leaf;
        while (current != null) {
            if (current instanceof PyDictLiteralExpression candidate) {
                return candidate;
            }
            if (current instanceof PsiFile) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement leaf) {
        PyDictLiteralExpression element = findElement(leaf);
        if (element == null) {
            return false;
        }
        LocalizeValue text = getPresentation(element);
        if (text == null) {
            return false;
        }
        setText(text);
        return true;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement leaf) throws IncorrectOperationException {
        PyDictLiteralExpression element = findElement(leaf);
        if (element == null) {
            return;
        }
        invoke(element, editor);
    }

    private @Nullable LocalizeValue getPresentation(PyDictLiteralExpression element) {
        PyKeyValueExpression[] elements = element.getElements();
        for (PyKeyValueExpression expression : elements) {
            PyExpression key = expression.getKey();
            if (!(key instanceof PyStringLiteralExpression)) {
                return null;
            }
            String str = ((PyStringLiteralExpression)key).getStringValue();
            if (PyNames.isReserved(str)) {
                return null;
            }
            if (str.isEmpty() || Character.isDigit(str.charAt(0))) {

                return null;

            }
            if (!StringUtil.isJavaIdentifier(str)) {
                return null;
            }
        }
        return PyLocalize.intnConvertDictLiteralToDictConstructor();
    }

    private void invoke(PyDictLiteralExpression element, Editor editor) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
        replaceDictLiteral(element, elementGenerator);
    }


    private static void replaceDictLiteral(PyDictLiteralExpression dictExpression, PyElementGenerator elementGenerator) {
        PyExpression[] argumentList = dictExpression.getElements();
        StringBuilder stringBuilder = new StringBuilder("dict(");
        int size = argumentList.length;
        for (int i = 0; i != size; ++i) {
            PyExpression argument = argumentList[i];
            if (argument instanceof PyKeyValueExpression) {
                PyExpression key = ((PyKeyValueExpression)argument).getKey();
                PyExpression value = ((PyKeyValueExpression)argument).getValue();
                if (key instanceof PyStringLiteralExpression && value != null) {
                    stringBuilder.append(((PyStringLiteralExpression)key).getStringValue());
                    stringBuilder.append("=");
                    stringBuilder.append(value.getText());
                    if (i != size - 1) {
                        stringBuilder.append(", ");
                    }
                }
            }
        }
        stringBuilder.append(")");
        PyCallExpression callExpression = (PyCallExpression)elementGenerator.createFromText(LanguageLevel.forElement(dictExpression),
                PyExpressionStatement.class,
                stringBuilder.toString()).getExpression();
        dictExpression.replace(callExpression);
    }
}

