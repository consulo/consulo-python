// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * User: catherine
 *
 * Intention to convert variadic parameter(s) to normal
 * For instance,
 *
 * from:
 * def foo(**kwargs):
 *   doSomething(kwargs['foo'])
 *
 * to:
 * def foo(foo, **kwargs):
 *   doSomething(foo)
 *
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.ConvertVariadicParamIntention", categories = "Python", fileExtensions = "py")
public final class ConvertVariadicParamIntention extends PyBaseIntentionAction {

    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnConvertVariadicParam();
    }


    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
        if (!(psiFile instanceof PyFile)) {
            return false;
        }

        PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
        PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);

        if (function != null) {
            if (LanguageLevel.forElement(function).isPython2() && function.getParameterList().hasPositionalContainer()) {
                return false;
            }

            Usages usages = findKeywordContainerUsages(function, false);

            if (usages.hasMoreReferences) {

                return false;

            }
            boolean caretInParameterList = PsiTreeUtil.isAncestor(function.getParameterList(), element, true);
            if (caretInParameterList && ContainerUtil.exists(usages.names.values(), n -> !n.unableToHasDefaultValue)) {
                return true;
            }

            for (Map.Entry<String, DefaultValue> nameAndValue : usages.names.entrySet()) {
                if (nameAndValue.getValue().unableToHasDefaultValue) {
                    continue;
                }
                String name = nameAndValue.getKey();
                if (ContainerUtil.exists(usages.calls.get(name), c -> PsiTreeUtil.isAncestor(c, element, true)) ||
                        ContainerUtil.exists(usages.subscriptions.get(name), s -> PsiTreeUtil.isAncestor(s, element, true))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static @Nullable PyParameter getKeywordContainer(PyParameterList parameterList) {
        return StreamEx
            .of(parameterList.getParameters())
            .select(PyNamedParameter.class)
            .findFirst(PyNamedParameter::isKeywordContainer)
            .orElse(null);
    }

    @Override
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);

        if (function != null) {
            replaceKeywordContainerUsages(function, project);
        }
    }

    private static Usages findKeywordContainerUsages(PyFunction function, boolean skipReferencesToContainer) {
        PyParameter keywordContainer = getKeywordContainer(function.getParameterList());
        if (keywordContainer == null) {
            return new Usages(false, new LinkedHashMap<>(), MultiMap.empty(), MultiMap.empty());
        }
        Set<PyExpression> allReferences = new HashSet<>();
        Set<PyExpression> referencesAsOperandOrReceiver = new HashSet<>();
        LinkedHashMap<String, DefaultValue> names = new LinkedHashMap<>();
        MultiMap<String, PyCallExpression> calls = MultiMap.create();
        MultiMap<String, PySubscriptionExpression> subscriptions = MultiMap.create();

        SyntaxTraverser
            .psiTraverser(function.getStatementList())
            .forEach(
                    e -> {
                    if (!skipReferencesToContainer &&
                            e instanceof PyReferenceExpression &&
                            ((PyReferenceExpression)e).getReference().isReferenceTo(keywordContainer)) {
                        allReferences.add((PyReferenceExpression)e);
                    }
                    else if (isKeywordContainerCall(e, keywordContainer)) {
                        PyCallExpression call = (PyCallExpression)e;
                        PyExpression receiver = call.getCallee() instanceof PyQualifiedExpression callee ? callee.getQualifier() : null;
                        referencesAsOperandOrReceiver.add(receiver);

                        String name = getIndexValueToReplace(call);
                        if (name != null) {
                            names.merge(name, getDefaultKeyValue(call), DefaultValue::merge);
                            calls.putValue(name, call);
                        }
                    }
                    else if (isKeywordContainerSubscription(e, keywordContainer)) {
                        PySubscriptionExpression subscription = (PySubscriptionExpression)e;
                        referencesAsOperandOrReceiver.add(subscription.getOperand());

                        String name = getIndexValueToReplace(subscription);
                        if (name != null) {
                            names.merge(name, DefaultValue.noDefault(), DefaultValue::merge);
                            subscriptions.putValue(name, subscription);
                        }
                    }
                }
            );

        allReferences.removeAll(referencesAsOperandOrReceiver);
        return new Usages(!allReferences.isEmpty(), names, calls, subscriptions);
    }

    private static @Nullable String getIndexValueToReplace(PySubscriptionExpression subscription) {
        return Optional
            .ofNullable(subscription.getIndexExpression())
            .map(indexExpression -> PyUtil.as(indexExpression, PyStringLiteralExpression.class))
            .map(PyStringLiteralExpression::getStringValue)
            .filter(PyNames::isIdentifier)
            .orElse(null);
    }

    private static @Nullable String getIndexValueToReplace(PyCallExpression call) {
        return Optional
            .of(call.getArguments())
            .map(ArrayUtil::getFirstElement)
            .map(firstArgument -> PyUtil.as(firstArgument, PyStringLiteralExpression.class))
            .map(PyStringLiteralExpression::getStringValue)
            .filter(PyNames::isIdentifier)
            .orElse(null);
    }

    private static void replaceKeywordContainerUsages(PyFunction function, Project project) {
        Usages usages = findKeywordContainerUsages(function, true);

        MultiMap<String, PySubscriptionExpression> subscriptions = usages.subscriptions;
        MultiMap<String, PyCallExpression> calls = usages.calls;

        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        for (Map.Entry<String, DefaultValue> nameAndValue : usages.names.entrySet()) {
            if (nameAndValue.getValue().unableToHasDefaultValue) {
                continue;
            }
            String name = nameAndValue.getKey();
            String defaultValue = nameAndValue.getValue().defaultValue;

            LanguageLevel languageLevel = LanguageLevel.forElement(function);

            PyNamedParameter parameter = elementGenerator.createParameter(name, defaultValue, null, languageLevel);
            insertParameter(function.getParameterList(), parameter, parameter.hasDefaultValue(), elementGenerator);

            PyExpression parameterUsage = elementGenerator.createExpressionFromText(languageLevel, name);
            subscriptions.get(name).forEach(e -> e.replace(parameterUsage));
            calls.get(name).forEach(e -> e.replace(parameterUsage));
        }
    }

    private static boolean isKeywordContainerSubscription(@Nullable PsiElement element, PyParameter keywordContainer) {
        if (element instanceof PySubscriptionExpression) {
            PyExpression operand = ((PySubscriptionExpression)element).getOperand();
            if (operand instanceof PyReferenceExpression) {
                return ((PyReferenceExpression)operand).getReference().isReferenceTo(keywordContainer);
            }
        }

        return false;
    }

    private static boolean isKeywordContainerCall(@Nullable PsiElement element, PyParameter keywordContainer) {
        if (element instanceof PyCallExpression) {
            PyExpression callee = ((PyCallExpression)element).getCallee();
            if (callee instanceof PyQualifiedExpression qualifiedCallee) {
                PyExpression qualifier = qualifiedCallee.getQualifier();

                if (qualifier instanceof PyReferenceExpression) {
                    return ((PyReferenceExpression)qualifier).getReference().isReferenceTo(keywordContainer) &&
                            ArrayUtil.contains(qualifiedCallee.getReferencedName(), "get", "pop", PyNames.GETITEM);
                }
            }
        }
        return false;
    }

    private static void insertParameter(PyParameterList parameterList,
            PyElement parameter,
            boolean hasDefaultValue,
            PyElementGenerator elementGenerator) {
        PyElement placeToInsertParameter = findCompatiblePlaceToInsertParameter(parameterList, hasDefaultValue);

        parameterList.addBefore(parameter, placeToInsertParameter);
        parameterList.addBefore((PsiElement)elementGenerator.createComma(), placeToInsertParameter);
    }

    private static DefaultValue getDefaultKeyValue(PyCallExpression call) {
        PyExpression[] arguments = call.getArguments();
        if (arguments.length > 1) {
            PyExpression argument = PyUtil.peelArgument(arguments[1]);
            String value = argument == null ? null : argument.getText();
            return value == null ? DefaultValue.noDefault() : DefaultValue.defaultValue(value);
        }

        PyQualifiedExpression callee = PyUtil.as(call.getCallee(), PyQualifiedExpression.class);
        if (callee != null && "get".equals(callee.getReferencedName())) {
            return DefaultValue.defaultValue(PyNames.NONE);
        }

        return DefaultValue.noDefault();
    }

    private static @Nullable PyElement findCompatiblePlaceToInsertParameter(PyParameterList parameterList, boolean hasDefaultValue) {
        if (hasDefaultValue) {
            return getKeywordContainer(parameterList);
        }
        List<PyParameter> parameters = Arrays.asList(parameterList.getParameters());
        for (Pair<PyParameter, PyParameter> currentAndNext : ContainerUtil.zip(parameters, parameters.subList(1, parameters.size()))) {
            PyParameter current = currentAndNext.getFirst();
            if (current instanceof PyNamedParameter && ((PyNamedParameter)current).isPositionalContainer()) {
                return currentAndNext.getSecond();
            }
        }

        return ContainerUtil.find(parameterList.getParameters(),
                p -> p.hasDefaultValue() || p instanceof PyNamedParameter && ((PyNamedParameter)p).isKeywordContainer());
    }

    private static final class Usages {

        private final boolean hasMoreReferences;

        private final LinkedHashMap<String, DefaultValue> names;

        private final MultiMap<String, PyCallExpression> calls;

        private final MultiMap<String, PySubscriptionExpression> subscriptions;

        private Usages(boolean hasMoreReferences,
                LinkedHashMap<String, DefaultValue> names,
                MultiMap<String, PyCallExpression> calls,
                MultiMap<String, PySubscriptionExpression> subscriptions) {
            this.hasMoreReferences = hasMoreReferences;
            this.names = names;
            this.calls = calls;
            this.subscriptions = subscriptions;
        }
    }

    private static final class DefaultValue {

        private final boolean unableToHasDefaultValue;

        private final @Nullable String defaultValue;

        private static DefaultValue noDefault() {
            return new DefaultValue(false, null);
        }

        private static DefaultValue defaultValue(String defaultValue) {
            return new DefaultValue(false, defaultValue);
        }

        private DefaultValue(boolean unableToHasDefaultValue, @Nullable String defaultValue) {
            this.unableToHasDefaultValue = unableToHasDefaultValue;
            this.defaultValue = defaultValue;
        }

        private static DefaultValue merge(DefaultValue self, DefaultValue other) {
            if (self.unableToHasDefaultValue) {
                return self;
            }
            if (other.unableToHasDefaultValue) {
                return other;
            }
            if (self.defaultValue == null) {

                return self;

            }
            if (other.defaultValue == null) {
                return other;
            }
            if (Objects.equals(self.defaultValue, other.defaultValue)) {

                return self;

            }
            return new DefaultValue(true, null);
        }
    }
}

