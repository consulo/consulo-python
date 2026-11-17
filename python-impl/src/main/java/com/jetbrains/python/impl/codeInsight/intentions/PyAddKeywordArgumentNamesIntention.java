// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyAddKeywordArgumentNamesIntention", categories = "Python", fileExtensions = "py")
public class PyAddKeywordArgumentNamesIntention extends PsiElementBaseIntentionAction {
    public PyAddKeywordArgumentNamesIntention() {
        setText(PyLocalize.intnNameAddKeywordArgumentNames());
    }

    private static boolean isElementApplicable(PyExpression element) {
        return element.getParent() instanceof PyArgumentList;
    }

    private static boolean stopSearchAt(PsiElement element) {
        return element instanceof PyStatement;
    }

    private static @Nullable PyExpression findElement(PsiElement leaf) {
        PsiElement current = leaf;
        while (current != null) {
            if (current instanceof PyExpression expression && isElementApplicable(expression)) {
                return expression;
            }
            if (stopSearchAt(current) || current instanceof PsiFile) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement leaf) {
        PyExpression element = findElement(leaf);
        if (element == null) {
            return false;
        }
        if (element instanceof PyKeywordArgument || element instanceof PyStarArgument) {
            return false;
        }

        if (!(element.getParent() instanceof PyArgumentList argumentList)) {
            return false;
        }
        PyExpression[] arguments = argumentList.getArguments();
        int caretArgIndex = ArrayUtil.indexOf(arguments, element);
        if (caretArgIndex < 0) {
            return false;
        }

        List<Pair<PyExpression, String>> convertible = collectConvertibleArguments(argumentList, caretArgIndex, project);
        if (convertible == null || convertible.isEmpty()) {
            return false;
        }

        setText(PyLocalize.intnAddKeywordArgumentNames());
        return true;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement leaf) throws IncorrectOperationException {
        PyExpression element = findElement(leaf);
        if (element == null) {
            return;
        }
        if (!(element.getParent() instanceof PyArgumentList argumentList)) {
            return;
        }
        PyExpression[] arguments = argumentList.getArguments();
        int caretArgIndex = ArrayUtil.indexOf(arguments, element);
        if (caretArgIndex < 0) {
            return;
        }

        List<Pair<PyExpression, String>> convertible = collectConvertibleArguments(argumentList, caretArgIndex, project);
        if (convertible == null) {
            return;
        }

        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        LanguageLevel languageLevel = LanguageLevel.forElement(element);

        for (Pair<PyExpression, String> pair : convertible) {
            PyExpression arg = pair.getFirst();
            String paramName = pair.getSecond();
            arg.replace(generator.createKeywordArgument(languageLevel, paramName, arg.getText()));
        }
    }

    /**
     * Resolves parameter names and collects arguments that should be converted to keyword arguments,
     * starting from {@code caretArgIndex}. Returns a list of (argument, parameterName) pairs,
     * or {@code null} if conversion is not possible.
     */
    private static @Nullable List<Pair<PyExpression, String>> collectConvertibleArguments(PyArgumentList argumentList,
                                                                                         int caretArgIndex,
                                                                                         Project project) {
        if (!(argumentList.getParent() instanceof PyCallExpression callExpression)) {
            return null;
        }
        PsiFile containingFile = argumentList.getContainingFile();
        if (containingFile == null) {
            return null;
        }

        TypeEvalContext typeContext = TypeEvalContext.codeAnalysis(project, containingFile);
        PyResolveContext resolveContext = PyResolveContext.defaultContext(typeContext);
        PyCallExpression.PyArgumentsMapping mapping = ContainerUtil.getFirstItem(callExpression.multiMapArguments(resolveContext));
        if (mapping == null) {
            return null;
        }
        Map<PyExpression, PyCallableParameter> mappedParameters = mapping.getMappedParameters();

        PyExpression[] arguments = argumentList.getArguments();

        // Positional unpacking (*args) anywhere in the call breaks positional correspondence
        if (ContainerUtil.exists(arguments, it -> it instanceof PyStarArgument starArgument && !starArgument.isKeyword())) {
            return null;
        }

        List<Pair<PyExpression, String>> result = new ArrayList<>();
        for (PyExpression arg : Arrays.asList(arguments).subList(caretArgIndex, arguments.length)) {
            if (arg instanceof PyKeywordArgument) {
                continue;
            }
            if (arg instanceof PyStarArgument) {
                return null;
            }

            PyCallableParameter param = mappedParameters.get(arg);
            if (param == null) {
                return null;
            }
            if (param.isPositionalContainer()) {
                return null;
            }

            if (param.getParameter() instanceof PyNamedParameter namedParam && namedParam.isPositionalOnly()) {
                return null;
            }

            String paramName = param.getName();
            if (paramName == null) {
                return null;
            }
            result.add(Pair.create(arg, paramName));
        }
        return result;
    }
}
