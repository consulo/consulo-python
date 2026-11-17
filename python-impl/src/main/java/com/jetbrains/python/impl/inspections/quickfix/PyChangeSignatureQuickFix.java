// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.jetbrains.python.impl.refactoring.NameSuggesterUtil;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.impl.refactoring.changeSignature.PyChangeSignatureDialog;
import com.jetbrains.python.impl.refactoring.changeSignature.PyMethodDescriptor;
import com.jetbrains.python.impl.refactoring.changeSignature.PyParameterInfo;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallExpression.PyArgumentsMapping;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.Application;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElement;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.xml.XmlStringUtil;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.impl.refactoring.changeSignature.PyParameterInfo.NEW_PARAMETER;

public final class PyChangeSignatureQuickFix extends LocalQuickFixOnPsiElement {

    public static final Key<Boolean> CHANGE_SIGNATURE_ORIGINAL_CALL = Key.create("CHANGE_SIGNATURE_ORIGINAL_CALL");

    public static PyChangeSignatureQuickFix forMismatchedCall(PyArgumentsMapping mapping) {
        assert mapping.getCallableType() != null;
        PyFunction function = as(mapping.getCallableType().getCallable(), PyFunction.class);
        assert function != null;
        Supplier<List<Pair<Integer, PyParameterInfo>>> extraParamsSupplier = () -> {
            PyElement callSiteExpression = mapping.getCallSite();
            int positionalParamAnchor = -1;
            PyParameter[] parameters = function.getParameterList().getParameters();
            for (PyParameter parameter : parameters) {
                PyNamedParameter namedParam = parameter.getAsNamed();
                boolean isVararg = namedParam != null && (namedParam.isPositionalContainer() || namedParam.isKeywordContainer());
                if (parameter instanceof PySingleStarParameter || parameter.hasDefaultValue() || isVararg) {
                    break;
                }
                positionalParamAnchor++;
            }
            List<Pair<Integer, PyParameterInfo>> newParameters = new ArrayList<>();
            TypeEvalContext context = TypeEvalContext.userInitiated(function.getProject(), callSiteExpression.getContainingFile());
            Set<String> usedParamNames = new HashSet<>();
            for (PyExpression arg : mapping.getUnmappedArguments()) {
                if (arg instanceof PyKeywordArgument keywordArgument) {
                    PyExpression value = keywordArgument.getValueExpression();
                    String valueText = value != null ? value.getText() : "";
                    newParameters.add(Pair.create(
                        parameters.length - 1,
                        new PyParameterInfo(NEW_PARAMETER, keywordArgument.getKeyword(), valueText, true)
                    ));
                }
                else {
                    String paramName = generateParameterName(arg, function, usedParamNames, context);
                    newParameters.add(Pair.create(positionalParamAnchor, new PyParameterInfo(NEW_PARAMETER, paramName, arg.getText(), false)));
                    usedParamNames.add(paramName);
                }
            }
            return newParameters;
        };
        return new PyChangeSignatureQuickFix(function, extraParamsSupplier, mapping.getCallSite());
    }

    public static PyChangeSignatureQuickFix forMismatchingMethods(PyFunction function, PyFunction complementary) {
        Supplier<List<Pair<Integer, PyParameterInfo>>> extraParamsSupplier = () -> {
            int paramLength = function.getParameterList().getParameters().length;
            int complementaryParamLength = complementary.getParameterList().getParameters().length;
            List<Pair<Integer, PyParameterInfo>> extraParams;
            if (complementaryParamLength > paramLength) {
                extraParams = Collections.singletonList(Pair.create(paramLength - 1, new PyParameterInfo(NEW_PARAMETER, "**kwargs", "", false)));
            }
            else {
                extraParams = Collections.emptyList();
            }
            return extraParams;
        };
        return new PyChangeSignatureQuickFix(function, extraParamsSupplier, null);
    }

    private final Supplier<List<Pair<Integer, PyParameterInfo>>> myExtraParametersSupplier;
    private final @Nullable SmartPsiElementPointer<PyElement> myOriginalCallSiteExpression;

    /**
     * @param extraParametersSupplier supplies new parameters anchored by indexes of the existing parameters they should be inserted <em>after</em>
     *                                (-1 in case they should precede the first parameter)
     */
    private PyChangeSignatureQuickFix(
        PyFunction function,
        Supplier<List<Pair<Integer, PyParameterInfo>>> extraParametersSupplier,
        @Nullable PyElement expression
    ) {
        super(function);
        myExtraParametersSupplier = () -> ContainerUtil.sorted(extraParametersSupplier.get(), Comparator.comparingInt(p -> p.getFirst()));
        if (expression != null) {
            myOriginalCallSiteExpression = SmartPointerManager.getInstance(function.getProject()).createSmartPsiElementPointer(expression);
        }
        else {
            myOriginalCallSiteExpression = null;
        }
    }

    // Consulo: LocalQuickFixOnPsiElement#getName() is final and returns getText(), JB getFamilyName() is kept as a helper
    public LocalizeValue getFamilyName() {
        return PyLocalize.qfixNameChangeSignature();
    }

    @Override
    public LocalizeValue getText() {
        PyFunction function = getFunction();
        if (function == null) {
            return getFamilyName();
        }
        List<PyParameterInfo> parameters = new PyMethodDescriptor(function).getParameters();
        String params = StringUtil.join(
            parameters,
            info -> info.isNew() ? PyLocalize.qfixBoldHtmlText(info.getName()).get() : info.getName(),
            ", "
        );

        LocalizeValue message = PyLocalize.qfixChangeSignatureOf(StringUtil.notNullize(function.getName()) + "(" + params + ")");
        return message.map(XmlStringUtil::wrapInHtml);
    }

    private @Nullable PyFunction getFunction() {
        return (PyFunction) getStartElement();
    }

    @Override
    @RequiredUIAccess
    public void invoke(Project project, PsiFile psiFile, PsiElement startElement, PsiElement endElement) {
        PyFunction function = getFunction();
        PyMethodDescriptor descriptor = createMethodDescriptor(function);

        PyChangeSignatureDialog dialog = new PyChangeSignatureDialog(project, descriptor) {
            // Similar to JavaChangeSignatureDialog.createAndPreselectNew()
            @Override
            protected int getSelectedIdx() {
                return (int) StreamEx.of(getParameters()).indexOf(info -> info.getOldIndex() < 0).orElse(super.getSelectedIdx());
            }
        };

        PyElement originalCallSite = myOriginalCallSiteExpression != null ? myOriginalCallSiteExpression.getElement() : null;
        try {
            if (originalCallSite != null) {
                originalCallSite.putUserData(CHANGE_SIGNATURE_ORIGINAL_CALL, true);
            }
            if (Application.get().isUnitTestMode()) {
                BaseRefactoringProcessor processor = dialog.createRefactoringProcessor();
                dialog.close(DialogWrapper.OK_EXIT_CODE);
                processor.run();
            }
            else {
                dialog.show();
            }
        }
        finally {
            if (originalCallSite != null) {
                originalCallSite.putUserData(CHANGE_SIGNATURE_ORIGINAL_CALL, null);
            }
        }
    }

    private static String generateParameterName(
        PyExpression argumentValue,
        PyFunction function,
        Set<String> usedParameterNames,
        TypeEvalContext context
    ) {
        Collection<String> suggestions = new LinkedHashSet<>();
        PyCallExpression callExpr = as(argumentValue, PyCallExpression.class);
        PyElement referenceElem = as(callExpr != null ? callExpr.getCallee() : argumentValue, PyReferenceExpression.class);
        if (referenceElem != null) {
            suggestions.addAll(NameSuggesterUtil.generateNames(referenceElem.getText()));
        }
        if (suggestions.isEmpty()) {
            PyType type = context.getType(argumentValue);
            if (type instanceof PyUnionType unionType) {
                type = ContainerUtil.findInstance(unionType.getMembers(), PyClassType.class);
            }
            String typeName = type != null && type.getName() != null ? type.getName() : "object";
            suggestions.addAll(NameSuggesterUtil.generateNamesByType(typeName));
        }
        String shortestName = Collections.min(suggestions, Comparator.comparingInt(String::length));

        String result = shortestName;
        int counter = 1;
        while (!PyRefactoringUtil.isValidNewName(result, function.getStatementList()) || usedParameterNames.contains(result)) {
            result = shortestName + counter;
            counter++;
        }
        return result;
    }

    private PyMethodDescriptor createMethodDescriptor(PyFunction function) {
        return new PyMethodDescriptor(function) {
            private final List<Pair<Integer, PyParameterInfo>> myExtraParameters = myExtraParametersSupplier.get();

            @Override
            public List<PyParameterInfo> getParameters() {
                List<PyParameterInfo> result = new ArrayList<>();
                List<PyParameterInfo> originalParams = super.getParameters();
                PeekingIterator<Pair<Integer, PyParameterInfo>> extra = Iterators.peekingIterator(myExtraParameters.iterator());
                while (extra.hasNext() && extra.peek().getFirst() < 0) {
                    result.add(extra.next().getSecond());
                }
                for (int i = 0; i < originalParams.size(); i++) {
                    result.add(originalParams.get(i));
                    while (extra.hasNext() && extra.peek().getFirst() == i) {
                        result.add(extra.next().getSecond());
                    }
                }
                return result;
            }
        };
    }

    // TODO consulo: LocalQuickFix#getElementToMakeWritable missing
    public @Nullable PsiElement getElementToMakeWritable(PsiFile currentFile) {
        return getFunction();
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
