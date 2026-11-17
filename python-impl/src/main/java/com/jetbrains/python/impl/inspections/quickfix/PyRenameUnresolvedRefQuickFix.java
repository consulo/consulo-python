
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PythonTemplateRunner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.references.PyReferenceBase;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.template.TextResult;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * User : ktisha
 * <p>
 * Quick fix to rename unresolved references
 */
public class PyRenameUnresolvedRefQuickFix implements LocalQuickFix {

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixRenameUnresolvedReference();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PyReferenceExpression referenceExpression)) {
            return;
        }

        ScopeOwner parentScope = ScopeUtil.getScopeOwner(referenceExpression);
        if (parentScope == null) {
            return;
        }

        List<PyReferenceExpression> refs = collectExpressionsToRename(referenceExpression, parentScope);

        LookupElement[] items = collectLookupItems(parentScope);
        String name = referenceExpression.getReferencedName();

        ReferenceNameExpression refExpr = new ReferenceNameExpression(items, name);
        // Consulo: TemplateBuilderImpl is internal, TemplateBuilder exposes the same replaceElement API
        TemplateBuilder builder = TemplateBuilderFactory.getInstance().
            createTemplateBuilder(parentScope);
        for (PyReferenceExpression expr : refs) {
            if (!expr.equals(referenceExpression)) {
                builder.replaceElement(expr, name, name, false);
            }
            else {
                builder.replaceElement(expr, name, refExpr, true);
            }
        }

        PythonTemplateRunner.runInlineTemplate(project, element, builder, parentScope.getTextRange().getStartOffset());
    }

    public static boolean isValidReference(PsiReference reference) {
        if (!(reference instanceof PyReferenceBase pyReference)) {
            return false;
        }
        ResolveResult[] results = pyReference.multiResolve(true);
        if (results.length == 0) {
            return false;
        }
        for (ResolveResult result : results) {
            if (!result.isValidResult()) {
                return false;
            }
        }
        return true;
    }

    private static List<PyReferenceExpression> collectExpressionsToRename(PyReferenceExpression expression,
            ScopeOwner parentScope) {

        List<PyReferenceExpression> result = new ArrayList<>();
        PyRecursiveElementVisitor visitor = new PyRecursiveElementVisitor() {
            @Override
            public void visitPyReferenceExpression(PyReferenceExpression node) {
                if (node.textMatches(expression) && !isValidReference(node.getReference())) {
                    result.add(node);
                }
                super.visitPyReferenceExpression(node);
            }
        };

        parentScope.accept(visitor);
        return result;
    }

    private static LookupElement[] collectLookupItems(ScopeOwner parentScope) {
        Set<LookupElement> items = new LinkedHashSet<>();

        Collection<String> usedNames = PyUtil.collectUsedNames(parentScope);
        for (String name : usedNames) {
            if (name != null) {
                items.add(LookupElementBuilder.create(name));
            }
        }

        return items.toArray(LookupElement.EMPTY_ARRAY);
    }

    private static class ReferenceNameExpression extends Expression {
        class HammingComparator implements Comparator<LookupElement> {
            @Override
            public int compare(LookupElement lookupItem1, LookupElement lookupItem2) {
                String s1 = lookupItem1.getLookupString();
                String s2 = lookupItem2.getLookupString();
                int diff1 = 0;
                for (int i = 0; i < Math.min(s1.length(), myOldReferenceName.length()); i++) {
                    if (s1.charAt(i) != myOldReferenceName.charAt(i)) {
                        diff1++;
                    }
                }
                int diff2 = 0;
                for (int i = 0; i < Math.min(s2.length(), myOldReferenceName.length()); i++) {
                    if (s2.charAt(i) != myOldReferenceName.charAt(i)) {
                        diff2++;
                    }
                }
                return diff1 - diff2;
            }
        }

        ReferenceNameExpression(LookupElement[] items, String oldReferenceName) {
            myItems = items;
            myOldReferenceName = oldReferenceName;
            Arrays.sort(myItems, new HammingComparator());
        }

        LookupElement[] myItems;
        private final String myOldReferenceName;

        @Override
        public Result calculateResult(ExpressionContext context) {
            if (myItems == null || myItems.length == 0) {
                return new TextResult(myOldReferenceName);
            }
            return new TextResult(myItems[0].getLookupString());
        }

        @Override
        public Result calculateQuickResult(ExpressionContext context) {
            return null;
        }

        @Override
        public LookupElement[] calculateLookupItems(ExpressionContext context) {
            if (myItems == null || myItems.length == 1) {
                return null;
            }
            return myItems;
        }
    }
}
