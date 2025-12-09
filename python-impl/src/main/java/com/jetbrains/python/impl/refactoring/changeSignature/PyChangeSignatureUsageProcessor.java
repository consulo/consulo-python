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
package com.jetbrains.python.impl.refactoring.changeSignature;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.impl.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.language.editor.refactoring.ResolveSnapshotProvider;
import consulo.language.editor.refactoring.changeSignature.ChangeInfo;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureUsageProcessor;
import consulo.language.editor.refactoring.changeSignature.ParameterInfo;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author ktisha
 */
@ExtensionImpl(id = "Python")
public class PyChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessor {
    private boolean useKeywords = false;
    private boolean isMethod = false;
    private boolean isAfterStar = false;

    @Nonnull
    @Override
    public UsageInfo[] findUsages(@Nonnull ChangeInfo info) {
        if (info instanceof PyChangeInfo pyChangeInfo) {
            List<UsageInfo> usages = PyRefactoringUtil.findUsages(pyChangeInfo.getMethod(), true);
            Query<PyFunction> search = PyOverridingMethodsSearch.search(pyChangeInfo.getMethod(), true);
            Collection<PyFunction> functions = search.findAll();
            for (PyFunction function : functions) {
                usages.add(new UsageInfo(function));
                usages.addAll(PyRefactoringUtil.findUsages(function, true));
            }
            return usages.toArray(new UsageInfo[usages.size()]);
        }
        return UsageInfo.EMPTY_ARRAY;
    }

    @Nonnull
    @Override
    public MultiMap<PsiElement, LocalizeValue> findConflicts(@Nonnull ChangeInfo info, SimpleReference<UsageInfo[]> refUsages) {
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
        if (info instanceof PyChangeInfo pyChangeInfo && info.isNameChanged()) {
            PyFunction function = pyChangeInfo.getMethod();
            PyClass clazz = function.getContainingClass();
            if (clazz != null && clazz.findMethodByName(info.getNewName(), true, null) != null) {
                conflicts.putValue(
                    function,
                    RefactoringLocalize.method0IsAlreadyDefinedInThe1(info.getNewName(), "class " + clazz.getQualifiedName())
                );
            }
        }
        return conflicts;
    }

    @Override
    @RequiredWriteAction
    public boolean processUsage(
        @Nonnull ChangeInfo changeInfo,
        @Nonnull UsageInfo usageInfo,
        boolean beforeMethodChange,
        @Nonnull UsageInfo[] usages
    ) {
        if (!isPythonUsage(usageInfo)) {
            return false;
        }
        if (!(changeInfo instanceof PyChangeInfo pyChangeInfo)) {
            return false;
        }
        if (!beforeMethodChange) {
            return false;
        }
        PsiElement element = usageInfo.getElement();

        if (pyChangeInfo.isNameChanged()) {
            PsiElement method = pyChangeInfo.getMethod();
            RenameUtil.doRenameGenericNamedElement(method, changeInfo.getNewName(), usages, null);
        }
        if (element == null) {
            return false;
        }
        if (element.getParent() instanceof PyCallExpression call) {
            PyArgumentList argumentList = call.getArgumentList();
            if (argumentList != null) {
                PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
                StringBuilder builder = buildSignature(pyChangeInfo, call);

                PyExpression newCall;
                if (call instanceof PyDecorator) {
                    newCall = elementGenerator.createDecoratorList("@" + builder.toString()).getDecorators()[0];
                }
                else {
                    newCall = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), builder.toString());
                }
                call.replace(newCall);

                return true;
            }
        }
        else if (element instanceof PyFunction function) {
            processFunctionDeclaration(pyChangeInfo, function);
        }
        return false;
    }

    @RequiredReadAction
    private StringBuilder buildSignature(PyChangeInfo changeInfo, PyCallExpression call) {
        PyArgumentList argumentList = call.getArgumentList();
        PyExpression callee = call.getCallee();
        String name = callee != null ? callee.getText() : changeInfo.getNewName();
        StringBuilder builder = new StringBuilder(name + "(");
        if (argumentList != null) {
            PyParameterInfo[] newParameters = changeInfo.getNewParameters();
            List<String> params = collectParameters(newParameters, argumentList);
            builder.append(StringUtil.join(params, ","));
        }
        builder.append(")");
        return builder;
    }

    @RequiredReadAction
    private List<String> collectParameters(PyParameterInfo[] newParameters, @Nonnull PyArgumentList argumentList) {
        useKeywords = false;
        isMethod = false;
        isAfterStar = false;
        List<String> params = new ArrayList<>();

        int currentIndex = 0;
        PyExpression[] arguments = argumentList.getArguments();

        for (PyParameterInfo info : newParameters) {
            int oldIndex = calculateOldIndex(info);
            String parameterName = info.getName();
            if (parameterName.equals(PyNames.CANONICAL_SELF) || parameterName.equals("*")) {
                continue;
            }

            if (parameterName.startsWith("**")) {
                currentIndex = addKwArgs(params, arguments, currentIndex);
            }
            else if (parameterName.startsWith("*")) {
                currentIndex = addPositionalContainer(params, arguments, currentIndex);
            }
            else if (oldIndex < 0) {
                addNewParameter(params, info);
                currentIndex += 1;
            }
            else {
                currentIndex = moveParameter(params, argumentList, info, currentIndex, oldIndex, arguments);
            }
        }
        return params;
    }

    private int calculateOldIndex(ParameterInfo info) {
        if (info.getName().equals(PyNames.CANONICAL_SELF)) {
            isMethod = true;
        }
        if (info.getName().equals("*")) {
            isAfterStar = true;
            useKeywords = true;
        }
        int oldIndex = info.getOldIndex();
        oldIndex = isMethod ? oldIndex - 1 : oldIndex;
        oldIndex = isAfterStar ? oldIndex - 1 : oldIndex;
        return oldIndex;
    }

    @RequiredReadAction
    private static int addPositionalContainer(List<String> params, PyExpression[] arguments, int index) {
        for (int i = index; i != arguments.length; ++i) {
            if (!(arguments[i] instanceof PyKeywordArgument)) {
                params.add(arguments[i].getText());
                index += 1;
            }
        }
        return index;
    }

    @RequiredReadAction
    private static int addKwArgs(List<String> params, PyExpression[] arguments, int index) {
        for (int i = index; i < arguments.length; ++i) {
            if (arguments[i] instanceof PyKeywordArgument keywordArgument) {
                params.add(keywordArgument.getText());
                index += 1;
            }
        }
        return index;
    }

    private void addNewParameter(List<String> params, PyParameterInfo info) {
        if (info.getDefaultInSignature()) {
            useKeywords = true;
        }
        else {
            params.add(useKeywords ? info.getName() + " = " + info.getDefaultValue() : info.getDefaultValue());
        }
    }

    /**
     * @return current index in argument list
     */
    @RequiredReadAction
    private int moveParameter(
        List<String> params,
        PyArgumentList argumentList,
        PyParameterInfo info,
        int currentIndex,
        int oldIndex,
        PyExpression[] arguments
    ) {
        String paramName = info.getOldName();
        PyKeywordArgument keywordArgument = argumentList.getKeywordArgument(paramName);
        if (keywordArgument != null) {
            params.add(keywordArgument.getText());
            useKeywords = true;
            return currentIndex + 1;
        }
        else if (currentIndex < arguments.length) {
            PyExpression currentParameter = arguments[currentIndex];
            if (currentParameter instanceof PyKeywordArgument && info.isRenamed()) {
                params.add(currentParameter.getText());
            }
            else if (oldIndex < arguments.length
                && (!(info.getDefaultInSignature() && arguments[oldIndex].getText().equals(info.getDefaultValue()))
                || !(currentParameter instanceof PyKeywordArgument))) {
                return addOldPositionParameter(params, arguments[oldIndex], info, currentIndex);
            }
            else {
                return currentIndex;
            }
        }
        else if (oldIndex < arguments.length) {
            return addOldPositionParameter(params, arguments[oldIndex], info, currentIndex);
        }
        else if (!info.getDefaultInSignature()) {
            params.add(useKeywords ? paramName + " = " + info.getDefaultValue() : info.getDefaultValue());
        }
        else {
            useKeywords = true;
            return currentIndex;
        }
        return currentIndex + 1;
    }

    @RequiredReadAction
    private int addOldPositionParameter(List<String> params, PyExpression argument, PyParameterInfo info, int currentIndex) {
        String paramName = info.getName();
        if (argument instanceof PyKeywordArgument keywordArgument) {
            PyExpression valueExpression = keywordArgument.getValueExpression();

            if (!paramName.equals(argument.getName()) && !StringUtil.isEmptyOrSpaces(info.getDefaultValue())) {
                if (!info.getDefaultInSignature()) {
                    params.add(useKeywords ? info.getName() + " = " + info.getDefaultValue() : info.getDefaultValue());
                }
                else {
                    return currentIndex;
                }
            }
            else {
                params.add(valueExpression == null ? paramName : paramName + " = " + valueExpression.getText());
                useKeywords = true;
            }
        }
        else {
            params.add(
                useKeywords && !argument.getText().equals(info.getDefaultValue())
                    ? paramName + " = " + argument.getText()
                    : argument.getText()
            );
        }
        return currentIndex + 1;
    }

    @RequiredReadAction
    private static boolean isPythonUsage(UsageInfo info) {
        PsiElement element = info.getElement();
        return element != null && element.getLanguage() == PythonLanguage.INSTANCE;
    }

    @Override
    @RequiredWriteAction
    public boolean processPrimaryMethod(@Nonnull ChangeInfo changeInfo) {
        if (changeInfo instanceof PyChangeInfo pyChangeInfo && changeInfo.getLanguage().is(PythonLanguage.INSTANCE)) {
            processFunctionDeclaration(pyChangeInfo, pyChangeInfo.getMethod());
            return true;
        }
        return false;
    }

    @RequiredWriteAction
    private static void processFunctionDeclaration(@Nonnull PyChangeInfo changeInfo, @Nonnull PyFunction function) {
        if (changeInfo.isParameterNamesChanged()) {
            PyParameter[] oldParameters = function.getParameterList().getParameters();
            for (PyParameterInfo paramInfo : changeInfo.getNewParameters()) {
                if (paramInfo.getOldIndex() >= 0 && paramInfo.isRenamed()) {
                    String newName = StringUtil.trimLeading(paramInfo.getName(), '*').trim();
                    UsageInfo[] usages = RenameUtil.findUsages(oldParameters[paramInfo.getOldIndex()], newName, true, false, null);
                    for (UsageInfo info : usages) {
                        RenameUtil.rename(info, newName);
                    }
                }
            }
        }
        if (changeInfo.isParameterSetOrOrderChanged()) {
            fixDoc(changeInfo, function);
            updateParameterList(changeInfo, function);
        }
        if (changeInfo.isNameChanged()) {
            RenameUtil.doRenameGenericNamedElement(function, changeInfo.getNewName(), UsageInfo.EMPTY_ARRAY, null);
        }
    }

    @RequiredReadAction
    private static void fixDoc(PyChangeInfo changeInfo, @Nonnull PyFunction function) {
        PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
        if (docStringExpression == null) {
            return;
        }
        PyParameterInfo[] parameters = changeInfo.getNewParameters();
        Set<String> names = new HashSet<>();
        for (PyParameterInfo info : parameters) {
            names.add(StringUtil.trimLeading(info.getName(), '*').trim());
        }
        Module module = function.getModule();
        if (module == null) {
            return;
        }
        PyDocstringGenerator generator = PyDocstringGenerator.forDocStringOwner(function);
        for (PyParameter p : function.getParameterList().getParameters()) {
            String paramName = p.getName();
            if (!names.contains(paramName) && paramName != null) {
                generator.withoutParam(paramName);
            }
        }
        generator.buildAndInsert();
    }

    @RequiredWriteAction
    private static void updateParameterList(PyChangeInfo changeInfo, PyFunction baseMethod) {
        PsiElement parameterList = baseMethod.getParameterList();

        PyParameterInfo[] parameters = changeInfo.getNewParameters();
        StringBuilder builder = new StringBuilder("def foo(");
        PyStringLiteralExpression docstring = baseMethod.getDocStringExpression();
        PyParameter[] oldParameters = baseMethod.getParameterList().getParameters();
        PyElementGenerator generator = PyElementGenerator.getInstance(baseMethod.getProject());
        PyDocstringGenerator docStringGenerator = PyDocstringGenerator.forDocStringOwner(baseMethod);
        boolean newParameterInDocString = false;
        for (int i = 0; i < parameters.length; ++i) {
            PyParameterInfo info = parameters[i];

            int oldIndex = info.getOldIndex();
            if (i != 0 && oldIndex < oldParameters.length) {
                builder.append(", ");
            }

            if (docstring != null && oldIndex < 0) {
                newParameterInDocString = true;
                docStringGenerator.withParam(info.getName());
            }

            if (oldIndex < oldParameters.length) {
                builder.append(info.getName());
            }
            if (oldIndex >= 0 && oldIndex < oldParameters.length
                && oldParameters[oldIndex] instanceof PyNamedParameter namedParam) {
                PyAnnotation annotation = namedParam.getAnnotation();
                if (annotation != null) {
                    builder.append(annotation.getText());
                }
            }
            String defaultValue = info.getDefaultValue();
            if (defaultValue != null && info.getDefaultInSignature() && StringUtil.isNotEmpty(defaultValue)) {
                builder.append(" = ").append(defaultValue);
            }
        }
        builder.append("): pass");

        if (newParameterInDocString) {
            docStringGenerator.buildAndInsert();
        }

        PyParameterList newParameterList =
            generator.createFromText(LanguageLevel.forElement(baseMethod), PyFunction.class, builder.toString()).getParameterList();
        parameterList.replace(newParameterList);
    }

    @Override
    public boolean shouldPreviewUsages(@Nonnull ChangeInfo changeInfo, @Nonnull UsageInfo[] usages) {
        return false;
    }

    @Override
    public void registerConflictResolvers(
        @Nonnull List<ResolveSnapshotProvider.ResolveSnapshot> snapshots,
        @Nonnull ResolveSnapshotProvider resolveSnapshotProvider,
        @Nonnull UsageInfo[] usages,
        @Nonnull ChangeInfo changeInfo
    ) {
    }
}
