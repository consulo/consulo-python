/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.stdlib.PyNamedTupleType;
import com.jetbrains.python.impl.inspections.quickfix.PyAddPropertyForFieldQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyMakePublicQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.impl.testing.pytest.PyTestUtil;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.*;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Inspection to detect situations, where
 * protected member (i.e. class member with a name beginning with an underscore)
 * is access outside the class or a descendant of the class where it's defined.
 *
 * @author ktisha
 */
@ExtensionImpl
public class PyProtectedMemberInspection extends PyInspection {
    @Nonnull
    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyProtectedMemberInspectionState();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameProtectedMemberAccess();
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        Object state
    ) {
        PyProtectedMemberInspectionState inspectionState = (PyProtectedMemberInspectionState) state;
        return new Visitor(holder, session, inspectionState);
    }


    private class Visitor extends PyInspectionVisitor {
        private final PyProtectedMemberInspectionState myState;

        public Visitor(
            @Nullable ProblemsHolder holder,
            @Nonnull LocalInspectionToolSession session,
            PyProtectedMemberInspectionState inspectionState
        ) {
            super(holder, session);
            myState = inspectionState;
        }

        @Override
        public void visitPyImportElement(PyImportElement node) {
            PyStatement statement = node.getContainingImportStatement();
            if (!(statement instanceof PyFromImportStatement)) {
                return;
            }
            PyReferenceExpression importReferenceExpression = node.getImportReferenceExpression();
            PyReferenceExpression importSource = ((PyFromImportStatement) statement).getImportSource();
            if (importReferenceExpression != null && importSource != null && !isImportFromTheSamePackage(importSource)) {
                checkReference(importReferenceExpression, importSource);
            }
        }

        private boolean isImportFromTheSamePackage(PyReferenceExpression importSource) {
            PsiDirectory directory = importSource.getContainingFile().getContainingDirectory();
            if (directory != null && PyUtil.isPackage(directory, true, importSource.getContainingFile()) &&
                directory.getName().equals(importSource.getName())) {
                return true;
            }
            return false;
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            PyExpression qualifier = node.getQualifier();
            if (myState.ignoreAnnotations && PsiTreeUtil.getParentOfType(node, PyAnnotation.class) != null) {
                return;
            }
            if (qualifier == null || PyNames.CANONICAL_SELF.equals(qualifier.getText())) {
                return;
            }
            checkReference(node, qualifier);
        }

        private void checkReference(@Nonnull PyReferenceExpression node, @Nonnull PyExpression qualifier) {
            if (myTypeEvalContext.getType(qualifier) instanceof PyNamedTupleType) {
                return;
            }
            String name = node.getName();
            List<LocalQuickFix> quickFixes = new ArrayList<>();
            quickFixes.add(new PyRenameElementQuickFix());

            if (name != null && name.startsWith("_") && !name.startsWith("__") && !name.endsWith("__")) {
                PsiReference reference = node.getReference(getResolveContext());
                for (PyInspectionExtension inspectionExtension : PyInspectionExtension.EP_NAME.getExtensions()) {
                    if (inspectionExtension.ignoreProtectedSymbol(node, myTypeEvalContext)) {
                        return;
                    }
                }
                PsiElement resolvedExpression = reference.resolve();
                PyClass resolvedClass = getClassOwner(resolvedExpression);
                if (resolvedExpression instanceof PyTargetExpression) {
                    String newName = StringUtil.trimLeading(name, '_');
                    if (resolvedClass != null) {
                        LocalizeValue qFixName = resolvedClass.getProperties().containsKey(newName)
                            ? PyLocalize.qfixUseProperty()
                            : PyLocalize.qfixAddProperty();
                        quickFixes.add(new PyAddPropertyForFieldQuickFix(qFixName));

                        Collection<String> usedNames = PyRefactoringUtil.collectUsedNames(resolvedClass);
                        if (!usedNames.contains(newName)) {
                            quickFixes.add(new PyMakePublicQuickFix());
                        }
                    }
                }

                PyClass parentClass = getClassOwner(node);
                if (parentClass != null) {
                    if (PyTestUtil.isPyTestClass(parentClass, null) && myState.ignoreTestFunctions) {
                        return;
                    }

                    if (parentClass.isSubclass(resolvedClass, myTypeEvalContext)) {
                        return;
                    }

                    PyClass outerClass = getClassOwner(parentClass);
                    while (outerClass != null) {
                        if (outerClass.isSubclass(resolvedClass, myTypeEvalContext)) {
                            return;
                        }

                        outerClass = getClassOwner(outerClass);
                    }
                }
                PyType type = myTypeEvalContext.getType(qualifier);
                String bundleKey =
                    type instanceof PyModuleType ? "INSP.protected.member.$0.access.module" : "INSP.protected.member.$0.access";
                registerProblem(
                    node,
                    PyBundle.message(bundleKey, name),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    null,
                    quickFixes.toArray(new LocalQuickFix[quickFixes.size() - 1])
                );
            }
        }

        @Nullable
        private PyClass getClassOwner(@Nullable PsiElement element) {
            for (ScopeOwner owner = ScopeUtil.getScopeOwner(element); owner != null; owner = ScopeUtil.getScopeOwner(owner)) {
                if (owner instanceof PyClass) {
                    return (PyClass) owner;
                }
            }
            return null;
        }
    }
}
