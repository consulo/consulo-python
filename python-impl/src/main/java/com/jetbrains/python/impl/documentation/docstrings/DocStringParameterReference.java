// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.docstrings;

import consulo.language.editor.annotation.HighlightSeverity;
import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.inspections.PyInspectionMessages;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PsiReferenceEx;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class DocStringParameterReference extends PsiReferenceBase<PyStringLiteralExpression> implements PsiReferenceEx {
    private final ReferenceType myType;

    public DocStringParameterReference(PyStringLiteralExpression element, TextRange range, ReferenceType refType) {
        super(element, range);
        myType = refType;
    }

    public enum ReferenceType {PARAMETER, PARAMETER_TYPE, KEYWORD, VARIABLE, CLASS_VARIABLE, INSTANCE_VARIABLE, GLOBAL_VARIABLE}

    @Override
    public @Nullable PyElement resolve() {
        PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
        if (owner instanceof PyFunction) {
            return resolveParameter((PyFunction)owner);
        }
        if (owner instanceof PyClass pyClass) {
            PyFunction init = pyClass.findMethodByName(PyNames.INIT, false, null);
            if (myType == ReferenceType.PARAMETER) {
                return init != null ? resolveParameter(init) : resolveClassVariable(pyClass);
            }
            if (myType == ReferenceType.INSTANCE_VARIABLE || myType == ReferenceType.VARIABLE || myType == ReferenceType.PARAMETER_TYPE) {
                if (myType == ReferenceType.PARAMETER_TYPE && init != null) {
                    PyNamedParameter parameter = resolveParameter(init);
                    if (parameter != null) {
                        return parameter;
                    }
                }
                PyElement instanceAttr = resolveInstanceVariable(pyClass);
                return instanceAttr != null ? instanceAttr : resolveClassVariable(pyClass);
            }
            if (myType == ReferenceType.CLASS_VARIABLE) {
                return resolveClassVariable(pyClass);
            }
        }
        if (owner instanceof PyFile && myType == ReferenceType.GLOBAL_VARIABLE) {
            return resolveGlobalVariable(((PyFile)owner));
        }
        return null;
    }

    private @Nullable PyTargetExpression resolveGlobalVariable(PyFile owner) {
        return owner.findTopLevelAttribute(getCanonicalText());
    }

    private @Nullable PyTargetExpression resolveInstanceVariable(PyClass owner) {
        return owner.findInstanceAttribute(getCanonicalText(), true);
    }

    private @Nullable PyTargetExpression resolveClassVariable(PyClass owner) {
        return owner.findClassAttribute(getCanonicalText(), true, null);
    }

    private @Nullable PyNamedParameter resolveParameter(PyFunction owner) {
        PyParameterList parameterList = owner.getParameterList();
        PyNamedParameter resolved = parameterList.findParameterByName(getCanonicalText());
        if (resolved != null) {
            return resolved;
        }
        for (PyParameter parameter : parameterList.getParameters()) {
            if (parameter instanceof PyNamedParameter namedParameter) {
                if (namedParameter.isKeywordContainer() || namedParameter.isPositionalContainer()) {
                    return namedParameter;
                }
            }
        }
        return null;
    }

    @Override
    public Object[] getVariants() {
        // see PyDocstringCompletionContributor
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public List<PyNamedParameter> collectParameterVariants() {
        PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
        if (owner instanceof PyFunction) {
            List<PyNamedParameter> result = new ArrayList<>();
            List<PyNamedParameter> namedParameters = ParamHelper.collectNamedParameters(((PyFunction)owner).getParameterList());
            Set<String> usedParameters = new HashSet<>();
            PyStringLiteralExpression expression = PsiTreeUtil.getParentOfType(getElement(), PyStringLiteralExpression.class, false);
            if (expression != null) {
                PsiReference[] references = expression.getReferences();
                for (PsiReference ref : references) {
                    if (ref instanceof DocStringParameterReference && ((DocStringParameterReference)ref).getType().equals(myType)) {
                        usedParameters.add(ref.getCanonicalText());
                    }
                }
            }
            for (PyNamedParameter param : namedParameters) {
                if (!usedParameters.contains(param.getName())) {
                    result.add(param);
                }
            }

            return result;
        }
        return Collections.emptyList();
    }

    public ReferenceType getType() {
        return myType;
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
        return HighlightSeverity.WEAK_WARNING;
    }

    @Override
    public @Nullable String getUnresolvedDescription() {
        PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
        if (owner instanceof PyFunction function) {
            return PyInspectionMessages.problemMessage(params -> PyLocalize.unresolvedDocstringParamReference(params[0], params[1]), function.getName(), getCanonicalText())
                .description();
        }
        return null;
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        TextRange range = getRangeInElement();
        Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(range.substring(myElement.getText()));

        if (quotes != null) {
            range = TextRange.create(range.getStartOffset() + quotes.first.length(), range.getEndOffset() - quotes.second.length());
        }

        String newName = range.replace(myElement.getText(), newElementName);
        myElement.replace(PyElementGenerator.getInstance(myElement.getProject()).createStringLiteralAlreadyEscaped(newName));
        return myElement;
    }
}
