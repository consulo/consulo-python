// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.liveTemplates;

import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.macro.Macro;
import consulo.language.editor.template.PsiElementResult;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.context.TemplateContextType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyImplicitImportNameDefiner;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.impl.psi.types.PyABCUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

@ExtensionImpl
public final class PyIterableVariableMacro extends Macro {
    @Override
    public String getName() {
        return "pyIterableVariable";
    }

    @Override
    public LocalizeValue getPresentableName() {
        return LocalizeValue.of("pyIterableVariable()");
    }

    @Override
    public @Nullable Result calculateResult(Expression[] params, ExpressionContext context) {
        PsiElement element = context.getPsiElementAtStartOffset();
        if (element != null) {
            List<PsiNamedElement> components = getIterableElements(element);
            if (!components.isEmpty()) {
                if (components.get(0) instanceof PyNamedParameter namedParameter) {
                    return new PsiElementResult(namedParameter.getNameIdentifier());
                }
                return new PsiElementResult(components.get(0));
            }
        }
        return null;
    }

    @Override
    public LookupElement @Nullable [] calculateLookupItems(Expression[] params, ExpressionContext context) {
        List<LookupElement> results = new ArrayList<>();
        PsiElement element = context.getPsiElementAtStartOffset();
        if (element != null) {
            for (PsiNamedElement iterableElement : getIterableElements(element)) {
                String name = iterableElement.getName();
                if (name != null) {
                    results.add(LookupElementBuilder.createWithSmartPointer(name, iterableElement));
                }
            }
        }
        return results.toArray(LookupElement.EMPTY_ARRAY);
    }

    @Override
    public boolean isAcceptableInContext(TemplateContextType context) {
        return context instanceof PythonTemplateContextType;
    }

    private static List<PsiNamedElement> getIterableElements(PsiElement element) {
        TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
        List<PsiNamedElement> components = new ArrayList<>();
        for (PsiNamedElement namedElement : getVisibleNamedElements(element)) {
            if (namedElement instanceof PyTypedElement) {
                PyType type = typeEvalContext.getType((PyTypedElement)namedElement);
                if (type != null && PyABCUtil.isSubtype(type, PyNames.ITERABLE, typeEvalContext)) {
                    components.add(namedElement);
                }
            }
        }
        return components;
    }

    private static List<PsiNamedElement> getVisibleNamedElements(PsiElement anchor) {
        List<PsiNamedElement> results = new ArrayList<>();
        for (ScopeOwner owner = ScopeUtil.getScopeOwner(anchor); owner != null; owner = ScopeUtil.getScopeOwner(owner)) {
            Scope scope = ControlFlowCache.getScope(owner);
            results.addAll(scope.getNamedElements());

            scope.getImportedNameDefiners()
                .stream()
                .filter(definer -> !(definer instanceof PyImplicitImportNameDefiner))
                .flatMap(definer -> StreamSupport.stream(definer.iterateNames().spliterator(), false))
                .filter(PsiNamedElement.class::isInstance)
                .map(PsiNamedElement.class::cast)
                .forEach(results::add);
        }
        return results;
    }
}
