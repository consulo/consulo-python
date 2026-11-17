// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.references;

import consulo.util.collection.Lists;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementDecorator;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.psi.ResolveResult;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PythonCodeStyleService;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.impl.codeInsight.completion.OverwriteEqualsInsertHandler;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyClassPattern;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordPattern;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PyKeywordPatternReference extends PsiReferenceBase.Poly<PyKeywordPattern> {
    public PyKeywordPatternReference(PyKeywordPattern keywordPattern) {
        super(keywordPattern, keywordPattern.getKeywordElement().getTextRangeInParent(), false);
    }

    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        PyClassPattern classPattern = getElement().getContainingClassPattern();
        if (classPattern == null) {
            return ResolveResult.EMPTY_ARRAY;
        }
        PyKeywordPattern keywordPattern = getElement();
        TypeEvalContext typeContext = TypeEvalContext.codeInsightFallback(keywordPattern.getProject());
        PyResolveContext resolveContext = PyResolveContext.defaultContext(typeContext);
        return resolveKeyword(classPattern, keywordPattern.getKeyword(), resolveContext);
    }

    public static ResolveResult[] resolveKeyword(PyClassPattern classPattern, String keyword, PyResolveContext resolveContext) {
        return resolveToClassTypes(classPattern, resolveContext).stream()
            .flatMap(t -> Lists.notNullize(t.resolveMember(keyword, null, AccessDirection.READ, resolveContext)).stream())
            .toArray(ResolveResult[]::new);
    }

    @Override
    public Object[] getVariants() {
        PyClassPattern classPattern = getElement().getContainingClassPattern();
        if (classPattern == null) {
            return LookupElement.EMPTY_ARRAY;
        }
        PyKeywordPattern keywordPattern = getElement();
        TypeEvalContext typeContext = TypeEvalContext.codeCompletion(keywordPattern.getProject(), keywordPattern.getContainingFile());
        return collectClassAttributeVariants(getElement(), classPattern, typeContext);
    }

    static LookupElement[] collectClassAttributeVariants(PsiElement location,
                                                                   PyClassPattern classPattern,
                                                                   TypeEvalContext typeContext) {
        PyResolveContext resolveContext = PyResolveContext.defaultContext(typeContext);
        return resolveToClassTypes(classPattern, resolveContext).stream()
            .flatMap(t -> Arrays.stream(t.getCompletionVariants("", location, new ProcessingContext())))
            .filter(LookupElement.class::isInstance)
            .map(LookupElement.class::cast)
            .filter(e -> isMeaningfulClassPatternAttribute(e))
            .map(e -> new KeywordAttributeLookupDecorator(e, location))
            .toArray(LookupElement[]::new);
    }

    private static boolean isMeaningfulClassPatternAttribute(LookupElement lookupElement) {
        String lookupString = lookupElement.getLookupString();
        if (lookupString.contains(".") || PyUtilCore.isSpecialName(lookupString)) {
            return false;
        }
        PsiElement elem = lookupElement.getPsiElement();
        if (elem instanceof PyClass) {
            return false;
        }
        if (elem instanceof PyFunction && ((PyFunction)elem).getProperty() == null) {
            return false;
        }
        return true;
    }

    private static List<PyClassLikeType> resolveToClassTypes(PyClassPattern classPattern,
                                                                      PyResolveContext resolveContext) {
        List<PsiElement> elements = PyUtil.multiResolveTopPriority(classPattern.getClassNameReference(), resolveContext);
        return elements.stream()
            .filter(PyClass.class::isInstance)
            .map(PyClass.class::cast)
            .map(e -> e.getType(resolveContext.getTypeEvalContext()))
            .filter(Objects::nonNull)
            .<PyClassLikeType>map(PyInstantiableType::toInstance)
            .toList();
    }

    private static class KeywordAttributeLookupDecorator extends LookupElementDecorator<LookupElement> {
        private final boolean myAddSpacesAroundEq;

        private KeywordAttributeLookupDecorator(LookupElement e, PsiElement settingsAnchor) {
            super(e);
            myAddSpacesAroundEq = PythonCodeStyleService.getInstance().isSpaceAroundEqInKeywordArgument(settingsAnchor.getContainingFile());
        }

        @Override
        public String getLookupString() {
            return super.getLookupString() + (myAddSpacesAroundEq ? " = " : "=");
        }

        @Override
        public Set<String> getAllLookupStrings() {
            return Collections.singleton(getLookupString());
        }

        @Override
        public void renderElement(LookupElementPresentation presentation) {
            super.renderElement(presentation);
            presentation.setItemText(getLookupString());
        }

        @Override
        public void handleInsert(InsertionContext context) {
            OverwriteEqualsInsertHandler.INSTANCE.handleInsert(context, this);
        }
    }
}
