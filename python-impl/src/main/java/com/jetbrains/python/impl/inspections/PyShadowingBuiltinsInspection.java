// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Warns about shadowing built-in names.
 *
 */
@ExtensionImpl
public final class PyShadowingBuiltinsInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameShadowingBuiltins();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    private static LocalQuickFix[] createQuickFixes(String name, PsiElement problemElement) {
        List<LocalQuickFix> fixes = new ArrayList<>();
        LocalQuickFix qf = PythonUiService.getInstance().createPyRenameElementQuickFix(problemElement);
        if (qf != null) {
            fixes.add(qf);
        }
        fixes.add(new PyIgnoreBuiltinQuickFix(name));
        return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
    }

    /**
     * JB persistent setting {@code ignoredNames} lives in {@link PyShadowingBuiltinsInspectionState}.
     */
    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyShadowingBuiltinsInspectionState();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session), ((PyShadowingBuiltinsInspectionState) state).ignoredNames);
    }

    private static final class PyIgnoreBuiltinQuickFix implements LocalQuickFix, LowPriorityAction {
        private final String myName;

        private PyIgnoreBuiltinQuickFix(String name) {
            myName = name;
        }

        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixIgnoreShadowedBuiltInName(myName);
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }

        @Override
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element != null) {
                // TODO consulo: InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile missing, modifying the current profile instead
                InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
                profile.<PyShadowingBuiltinsInspection, PyShadowingBuiltinsInspectionState>modifyToolSettings(
                    PyShadowingBuiltinsInspection.class.getSimpleName(),
                    element,
                    (inspection, state) -> {
                        if (!state.ignoredNames.contains(myName)) {
                            state.ignoredNames.add(myName);
                        }
                    }
                );
            }
        }
    }

    private static class Visitor extends PyInspectionVisitor {
        private final Set<String> myIgnoredNames;

        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context, Collection<String> ignoredNames) {
            super(holder, context);
            myIgnoredNames = Set.copyOf(ignoredNames);
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
            if (name != null && PyUtil.getInitialUnderscores(name) != 1 && !myIgnoredNames.contains(name)) {
                PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
                PsiElement builtin = builtinCache.getByName(name);
                if (builtin != null && !PyUtil.inSameFile(builtin, element)) {
                    PsiElement identifier = element.getNameIdentifier();
                    PsiElement problemElement = identifier != null ? identifier : element;
                    registerProblem(problemElement, PyInspectionMessages.problemMessage(a -> PyLocalize.inspShadowingBuiltinsShadowsBuiltInName(a[0]), name),
                                    ProblemHighlightType.WEAK_WARNING, createQuickFixes(name, problemElement));
                }
            }
        }
    }
}

