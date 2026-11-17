// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.codeInsight.InitVarInfo;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.codeInsight.stdlib.PyAttrsDataclassType;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames.Attrs;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames.Dataclasses;
import com.jetbrains.python.impl.codeInsight.stdlib.PyStdlibDataclassType;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.extensions.CaptureExtKt;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.completion.AutoCompletionContext;
import consulo.language.editor.completion.AutoCompletionDecision;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.util.lang.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ExtensionImpl
public class PyDataclassCompletionContributor extends CompletionContributor implements DumbAware {

    @Override
    public AutoCompletionDecision handleAutoCompletionPossibility(AutoCompletionContext context) {
        return PyCompletionUtilsKt.autoInsertSingleItem(context);
    }

    public PyDataclassCompletionContributor() {
        extend(CompletionType.BASIC, CaptureExtKt.afterDefInMethod(PlatformPatterns.psiElement()), PostInitProvider.INSTANCE);
        extend(CompletionType.BASIC, CaptureExtKt.inParameterList(PlatformPatterns.psiElement()), AttrsValidatorParameterProvider.INSTANCE);
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static final class PostInitProvider implements CompletionProvider {
        private static final PostInitProvider INSTANCE = new PostInitProvider();

        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
            PyClass cls = PyCompletionUtilsKt.getPyClass(parameters);
            if (cls == null) {
                return;
            }
            TypeEvalContext typeEvalContext = PyCompletionUtilsKt.getTypeEvalContext(parameters);

            PyDataclassParameters dataclassParameters = PyDataclassesKt.parseDataclassParameters(cls, typeEvalContext);
            if (dataclassParameters == null || !dataclassParameters.getInit()) {
                return;
            }

            if (PyStdlibDataclassType.INSTANCE.equals(dataclassParameters.getType())) {
                List<String> postInitParameters = new ArrayList<>();
                postInitParameters.add(PyNames.CANONICAL_SELF);

                List<InitVarInfo> initVars = PyDataclassesKt.getDataclassInitVars(cls, dataclassParameters, typeEvalContext);
                if (initVars != null) {
                    for (InitVarInfo it : initVars) {
                        String name = it.targetExpression().getName();
                        PyExpression typeHint = PyTypingTypeProvider.getAnnotationValue(it.targetExpression(), typeEvalContext);
                        if (name != null && typeHint instanceof PySubscriptionExpression subscriptionExpression) {
                            PyExpression indexExpression = subscriptionExpression.getIndexExpression();
                            String parameterString;
                            if (indexExpression != null) {
                                parameterString = name + ": " + indexExpression.getText();
                            }
                            else {
                                parameterString = name;
                            }
                            postInitParameters.add(parameterString);
                        }
                    }
                }

                PyCompletionUtilsKt.addMethodToResult(result, cls, typeEvalContext,
                    Dataclasses.DUNDER_POST_INIT, "(" + StringUtil.join(postInitParameters, ", ") + ")", null);
            }
            else if (PyAttrsDataclassType.INSTANCE.equals(dataclassParameters.getType())) {
                PyCompletionUtilsKt.addMethodToResult(result, cls, typeEvalContext, Attrs.DUNDER_POST_INIT);
            }
        }
    }

    private static final class AttrsValidatorParameterProvider implements CompletionProvider {
        private static final AttrsValidatorParameterProvider INSTANCE = new AttrsValidatorParameterProvider();

        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
            PyClass cls = PyCompletionUtilsKt.getPyClass(parameters);
            if (cls == null) {
                return;
            }

            PyParameterList parameterList = PsiTreeUtil.getParentOfType(parameters.getPosition(), PyParameterList.class);
            if (parameterList == null) {
                return;
            }
            PyParameter parameter = PsiTreeUtil.getParentOfType(parameters.getPosition(), PyParameter.class);
            if (parameter == null) {
                return;
            }

            int index = Arrays.asList(parameterList.getParameters()).indexOf(parameter);
            if (index != 1 && index != 2) {
                return;
            }

            PyFunction containingFunction = parameterList.getContainingFunction();
            PyDecoratorList decorators = containingFunction != null ? containingFunction.getDecoratorList() : null;
            if (decorators == null) {
                return;
            }
            boolean noneMatch = true;
            for (PyDecorator it : decorators.getDecorators()) {
                if (it.getQualifiedName() != null && it.getQualifiedName().endsWith("validator")) {
                    noneMatch = false;
                    break;
                }
            }
            if (noneMatch) {
                return;
            }

            TypeEvalContext typeEvalContext = PyCompletionUtilsKt.getTypeEvalContext(parameters);

            PyDataclassParameters dataclassParameters = PyDataclassesKt.parseDataclassParameters(cls, typeEvalContext);
            if (dataclassParameters != null && PyAttrsDataclassType.INSTANCE.equals(dataclassParameters.getType())) {
                result.addElement(LookupElementBuilder.create(index == 1 ? "attribute" : "value").withIcon(PlatformIconGroup.nodesParameter()));
            }
        }
    }
}
