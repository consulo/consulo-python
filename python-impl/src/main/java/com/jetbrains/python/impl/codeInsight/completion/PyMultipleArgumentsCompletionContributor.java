// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.extensions.CaptureExtKt;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ExtensionImpl
public class PyMultipleArgumentsCompletionContributor extends CompletionContributor implements DumbAware {
    public PyMultipleArgumentsCompletionContributor() {
        extend(CompletionType.BASIC, CaptureExtKt.inArgumentList(PlatformPatterns.psiElement()), MyCompletionProvider.INSTANCE);
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static final class MyCompletionProvider implements CompletionProvider {
        private static final MyCompletionProvider INSTANCE = new MyCompletionProvider();

        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
            PsiElement position = parameters.getPosition();
            Integer argumentIndex = getArgumentIndex(position);
            if (argumentIndex == null) {
                return;
            }

            PyCallExpression call = PsiTreeUtil.getParentOfType(position, PyCallExpression.class);
            if (call == null) {
                return;
            }
            TypeEvalContext typeEvalContext = PyCompletionUtilsKt.getTypeEvalContext(parameters);
            PyResolveContext resolveContext = PyResolveContext.defaultContext(typeEvalContext);
            List<PyCallableType> callableTypes = call.multiResolveCallee(resolveContext);
            if (callableTypes.isEmpty()) {
                return;
            }

            ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(position);
            if (scopeOwner == null) {
                return;
            }
            Set<String> names = collectNames(scopeOwner, position);

            for (PyCallableType callableType : callableTypes) {
                List<PyCallableParameter> callableParameters = callableType.getParameters(typeEvalContext);
                if (callableParameters == null ||
                    argumentIndex >= callableParameters.size() ||
                    ContainerUtil.exists(callableParameters, it -> it.isKeywordContainer() || it.isPositionalContainer())
                ) {
                    continue;
                }

                List<PyCallableParameter> unfilledParameters = ContainerUtil.subList(callableParameters, argumentIndex);
                List<String> variables = collectVariablesToComplete(unfilledParameters, names);
                if (variables.size() > 1) {
                    result.addElement(createParametersLookupElement(variables, call));
                }
            }
        }
    }

    public static final class Helper {
        public static final Key<Boolean> MULTIPLE_ARGUMENTS_VARIANT_KEY = Key.create("py.multiple.arguments.completion.variant");

        private Helper() {
        }
    }

    private static @Nullable Integer getArgumentIndex(PsiElement position) {
        PyArgumentList argumentList = PsiTreeUtil.getParentOfType(position, PyArgumentList.class);
        if (argumentList == null) {
            return null;
        }
        PyExpression[] arguments = argumentList.getArguments();
        if (arguments.length == 0) {
            return null;
        }
        for (PyExpression argument : arguments) {
            if (argument instanceof PyKeywordArgument || argument instanceof PyStarArgument) {
                return null;
            }
        }
        if (!PsiTreeUtil.isAncestor(arguments[arguments.length - 1], position, false)) {
            return null;
        }
        return arguments.length - 1;
    }

    private static LookupElement createParametersLookupElement(List<String> variables, PyCallExpression call) {
        LookupElementBuilder builder = LookupElementBuilder.create(String.join(", ", variables))
            .withIcon(PlatformIconGroup.nodesVariable())
            .withInsertHandler(new PyMultipleArgumentsInsertHandler(call));
        builder.putUserData(Helper.MULTIPLE_ARGUMENTS_VARIANT_KEY, true);
        return builder;
    }

    private static List<String> collectVariablesToComplete(List<PyCallableParameter> parameters, Set<String> argumentsNames) {
        List<String> variables = new ArrayList<>();
        boolean keywordsOnlyFlag = false;

        for (PyCallableParameter parameter : parameters) {
            if (parameter.isPositionOnlySeparator()) {
                continue;
            }
            if (parameter.isKeywordOnlySeparator()) {
                keywordsOnlyFlag = true;
                continue;
            }

            String paramName = parameter.getName();
            if (paramName == null) {
                return Collections.emptyList();
            }
            if (argumentsNames.contains(paramName)) {
                if (!keywordsOnlyFlag) {
                    variables.add(paramName);
                }
                else {
                    variables.add(paramName + "=" + paramName);
                }
            }
            else {
                if (!parameter.hasDefaultValue()) {
                    return Collections.emptyList();
                }
            }
        }

        return variables;
    }

    private static Set<String> collectNames(ScopeOwner scope, PsiElement position) {
        Set<String> result = new HashSet<>();
        for (PsiNamedElement element : ControlFlowCache.getScope(scope).getNamedElements()) {
            PyListCompExpression listComp = PsiTreeUtil.getParentOfType(element, PyListCompExpression.class);
            boolean accepted = listComp != null
                ? PsiTreeUtil.isAncestor(listComp.getResultExpression(), position, false)
                : PyPsiUtils.isBefore(element, position);
            if (accepted) {
                String name = element.getName();
                if (name != null) {
                    result.add(name);
                }
            }
        }
        return result;
    }
}
