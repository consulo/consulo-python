// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.DocstringQuickFix;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class PyMissingOrEmptyDocstringInspection extends PyBaseDocstringInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameMissingOrEmptyDocstring();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonDocstrings();
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    @Override
    public Visitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly, LocalInspectionToolSession session, Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session)) {
            @Override
            protected void checkDocString(PyDocStringOwner node) {
                PyStringLiteralExpression docStringExpression = node.getDocStringExpression();
                if (docStringExpression == null) {
                    for (PyInspectionExtension extension : PyInspectionExtension.EP_NAME.getExtensionList()) {
                        if (extension.ignoreMissingDocstring(node)) {
                            return;
                        }
                    }
                    PsiElement marker = null;
                    if (node instanceof PyClass) {
                        ASTNode n = ((PyClass) node).getNameNode();
                        if (n != null) {
                            marker = n.getPsi();
                        }
                    }
                    else if (node instanceof PyFunction) {
                        ASTNode n = ((PyFunction) node).getNameNode();
                        if (n != null) {
                            marker = n.getPsi();
                        }
                    }
                    else if (node instanceof PyFile) {
                        TextRange tr = new TextRange(0, 0);
                        ProblemsHolder holder = getHolder();
                        if (holder != null) {
                            holder.newProblem(PyLocalize.inspNoDocstring())
                                .range(node, tr)
                                .create();
                        }
                        return;
                    }
                    if (marker == null) {
                        marker = node;
                    }
                    if (node instanceof PyFunction pyFunction && !superFunctionHasDoc(pyFunction, myTypeEvalContext)) {
                        registerProblem(marker, PyLocalize.inspNoDocstring().get(), new DocstringQuickFix(null, null));
                    }
                    else if (node instanceof PyClass pyClass && !superClassHasDoc(pyClass, myTypeEvalContext)) {
                        if (pyClass.findInitOrNew(false, myTypeEvalContext) != null) {
                            registerProblem(marker, PyLocalize.inspNoDocstring().get(), new DocstringQuickFix(null, null));
                        }
                        else {
                            registerProblem(marker, PyLocalize.inspNoDocstring().get());
                        }
                    }
                }
                else if (StringUtil.isEmptyOrSpaces(docStringExpression.getStringValue())) {
                    registerProblem(docStringExpression, PyLocalize.inspEmptyDocstring().get());
                }
            }
        };
    }

    private static boolean superClassHasDoc(PyClass pyClass, TypeEvalContext context) {
        for (PyClass ancestor : pyClass.getAncestorClasses(context)) {
            if (StringUtil.isNotEmpty(ancestor.getDocStringValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean superFunctionHasDoc(PyFunction pyFunction, TypeEvalContext context) {
        PyClass containingClass = pyFunction.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        for (PyClass ancestor : containingClass.getAncestorClasses(context)) {
            PyFunction superFunction = ancestor.findMethodByName(pyFunction.getName(), false, context);
            if (superFunction != null && StringUtil.isNotEmpty(superFunction.getDocStringValue())) {
                return true;
            }
        }
        return false;
    }
}
