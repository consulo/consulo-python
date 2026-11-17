// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.jetbrains.python.psi.types.PyType;
import consulo.platform.base.icon.PlatformIconGroup;

public class PyStructuralType implements PyType {
    private final Set<String> myAttributes;
    private final boolean myInferredFromUsages;

    public PyStructuralType(Set<String> attributes, boolean inferredFromUsages) {
        myAttributes = new LinkedHashSet<>(attributes);
        myInferredFromUsages = inferredFromUsages;
    }

    @Override
    public @Nullable List<? extends RatedResolveResult> resolveMember(String name,
                                                                      @Nullable PyExpression location,
                                                                      AccessDirection direction,
                                                                      PyResolveContext resolveContext) {
        return Collections.emptyList();
    }

    @Override
    public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
        List<Object> variants = new ArrayList<>();
        for (String attribute : myAttributes) {
            if (!attribute.equals(completionPrefix)) {
                variants.add(LookupElementBuilder.create(attribute).withIcon(
                    PlatformIconGroup.nodesField()));
            }
        }
        return variants.toArray();
    }

    @Override
    public @Nullable String getName() {
        return "{" + StringUtil.join(myAttributes, ", ") + "}";
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public void assertValid(String message) {
    }

    @Override
    public String toString() {
        return "PyStructuralType(" + StringUtil.join(myAttributes, ", ") + ")";
    }

    public boolean isInferredFromUsages() {
        return myInferredFromUsages;
    }

    public Set<String> getAttributeNames() {
        return Collections.unmodifiableSet(myAttributes);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PyStructuralType type = (PyStructuralType)o;
        return myInferredFromUsages == type.myInferredFromUsages && Objects.equals(myAttributes, type.myAttributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myAttributes, myInferredFromUsages);
    }
}
