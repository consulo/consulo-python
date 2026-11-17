// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inlayHints;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyCallExpression.PyArgumentsMapping;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.inlay.HintInfo;
import consulo.language.editor.inlay.InlayInfo;
import consulo.language.editor.inlay.InlayParameterHintsProvider;
import consulo.language.editor.inlay.Option;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.QualifiedName;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.keymap.util.KeymapUtil;
import org.jspecify.annotations.Nullable;

import java.util.*;

@ExtensionImpl
public class PythonInlayParameterHintsProvider implements InlayParameterHintsProvider {
    public static final class Helper {
        public static final Option showForClassConstructorCalls = new Option(
            "python.show.class.constructor.call.parameter.names",
            PyCodeInsightLocalize.inlayParametersPythonShowClassConstructorCallParameterNames(),
            true
        );

        public static final Option showForNonLiteralArguments = new Option(
            "python.show.hints.for.non-literal.arguments",
            PyCodeInsightLocalize.inlayParametersPythonShowHintsForNonLiteralArguments(),
            false
        );

        private Helper() {
        }
    }

    private List<InlayInfo> getInlayInfoForArgumentList(PyArgumentList node) {
        if (node.getParent() instanceof PyClass || node.getArguments().length == 1) {
            return List.of();
        }

        TypeEvalContext context = TypeEvalContext.codeAnalysis(node.getProject(), node.getContainingFile());
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);

        PyCallExpression callExpression = node.getCallExpression();
        if (callExpression == null) {
            return List.of();
        }
        List<PyArgumentsMapping> argumentMappings = callExpression.multiMapArguments(resolveContext);
        if (!isParameterHintSafeOverloads(argumentMappings)) {
            return List.of();
        }
        PyArgumentsMapping mapping = argumentMappings.get(0);

        PyCallableType callableType = mapping.getCallableType();
        PyCallable callable = callableType != null ? callableType.getCallable() : null;

        if (callable == null || (PyUtil.isInitOrNewMethod(callable) && !Helper.showForClassConstructorCalls.isEnabled())) {
            return List.of();
        }

        List<InlayInfo> info = new ArrayList<>();
        Map<PyExpression, PyCallableParameter> mappedParameters = mapping.getMappedParameters();

        for (Map.Entry<PyExpression, PyCallableParameter> entry : mappedParameters.entrySet()) {
            PyExpression argument = entry.getKey();
            PyCallableParameter parameter = entry.getValue();
            if (parameter != null && argument != null) {
                if (parameter.isPositionalContainer()) {
                    info.add(new InlayInfo("*" + parameter.getName(), argument.getTextOffset()));
                    return info;
                }
                if (parameter.isKeywordContainer()) {
                    return info;
                }
                if (!(argument instanceof PyKeywordArgument)) {
                    if (isLiteralArgument(argument) || Helper.showForNonLiteralArguments.isEnabled()) {
                        if (!Objects.equals(parameter.getName(), argument.getText())) {
                            String name = parameter.getName();
                            if (name != null) {
                                info.add(new InlayInfo(name, argument.getTextOffset()));
                            }
                        }
                    }
                }
            }
        }
        return info;
    }

    /**
     * Determines whether it is safe to show hints for a method.
     *
     * @return {@code true} if the method does not have overloads or all the overloads have the same signature, i.e.,
     * all parameters have the same names and are placed in the same order.
     * Otherwise, return {@code false}.
     */
    private static boolean isParameterHintSafeOverloads(List<PyArgumentsMapping> mappings) {
        if (mappings.size() == 1) {
            return true;
        }

        Set<List<String>> distinct = new LinkedHashSet<>();
        for (PyArgumentsMapping mapping : mappings) {
            List<String> listOfParameters = new ArrayList<>();
            for (PyCallableParameter parameter : mapping.getMappedParameters().values()) {
                listOfParameters.add(parameter.getPresentableText(false));
            }
            distinct.add(listOfParameters);
        }
        return distinct.size() == 1;
    }

    @Override
    public @Nullable HintInfo getHintInfo(PsiElement element) {
        if (element instanceof PyArgumentList) {
            PsiElement parent = element.getParent();

            if (parent instanceof PyCallExpression callExpression) {
                PyExpression callee = callExpression.getCallee();

                if (callee == null) {
                    return null;
                }

                TypeEvalContext context = TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile());
                PyResolveContext resolveContext = PyResolveContext.defaultContext(context);

                List<PyCallableType> callableTypes = callExpression.multiResolveCallee(resolveContext);
                if (callableTypes.isEmpty()) {
                    return null;
                }
                PyCallableType callableType = callableTypes.get(0);
                PyCallable callable = callableType.getCallable();

                if (callable instanceof PyQualifiedNameOwner qualifiedNameOwner) {
                    String qName = qualifiedNameOwner.getQualifiedName();
                    String fullQName = PyBuiltinCache.isInBuiltins(callee) ? (qName != null ? toQNameForBuiltins(qName) : null) : qName;
                    PyParameterList parameterList = callable.getParameterList();

                    if (fullQName != null) {
                        List<String> paramNames = new ArrayList<>();
                        for (PyParameter parameter : parameterList.getParameters()) {
                            PyNamedParameter named = parameter.getAsNamed();
                            String repr = named != null ? named.getRepr(false).replace("*", "<star>") : null;
                            paramNames.add(repr != null ? repr : parameter instanceof PySlashParameter ? "/" : "*");
                        }
                        return new HintInfo.MethodInfo(fullQName, paramNames);
                    }
                }
            }
        }
        return null;
    }

    private static boolean isLiteralArgument(PyExpression expression) {
        return expression instanceof PyLiteralExpression ||
            expression instanceof PyListLiteralExpression ||
            expression instanceof PyDictLiteralExpression ||
            (expression instanceof PyParenthesizedExpression && expression.getChildren()[0] instanceof PyTupleExpression);
    }

    private static String toQNameForBuiltins(String qName) {
        List<String> components = new ArrayList<>();
        components.add("builtins");
        components.addAll(Arrays.asList(qName.split("\\.", -1)));
        return QualifiedName.fromComponents(components).toString();
    }

    @Override
    public List<InlayInfo> getParameterHints(PsiElement element, PsiFile file) {
        if (element instanceof PyArgumentList argumentList) {
            return getInlayInfoForArgumentList(argumentList);
        }
        return List.of();
    }

    @Override
    public Set<String> getDefaultBlackList() {
        return Set.of("builtins.*", "typing.*");
    }

    @Override
    public LocalizeValue getBlacklistExplanationHTML() {
        return PyCodeInsightLocalize.inlayParametersPythonHintsBlacklistExplanation(
            KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)
        );
    }

    @Override
    public List<Option> getSupportedOptions() {
        return List.of(Helper.showForClassConstructorCalls, Helper.showForNonLiteralArguments);
    }

    @Override
    public boolean isBlackListSupported() {
        return true;
    }

    @Override
    public LocalizeValue getPreviewFileText() {
        // TODO consulo: per-option previews are not supported; JB resources/inlayProviders/Parameters/python.show.class.constructor.call.parameter.names.py
        return LocalizeValue.of("""
            class Rectangle:
                def __init__(self, length, width):
                    self.length = length
                    self.width = width

            Rectangle(10, 4)""");
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
