// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
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
import org.jspecify.annotations.Nullable;

/**
 * User: catherine
 * Intention to convert dict constructor to dict literal expression with only explicit keyword arguments
 * For instance,
 * dict() -> {}
 * dict(a=3, b=5) -> {'a': 3, 'b': 5}
 * dict(foo) -> no transformation
 * dict(**foo) -> no transformation
 */
// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyDictConstructorToLiteralFormIntention", categories = "Python", fileExtensions = "py")
public final class PyDictConstructorToLiteralFormIntention extends PsiElementBaseIntentionAction {
    public PyDictConstructorToLiteralFormIntention() {
        setText(PyLocalize.intnConvertDictConstructorToDictLiteral());
    }

    private static @Nullable PyCallExpression findElement(PsiElement leaf) {
        PsiElement current = leaf;
        while (current != null) {
            if (current instanceof PyCallExpression candidate) {
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
        PyCallExpression element = findElement(leaf);
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
        PyCallExpression element = findElement(leaf);
        if (element == null) {
            return;
        }
        invoke(element, editor);
    }

    private @Nullable LocalizeValue getPresentation(PyCallExpression element) {
        if (element.isCalleeText("dict")) {
            TypeEvalContext context = TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile());
            PyType type = context.getType(element);
            if (type != null && type.isBuiltin()) {
                PyExpression[] argumentList = element.getArguments();
                for (PyExpression argument : argumentList) {
                    if (!(argument instanceof PyKeywordArgument)) {
                        return null;
                    }
                }
                return PyLocalize.intnConvertDictConstructorToDictLiteral();
            }
        }
        return null;
    }


    private void invoke(PyCallExpression element, Editor editor) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
        replaceDictConstructor(element, elementGenerator);
    }

    private static void replaceDictConstructor(PyCallExpression expression, PyElementGenerator elementGenerator) {
        PyExpression[] argumentList = expression.getArguments();
        StringBuilder stringBuilder = new StringBuilder();

        int size = argumentList.length;

        for (int i = 0; i != size; ++i) {
            PyExpression argument = argumentList[i];
            if (argument instanceof PyKeywordArgument) {
                stringBuilder.append("'");
                stringBuilder.append(((PyKeywordArgument)argument).getKeyword());
                stringBuilder.append("' : ");
                stringBuilder.append(((PyKeywordArgument)argument).getValueExpression().getText());
                if (i != size - 1) {
                    stringBuilder.append(",");
                }
            }
        }
        PyDictLiteralExpression dict =
                (PyDictLiteralExpression)elementGenerator.createFromText(LanguageLevel.forElement(expression), PyExpressionStatement.class,
                "{" + stringBuilder + "}").getExpression();
        expression.replace(dict);
    }
}

