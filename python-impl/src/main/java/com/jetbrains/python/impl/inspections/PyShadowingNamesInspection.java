// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.inspections.PyInspectionExtension;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.PyResolveProcessor;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * Warns about shadowing names defined in outer scopes.
 *
 */
@ExtensionImpl
public final class PyShadowingNamesInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspShadowingNames();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyClass(PyClass node) {
            processElement(node);
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            processElement(node);
        }

        @Override
        public void visitPyNamedParameter(PyNamedParameter node) {
            if (node.isSelf()) {
                return;
            }
            processElement(node);
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            if (!node.isQualified()) {
                processElement(node);
            }
        }

        private void processElement(PsiNameIdentifierOwner element) {
            ScopeOwner owner = ScopeUtil.getScopeOwner(element);
            if (owner instanceof PyClass) {
                return;
            }
            String name = element.getName();
            if (name != null) {
                PsiElement identifier = element.getNameIdentifier();
                PsiElement problemElement = identifier != null ? identifier : element;
                if (PyNames.UNDERSCORE.equals(name) || name.startsWith(PyNames.UNDERSCORE) && element instanceof PyParameter) {
                    return;
                }
                if (owner != null) {
                    ScopeOwner nextOwner = ScopeUtil.getScopeOwner(owner);
                    if (nextOwner != null) {
                        PyResolveProcessor processor = new PyResolveProcessor(name);
                        PyResolveUtil.scopeCrawlUp(processor, nextOwner, null, name, null);
                        for (PsiElement resolved : processor.getElements()) {
                            if (resolved != null) {
                                PyComprehensionElement comprehension = PsiTreeUtil.getParentOfType(resolved, PyComprehensionElement.class);
                                if (comprehension != null && PyUtil.isOwnScopeComprehension(comprehension)) {
                                    return;
                                }
                                Scope scope = ControlFlowCache.getScope(owner);
                                if (scope.isGlobal(name) || scope.isNonlocal(name)) {
                                    return;
                                }
                                if (PyInspectionExtension.EP_NAME.findFirstSafe(Application.get(), o -> o.ignoreShadowed(resolved)) != null) {
                                    return;
                                }
                                registerProblem(problemElement, PyInspectionMessages.problemMessage(a -> PyLocalize.inspShadowsNameFromOuterScope(a[0]), name),
                                                ProblemHighlightType.WEAK_WARNING,
                                                LocalQuickFix.notNullElements(PythonUiService.getInstance().createPyRenameElementQuickFix(problemElement)));
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}

