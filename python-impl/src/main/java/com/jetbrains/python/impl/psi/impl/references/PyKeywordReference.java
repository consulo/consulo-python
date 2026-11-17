// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.references;

import consulo.document.util.TextRange;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.util.ArrayList;
import java.util.List;

public final class PyKeywordReference extends PsiReferenceBase.Poly<PyElement> {

    private final PyResolveContext myContext;

    public PyKeywordReference(PyElement owner, PyResolveContext context, TextRange rangeInOwner) {
        super(owner, rangeInOwner, false);
        myContext = context;
    }

    @Override
    public boolean isSoft() {
        return true;
    }

    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        PyElement element = getElement();
        if (element instanceof PyForPart forPart) {
            PyExpression source = forPart.getSource();
            if (source == null) {
                return ResolveResult.EMPTY_ARRAY;
            }

            TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
            PyType type = typeEvalContext.getType(source);
            if (type == null) {
                return ResolveResult.EMPTY_ARRAY;
            }

            List<RatedResolveResult> results = new ArrayList<>();

            boolean isAsync = false;
            PyForStatement forStatement = PsiTreeUtil.getParentOfType(forPart, PyForStatement.class);
            if (forStatement != null) {
                isAsync = forStatement.isAsync();
            }

            String iterName = isAsync ? PyNames.AITER : PyNames.ITER;

            var members = type.resolveMember(iterName, source, AccessDirection.READ, myContext);
            if (members != null) {
                results.addAll(members);
            }

            if (results.isEmpty()) {
                members = type.resolveMember(PyNames.GETITEM, source, AccessDirection.READ, myContext);
                if (members != null) {
                    results.addAll(members);
                }
            }

            return RatedResolveResult.sorted(results).toArray(ResolveResult.EMPTY_ARRAY);
        }
        return ResolveResult.EMPTY_ARRAY;
    }
}
