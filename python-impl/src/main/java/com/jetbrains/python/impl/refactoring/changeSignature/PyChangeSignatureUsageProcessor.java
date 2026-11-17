// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.changeSignature;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.ast.PyAstSingleStarParameter;
import com.jetbrains.python.ast.PyAstSlashParameter;
import com.jetbrains.python.impl.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.impl.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.impl.inspections.quickfix.PyChangeSignatureQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ArgumentMappingResults;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableArgument;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.language.editor.refactoring.ResolveSnapshotProvider;
import consulo.language.editor.refactoring.changeSignature.ChangeInfo;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureUsageProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * User : ktisha
 */
@ExtensionImpl(id = "Python")
public class PyChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessor {
    @Override
    public UsageInfo[] findUsages(ChangeInfo info) {
        if (info instanceof PyChangeInfo) {
            final PyFunction targetFunction = ((PyChangeInfo) info).getMethod();
            final List<UsageInfo> usages = PyPsiIndexUtil.findUsages(targetFunction, true);
            if (!PyUtil.isConstructorLikeMethod(targetFunction)) {
                final Query<PyFunction> search = PyOverridingMethodsSearch.search(targetFunction, true);
                for (PyFunction override : search.findAll()) {
                    usages.add(new UsageInfo(override));
                    usages.addAll(PyPsiIndexUtil.findUsages(override, true));
                }
            }
            return usages.toArray(UsageInfo.EMPTY_ARRAY);
        }
        return UsageInfo.EMPTY_ARRAY;
    }

    @Override
    public MultiMap<PsiElement, LocalizeValue> findConflicts(ChangeInfo info, SimpleReference<UsageInfo[]> refUsages) {
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
        ChangeInfo changeInfo,
        UsageInfo usageInfo,
        boolean beforeMethodChange,
        UsageInfo[] usages
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
            // Don't modify the call that was the cause of Change Signature invocation
            if (call.getUserData(PyChangeSignatureQuickFix.CHANGE_SIGNATURE_ORIGINAL_CALL) != null) {
                return true;
            }
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
        else if (element instanceof PyFunction function && element != pyChangeInfo.getMethod()) {
            processFunctionDeclaration(pyChangeInfo, function);
        }
        return false;
    }

    @RequiredReadAction
    private static StringBuilder buildSignature(PyChangeInfo changeInfo, PyCallExpression call) {
        final PyArgumentList argumentList = call.getArgumentList();
        final PyExpression callee = call.getCallee();
        String name = callee != null ? callee.getText() : changeInfo.getNewName();
        StringBuilder builder = new StringBuilder(name + "(");
        if (argumentList != null) {
            List<String> params = collectParameters(changeInfo, call);
            builder.append(StringUtil.join(params, ","));
        }
        builder.append(")");
        return builder;
    }


    @RequiredReadAction
    private static List<String> collectParameters(PyChangeInfo changeInfo, PyCallExpression call) {
        boolean keywordArgsRequired = false;
        final List<String> newArguments = new ArrayList<>();

        final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(call.getProject(), null);
        final PyFunction function = changeInfo.getMethod();

        final List<PyCallableParameter> allOrigParams = function.getParameters(typeEvalContext);
        Map<PyCallableArgument, PyCallableParameter> mappedParameters = Map.of();
        List<@Nullable PyCallableParameter> parametersMappedToVariadicKeywordArguments = List.of();
        int implicitCount = 0;
        if (typeEvalContext.getType(function) instanceof PyCallableType callableType) {
            final List<PyCallableParameter> rawParameters = callableType.getParameters(typeEvalContext);
            if (rawParameters != null) {
                final List<PyCallableArgument> arguments = ContainerUtil.map(call.getArguments(), PyCallableArgument::new);
                final List<PyCallableParameter> parameters = PyCallExpressionHelper.unpackParameters(rawParameters, arguments, typeEvalContext);
                final PyResolveContext resolveContext = PyResolveContext.defaultContext(typeEvalContext);
                final List<PyCallableParameter> explicitParameters = filterExplicitParameters(parameters, function, call, resolveContext);
                implicitCount = parameters.size() - explicitParameters.size();
                ArgumentMappingResults mapping = PyCallExpressionHelper.analyzeArguments(arguments, explicitParameters, typeEvalContext);
                mappedParameters = mapping.getMappedParameters();
                parametersMappedToVariadicKeywordArguments = mapping.getParametersMappedToVariadicKeywordArguments();
            }
        }

        MultiMap<Integer, PyCallableArgument> oldParamIndexToArgs = MultiMap.create();
        for (Map.Entry<PyCallableArgument, PyCallableParameter> entry : mappedParameters.entrySet()) {
            final PyCallableParameter param = entry.getValue();
            oldParamIndexToArgs.putValue(allOrigParams.indexOf(param), entry.getKey());
        }
        assert oldParamIndexToArgs.keySet().stream().allMatch(index -> index >= 0);

        List<PyParameterInfo> newParamInfos = Arrays.asList(changeInfo.getNewParameters());

        final int posVarargIndex = ContainerUtil.indexOf(newParamInfos, info -> isPositionalVarargName(info.getName()));
        final int posOnlyMarkerIndex = ContainerUtil.indexOf(newParamInfos, info -> PyAstSlashParameter.TEXT.equals(info.getName()));
        final boolean posVarargEmpty = posVarargIndex != -1 && oldParamIndexToArgs.get(newParamInfos.get(posVarargIndex).getOldIndex()).isEmpty();
        List<PyExpression> notInsertedVariadicKeywordArgs =
            new ArrayList<>(ContainerUtil.filter(call.getArguments(), a -> a instanceof PyStarArgument && ((PyStarArgument) a).isKeyword()));
        boolean variadicKeywordArgsUsed = false;
        for (int paramIndex = implicitCount; paramIndex < newParamInfos.size(); paramIndex++) {
            PyParameterInfo info = newParamInfos.get(paramIndex);
            final String paramName = info.getName();
            final boolean isKeywordVararg = isKeywordVarargName(paramName);
            final boolean isPositionalVararg = isPositionalVarargName(paramName);
            final boolean beforePositionalOnlyMarker = paramIndex < posOnlyMarkerIndex;
            final boolean defaultShouldBeInlined = beforePositionalOnlyMarker &&
                ContainerUtil.exists(newParamInfos.subList(paramIndex + 1, posOnlyMarkerIndex),
                    i -> !i.isNew() && !oldParamIndexToArgs.get(i.getOldIndex()).isEmpty());
            if (paramName.equals(PyAstSingleStarParameter.TEXT)) {
                keywordArgsRequired = true;
                continue;
            }
            if (paramName.equals(PyAstSlashParameter.TEXT)) {
                continue;
            }
            final String paramDefault = StringUtil.notNullize(info.getDefaultValue());
            final int oldIndex = info.getOldIndex();
            if (oldIndex < 0) {
                if (info.getDefaultInSignature() && !defaultShouldBeInlined) {
                    // If the next argument was passed by position it would match with this new default.
                    // Imagine "def f(x, y=None): ..." -> "def f(x, foo=None, y=None): ..." and a call "f(1, 2)"
                    keywordArgsRequired = true;
                }
                else if (!isKeywordVararg && !isPositionalVararg) {
                    newArguments.add(formatArgument(paramName, paramDefault, keywordArgsRequired));
                }
            }
            else {
                final Collection<PyCallableArgument> existingArgs = oldParamIndexToArgs.get(oldIndex);
                final PyCallableParameter oldParam = allOrigParams.get(oldIndex);
                final boolean usesValueFromVariadic = parametersMappedToVariadicKeywordArguments.contains(oldParam);
                variadicKeywordArgsUsed |= usesValueFromVariadic;
                if (!existingArgs.isEmpty()) {
                    for (PyCallableArgument arg : existingArgs) {
                        @Nullable PyExpression argValue;
                        String argName;
                        if (arg.getExpression() instanceof PyKeywordArgument keywordArgument) {
                            argValue = keywordArgument.getValueExpression();
                            argName = StringUtil.notNullize(keywordArgument.getKeyword());
                        }
                        else {
                            argValue = arg.getExpression();
                            argName = "";
                        }
                        notInsertedVariadicKeywordArgs.remove(argValue);
                        // Keep format of existing keyword arguments unless it's illegal in their new position
                        if (!argName.isEmpty() && !(paramIndex < posVarargIndex && !posVarargEmpty) && !beforePositionalOnlyMarker) {
                            keywordArgsRequired = true;
                        }
                        assert !(isPositionalVararg && keywordArgsRequired);
                        final String argValueText = argValue != null ? argValue.getText() : "";
                        final String newArgumentName = isKeywordVararg ? argName : paramName;
                        newArguments.add(formatArgument(newArgumentName, argValueText, keywordArgsRequired));
                    }
                }
                // Parameter receives its default value from the signature, all subsequent arguments must use keyword form
                else if (info.getDefaultInSignature() && !defaultShouldBeInlined) {
                    keywordArgsRequired = true;
                }
                else if (!isPositionalVararg && !isKeywordVararg && !usesValueFromVariadic) {
                    // Existing ordinary parameter with neither a default value in the signature, not a corresponding argument.
                    // Most likely its default was propagated from the signature down to calls.
                    newArguments.add(formatArgument(paramName, paramDefault, keywordArgsRequired));
                }
            }
            // Keyword-only arguments should follow (Python 3)
            if (isPositionalVararg) {
                keywordArgsRequired = true;
            }
        }
        if (variadicKeywordArgsUsed) {
            newArguments.addAll(ContainerUtil.map(notInsertedVariadicKeywordArgs, PsiElement::getText));
        }

        return newArguments;
    }

    private static List<PyCallableParameter> filterExplicitParameters(List<PyCallableParameter> parameters,
                                                                      @Nullable PyCallable callable,
                                                                      PyElement callSite,
                                                                      PyResolveContext resolveContext) {
        final int implicitOffset;
        if (callSite instanceof PyCallExpression callExpression) {
            final PyExpression callee = callExpression.getCallee();
            if (callee != null && callable instanceof PyFunction) {
                implicitOffset = PyCallExpressionHelper.getImplicitArgumentCount(callee, (PyFunction) callable, resolveContext);
            }
            else {
                implicitOffset = 0;
            }
        }
        else {
            implicitOffset = 1;
        }
        return parameters.subList(Math.min(implicitOffset, parameters.size()), parameters.size());
    }

    private static boolean isPositionalVarargName(String paramName) {
        return !isKeywordVarargName(paramName) && !paramName.equals(PyAstSingleStarParameter.TEXT) && paramName.startsWith("*");
    }

    private static boolean isKeywordVarargName(String paramName) {
        return paramName.startsWith("**");
    }

    private static String formatArgument(String name, String value, boolean keywordArgument) {
        if (keywordArgument && !value.startsWith("*")) {
            assert !name.startsWith("*");
            return name + "=" + value;
        }
        else {
            return value;
        }
    }

    @RequiredReadAction
    private static boolean isPythonUsage(UsageInfo info) {
        PsiElement element = info.getElement();
        return element != null && element.getLanguage() == PythonLanguage.INSTANCE;
    }

    @Override
    @RequiredWriteAction
    public boolean processPrimaryMethod(ChangeInfo changeInfo) {
        if (changeInfo instanceof PyChangeInfo pyChangeInfo && changeInfo.getLanguage().is(PythonLanguage.INSTANCE)) {
            processFunctionDeclaration(pyChangeInfo, pyChangeInfo.getMethod());
            return true;
        }
        return false;
    }

    @RequiredWriteAction
    private static void processFunctionDeclaration(PyChangeInfo changeInfo, PyFunction function) {
        if (changeInfo.isParameterNamesChanged()) {
            final PyParameter[] oldParameters = function.getParameterList().getParameters();
            final Map<PyParameter, String> paramRenames = StreamEx.of(changeInfo.getNewParameters())
                .filter(info -> info.getOldIndex() >= 0 && info.isRenamed())
                .toMap(info -> oldParameters[info.getOldIndex()],
                    info -> StringUtil.trimLeading(info.getName(), '*').trim());
            final Map<PsiElement, String> allRenames = new HashMap<>(paramRenames);
            if (changeInfo.isNameChanged()) {
                allRenames.put(function, changeInfo.getNewName());
            }
            for (Map.Entry<PyParameter, String> entry : paramRenames.entrySet()) {
                final PyParameter oldParameter = entry.getKey();
                final String newName = entry.getValue();
                final UsageInfo[] usages = RenameUtil.findUsages(oldParameter, newName, true, false, allRenames);
                for (UsageInfo info : usages) {
                    RenameUtil.rename(info, newName);
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
    private static void fixDoc(PyChangeInfo changeInfo, PyFunction function) {
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
    public boolean shouldPreviewUsages(ChangeInfo changeInfo, UsageInfo[] usages) {
        return false;
    }

    @Override
    public void registerConflictResolvers(
        List<ResolveSnapshotProvider.ResolveSnapshot> snapshots,
        ResolveSnapshotProvider resolveSnapshotProvider,
        UsageInfo[] usages,
        ChangeInfo changeInfo
    ) {
    }
}
