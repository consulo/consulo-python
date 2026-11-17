// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.references;

import consulo.language.impl.psi.stub.StubBasedPsiElementBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementResolveResult;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.psi.PyCapturePattern;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyClassPattern;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyPatternArgumentList;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.impl.psi.PyUtil.as;

public class PyTargetReference extends PyReferenceImpl {
    public PyTargetReference(PyQualifiedExpression element, PyResolveContext context) {
        super(element, context);
    }

    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        if (myElement instanceof StubBasedPsiElementBase && ((StubBasedPsiElementBase<?>)myElement).getStub() == null ||
            myContext.getTypeEvalContext().maySwitchToAST(myElement)) {
            ResolveResult[] results = super.multiResolve(incompleteCode);
            boolean shadowed = false;
            for (ResolveResult result : results) {
                PsiElement element = result.getElement();
                if (element != null && (element.getContainingFile() != myElement.getContainingFile() ||
                                        element instanceof PyFunction || element instanceof PyClass)) {
                    shadowed = true;
                    break;
                }
            }
            if (results.length > 0 && !shadowed) {
                return results;
            }
        }
        // resolve to self if no other target found
        return new ResolveResult[]{new PsiElementResolveResult(myElement)};
    }

    @Override
    public Object[] getVariants() {
        PyImportElement importElement = PsiTreeUtil.getParentOfType(myElement, PyImportElement.class);
        // reference completion is useless in 'as' part of import statement (PY-2384)
        if (importElement != null && myElement == importElement.getAsNameElement()) {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }
        PyClassPattern classPattern = getContainingClassPatternIfInAttributeNamePosition();
        if (classPattern != null) {
            TypeEvalContext context = TypeEvalContext.codeCompletion(myElement.getProject(), myElement.getContainingFile());
            return PyKeywordPatternReference.collectClassAttributeVariants(myElement, classPattern, context);
        }
        return super.getVariants();
    }

    private @Nullable PyClassPattern getContainingClassPatternIfInAttributeNamePosition() {
        PsiElement grandParent = myElement.getParent().getParent();
        if (myElement.getParent() instanceof PyCapturePattern && grandParent instanceof PyPatternArgumentList) {
            return as(grandParent.getParent(), PyClassPattern.class);
        }
        return null;
    }
}
