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
import com.jetbrains.python.psi.types.PyCallableType;
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
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyRemoveKeywordArgumentNamesIntention", categories = "Python", fileExtensions = "py")
public class PyRemoveKeywordArgumentNamesIntention extends PsiElementBaseIntentionAction {
    public PyRemoveKeywordArgumentNamesIntention() {
        setText(PyLocalize.intnNameRemoveKeywordArgumentNames());
    }

    private static boolean isElementApplicable(PyKeywordArgument element) {
        return element.getParent() instanceof PyArgumentList;
    }

    private static boolean stopSearchAt(PsiElement element) {
        return element instanceof PyStatement;
    }

    private static @Nullable PyKeywordArgument findElement(PsiElement leaf) {
        PsiElement current = leaf;
        while (current != null) {
            if (current instanceof PyKeywordArgument keywordArgument && isElementApplicable(keywordArgument)) {
                return keywordArgument;
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
        PyKeywordArgument element = findElement(leaf);
        if (element == null) {
            return false;
        }
        Set<PyKeywordArgument> convertible = collectConvertibleArguments(element, project);
        if (convertible == null || convertible.isEmpty()) {
            return false;
        }
        setText(PyLocalize.intnRemoveKeywordArgumentNames());
        return true;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement leaf) throws IncorrectOperationException {
        PyKeywordArgument element = findElement(leaf);
        if (element == null) {
            return;
        }
        Set<PyKeywordArgument> convertible = collectConvertibleArguments(element, project);
        if (convertible == null) {
            return;
        }

        if (!(element.getParent() instanceof PyArgumentList argumentList)) {
            return;
        }
        PyExpression[] arguments = argumentList.getArguments();

        // Build new argument list:
        // 1. Existing positional args and positional unpacking from the start
        // 2. Converted keyword args in parameter order (values only)
        // 3. Remaining unaffected arguments in their original order
        List<PyExpression> positionalPrefix = new ArrayList<>();
        List<PyExpression> remaining = new ArrayList<>();

        boolean seenNonPositional = false;
        for (PyExpression arg : arguments) {
            if (!seenNonPositional && !(arg instanceof PyKeywordArgument) && !(arg instanceof PyStarArgument starArgument && starArgument.isKeyword())) {
                positionalPrefix.add(arg);
            }
            else {
                seenNonPositional = true;
                if (!convertible.contains(arg)) {
                    remaining.add(arg);
                }
            }
        }

        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        LanguageLevel languageLevel = LanguageLevel.forElement(element);

        List<String> newArgTexts = new ArrayList<>();
        for (PyExpression arg : positionalPrefix) {
            newArgTexts.add(arg.getText());
        }
        for (PyKeywordArgument kwArg : convertible) {
            PyExpression value = kwArg.getValueExpression();
            if (value == null) {
                return;
            }
            newArgTexts.add(value.getText());
        }
        for (PyExpression arg : remaining) {
            newArgTexts.add(arg.getText());
        }

        String newCallText = "f(" + String.join(", ", newArgTexts) + ")";
        if (!(generator.createExpressionFromText(languageLevel, newCallText) instanceof PyCallExpression newCall)) {
            return;
        }
        PyArgumentList newArgList = newCall.getArgumentList();
        if (newArgList == null) {
            return;
        }
        argumentList.replace(newArgList);
    }

    /**
     * Collects keyword arguments that should be converted to positional, in parameter order.
     * Includes the caret argument and all keyword arguments for preceding parameters.
     * Returns {@code null} if conversion is not possible.
     */
    private static @Nullable Set<PyKeywordArgument> collectConvertibleArguments(PyKeywordArgument element, Project project) {
        if (!(element.getParent() instanceof PyArgumentList argumentList)) {
            return null;
        }
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
        if (!mapping.isComplete()) {
            return null;
        }
        Map<PyExpression, PyCallableParameter> mappedParameters = mapping.getMappedParameters();

        PyCallableParameter caretParam = mappedParameters.get(element);
        if (caretParam == null) {
            return null;
        }

        // Reject if keyword-only or keyword container (**kwargs)
        if (caretParam.isKeywordContainer()) {
            return null;
        }
        // Fail-fast for callable parameters over real PSI parameters.
        // For synthesized callable parameters, e.g. for dataclass constructors, 
        // we will need to look for a preceding keyword-only separator parameter. 
        if (caretParam.getParameter() instanceof PyNamedParameter namedParameter && namedParameter.isKeywordOnly()) {
            return null;
        }

        // Get ordered parameter list, skipping implicit parameters (self, cls)
        PyCallableType callableType = mapping.getCallableType();
        if (callableType == null) {
            return null;
        }
        List<PyCallableParameter> allParameters = callableType.getParameters(typeContext);
        if (allParameters == null) {
            return null;
        }

        int caretParamIndex = allParameters.indexOf(caretParam);
        if (caretParamIndex < 0) {
            return null;
        }

        PyExpression[] arguments = argumentList.getArguments();

        // Positional unpacking (*args) in the call breaks positional correspondence
        if (ContainerUtil.exists(arguments, it -> it instanceof PyStarArgument starArgument && !starArgument.isKeyword())) {
            return null;
        }

        // Build a map from PSI parameter to the call-site argument expression
        Map<PyCallableParameter, PyExpression> paramToArg = new HashMap<>();
        for (Map.Entry<PyExpression, PyCallableParameter> entry : mappedParameters.entrySet()) {
            paramToArg.put(entry.getValue(), entry.getKey());
        }

        // Check all params from 0 to caretParamIndex have explicit arguments;
        // collect keyword args that need converting
        Set<PyKeywordArgument> result = new LinkedHashSet<>();
        for (PyCallableParameter param : allParameters.subList(0, Math.min(caretParamIndex + 1, allParameters.size()))) {

            // Skip positional-only separator (/)
            if (param.isPositionOnlySeparator()) {
                continue;
            }

            // A positional/keyword vararg or keyword-only separator (*) before the caret
            // means subsequent params are keyword-only. 
            // Checking it again is necessary for synthesized callable parameters.
            if (param.isPositionalContainer() || param.isKeywordContainer() || param.isKeywordOnlySeparator()) {
                return null;
            }

            PyExpression arg = paramToArg.get(param);

            if (arg == null) {
                // No explicit argument for this parameter — can't convert to positional.
                // This also handles the case where **kwargs unpacking in the call could
                // satisfy this parameter, since such mappings are not in mappedParameters.
                return null;
            }

            if (arg instanceof PyKeywordArgument keywordArgument) {
                result.add(keywordArgument);
            }
        }

        return result;
    }
}
