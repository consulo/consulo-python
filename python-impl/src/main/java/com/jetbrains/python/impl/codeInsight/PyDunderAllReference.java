// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.LightNamedElement;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.document.util.TextRange;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiPolyVariantReferenceBase;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.collection.ArrayUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class PyDunderAllReference extends PsiPolyVariantReferenceBase<PyStringLiteralExpression> {
    public PyDunderAllReference(PyStringLiteralExpression element) {
        super(element);
        List<TextRange> ranges = element.getStringValueTextRanges();
        if (!ranges.isEmpty()) {
            setRangeInElement(ranges.get(0));
        }
    }

    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        PyStringLiteralExpression element = getElement();
        String name = element.getStringValue();
        PyFile file = (PyFile) element.getContainingFile();

        List<RatedResolveResult> resolveResults = file.multiResolveName(name);

        boolean onlyDunderAlls = resolveResults.stream()
            .map(RatedResolveResult::getElement)
            .allMatch(resolvedElement -> resolvedElement instanceof PyTargetExpression &&
                PyNames.ALL.equals(((PyTargetExpression) resolvedElement).getName()));

        if (onlyDunderAlls) {
            return ResolveResult.EMPTY_ARRAY;
        }

        return resolveResults.toArray(RatedResolveResult.EMPTY_ARRAY);
    }

    @Override
    public Object[] getVariants() {
        List<LookupElement> result = new ArrayList<>();

        PyFile containingFile = (PyFile) getElement().getContainingFile().getOriginalFile();

        List<String> dunderAll = containingFile.getDunderAll();
        Set<String> seenNames = new HashSet<>();
        if (dunderAll != null) {
            seenNames.addAll(dunderAll);
        }

        containingFile.processDeclarations(new PsiScopeProcessor() {
            @Override
            public boolean execute(PsiElement element, ResolveState state) {
                if (element instanceof PsiNamedElement && !(element instanceof LightNamedElement)) {
                    String name = ((PsiNamedElement) element).getName();
                    if (name != null && PyUtil.getInitialUnderscores(name) == 0 && !seenNames.contains(name)) {
                        seenNames.add(name);
                        result.add(toLookupElement(name, element, true));
                    }
                }
                else if (element instanceof PyImportElement) {
                    String visibleName = ((PyImportElement) element).getVisibleName();
                    if (visibleName != null && !seenNames.contains(visibleName)) {
                        seenNames.add(visibleName);
                        result.add(toLookupElement(visibleName, element, false));
                    }
                }
                return true;
            }
        }, ResolveState.initial(), null, containingFile);

        result.addAll(PyModuleType.getSubModuleVariants(containingFile.getParent(), getElement(), seenNames));

        return ArrayUtil.toObjectArray(result);
    }

    private static LookupElement toLookupElement(String name, PsiElement element, boolean withIcon) {
        LookupElementBuilder builder = LookupElementBuilder.createWithSmartPointer(name, element);
        return withIcon ? builder.withIcon(IconDescriptorUpdaters.getIcon(element, 0)) : builder;
    }
}
