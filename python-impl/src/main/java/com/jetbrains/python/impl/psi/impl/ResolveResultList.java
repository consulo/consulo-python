// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.impl.PyPsiUtils;

import com.google.common.collect.Lists;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.ResolveResult;
import consulo.language.impl.psi.LightElement;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.impl.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResolveResultList extends ArrayList<RatedResolveResult> {
    public static List<RatedResolveResult> to(PsiElement element) {
        if (element == null) {
            return Collections.emptyList();
        }
        ResolveResultList list = new ResolveResultList();
        list.poke(element, RatedResolveResult.RATE_NORMAL);
        return list;
    }

    public static List<? extends RatedResolveResult> asImportedResults(@Nullable List<? extends RatedResolveResult> from,
                                                                       @Nullable PyImportedNameDefiner nameDefiner) {
        if (ContainerUtil.isEmpty(from)) {
            return Collections.emptyList();
        }
        return ContainerUtil.map(from, res -> new ImportedResolveResult(res.getElement(), res.getRate(), nameDefiner));
    }


    public static List<PsiElement> getElements(List<? extends ResolveResult> from) {
        if (from.isEmpty()) {
            return Collections.emptyList();
        }
        return Lists.transform(from, res -> res != null ? res.getElement() : null);
    }

    // Allows to add non-null elements and discard nulls in a hassle-free way.

    public boolean poke(PsiElement what, int rate) {
        PyPsiUtils.assertValid(what);
        if (what == null) {
            return false;
        }
        if (!(what instanceof LightElement) && !what.isValid()) {
            throw new PsiInvalidElementAccessException(what, "Trying to resolve a reference to an invalid element");
        }
        super.add(new RatedResolveResult(rate, what));
        return true;
    }
}
