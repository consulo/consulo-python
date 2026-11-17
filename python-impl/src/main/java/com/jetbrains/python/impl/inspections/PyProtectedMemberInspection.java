// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.inspections.PyInspectionExtension;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.inspections.quickfix.PyAddPropertyForFieldQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyMakePublicQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.impl.testing.PythonUnitTestDetectorsKt;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: ktisha
 * <p>
 * Inspection to detect situations, where
 * protected member (i.e. class member with a name beginning with an underscore)
 * is access outside the class or a descendant of the class where it's defined.
 */
@ExtensionImpl
public final class PyProtectedMemberInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameProtectedMember();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonClassStructure();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyProtectedMemberInspectionState();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session), (PyProtectedMemberInspectionState) state);
    }

    private static class Visitor extends PyInspectionVisitor {
        private final PyProtectedMemberInspectionState myState;

        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context, PyProtectedMemberInspectionState state) {
            super(holder, context);
            myState = state;
        }

        @Override
        public void visitPyImportElement(PyImportElement node) {
            PyStatement statement = node.getContainingImportStatement();
            if (!(statement instanceof PyFromImportStatement)) {
                return;
            }
            PyReferenceExpression importReferenceExpression = node.getImportReferenceExpression();
            PyReferenceExpression importSource = ((PyFromImportStatement)statement).getImportSource();
            if (importReferenceExpression != null && importSource != null && !isImportFromTheSamePackage(importSource)) {
                checkReference(importReferenceExpression, importSource);
            }
        }

        private boolean isImportFromTheSamePackage(PyReferenceExpression importSource) {
            PsiDirectory currentFileDirectory = importSource.getContainingFile().getContainingDirectory();

            if (currentFileDirectory != null && PyUtil.isPackage(currentFileDirectory, true, importSource)) {
                PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);

                return StreamEx
                  .of(importSource.getReference(resolveContext).multiResolve(false))
                  .map(ResolveResult::getElement)
                  .select(PyFile.class)
                  .map(PsiFile::getContainingDirectory)
                  .nonNull()
                  .map(PsiDirectory::getVirtualFile)
                  .anyMatch(importedSourceDir -> VirtualFileUtil.isAncestor(importedSourceDir, currentFileDirectory.getVirtualFile(), false));
            }

            return false;
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            PyExpression qualifier = node.getQualifier();
            if (myState.ignoreAnnotations && PsiTreeUtil.getParentOfType(node, PyAnnotation.class) != null) {
                return;
            }
            if (qualifier == null || ArrayUtil.contains(qualifier.getText(), PyNames.CANONICAL_SELF, PyNames.CANONICAL_CLS)) {
                return;
            }
            if (isImportFromTheSamePackage(node)) {
                return;
            }
            checkReference(node, qualifier);
        }

        private void checkReference(PyReferenceExpression node, PyExpression qualifier) {
            String name = node.getName();
            List<LocalQuickFix> quickFixes = new ArrayList<>();
            LocalQuickFix renameElementQuickFix = PythonUiService.getInstance().createPyRenameElementQuickFix(node);
            if (renameElementQuickFix != null) {
                quickFixes.add(renameElementQuickFix);
            }

            if (name != null && name.startsWith("_") && !name.startsWith("__") && !name.endsWith("__")) {
                PsiReference reference = node.getReference(getResolveContext());
                for (PyInspectionExtension inspectionExtension : PyInspectionExtension.EP_NAME.getExtensions()) {
                    if (inspectionExtension.ignoreProtectedSymbol(node, myTypeEvalContext)) {
                        return;
                    }
                }
                PsiElement resolvedExpression = reference.resolve();
                PyClass resolvedClass = getNotPyiClassOwner(resolvedExpression);

                if (resolvedExpression instanceof PyTargetExpression) {

                    String newName = StringUtil.trimLeading(name, '_');
                    if (resolvedClass != null) {

                        LocalizeValue qFixName = resolvedClass.getProperties().containsKey(newName) ?
                                                PyLocalize.qfixUseProperty() : PyLocalize.qfixAddProperty();
                        quickFixes.add(new PyAddPropertyForFieldQuickFix(qFixName));

                        PyClassType classType = PyUtil.as(myTypeEvalContext.getType(resolvedClass), PyClassType.class);
                        if (classType != null) {
                            Set<String> usedNames = classType.getMemberNames(true, myTypeEvalContext);
                            if (!usedNames.contains(newName)) {
                                quickFixes.add(new PyMakePublicQuickFix());
                            }
                        }
                    }
                }

                if (myState.ignoreTestFunctions) {
                    for (PsiElement item = node.getParent(); item != null && !(item instanceof PsiFileSystemItem); item = item.getParent()) {
                        if (PythonUnitTestDetectorsKt.isTestElement(item, myTypeEvalContext)) {
                            return;
                        }
                    }
                }

                if (resolvedClass != null) {
                    PyClass parentClass = getClassOwner(node);
                    while (parentClass != null) {
                        if (parentClass.isSubclass(resolvedClass, myTypeEvalContext)) {
                            return;
                        }
                        parentClass = getClassOwner(parentClass);
                    }
                }
                PyType type = myTypeEvalContext.getType(qualifier);
                String message = type instanceof PyModuleType
                                                          ? PyLocalize.inspProtectedMemberAccessToProtectedMemberOfModule(name).get()
                                                          : PyLocalize.inspProtectedMemberAccessToProtectedMemberOfClass(name).get();
                registerProblem(node, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null, quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
            }
        }

        private @Nullable PyClass getNotPyiClassOwner(@Nullable PsiElement element) {
            PyClass owner = getClassOwner(element);
            return owner == null ? null : PyiUtil.getOriginalElementOrLeaveAsIs(owner, PyClass.class);
        }

        private static @Nullable PyClass getClassOwner(@Nullable PsiElement element) {
            for (ScopeOwner owner = ScopeUtil.getScopeOwner(element); owner != null; owner = ScopeUtil.getScopeOwner(owner)) {
                if (owner instanceof PyClass) {
                    return (PyClass)owner;
                }
            }
            return null;
        }

        @Override
        public void visitPyFromImportStatement(PyFromImportStatement node) {
            PyReferenceExpression source = node.getImportSource();
            if (source == null) {
                return;
            }

            Set<String> dunderAlls = collectDunderAlls(source);
            if (dunderAlls == null) {
                return;
            }

            StreamEx
              .of(node.getImportElements())
              .map(PyImportElement::getImportReferenceExpression)
              .nonNull()
              .filter(
                referenceExpression -> !dunderAlls.contains(referenceExpression.getName()) && !resolvesToFileSystemItem(referenceExpression)
              )
              .forEach(
                referenceExpression -> {
                        PyInspectionMessages.ProblemMessage message = PyInspectionMessages.problemMessage(a -> PyLocalize.inspProtectedMemberNameNotDeclaredInAll(a[0]), referenceExpression.getName());
                        registerProblem(referenceExpression, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }
                );
        }

        private @Nullable Set<String> collectDunderAlls(PyReferenceExpression source) {
            PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);

            List<List<String>> resolvedDunderAlls = StreamEx
              .of(source.getReference(resolveContext).multiResolve(false))
              .map(ResolveResult::getElement)
              .select(PyFile.class)
              .map(PyFile::getDunderAll)
              .toList();
            if (resolvedDunderAlls.isEmpty()) {
                return null;
            }

            Set<String> result = new HashSet<>();
            for (List<String> dunderAll : resolvedDunderAlls) {
                if (dunderAll == null) {
                    return null;
                }
                result.addAll(dunderAll);
            }
            return result;
        }

        private boolean resolvesToFileSystemItem(PyReferenceExpression referenceExpression) {
            PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);

            return ContainerUtil.exists(referenceExpression.getReference(resolveContext).multiResolve(false),
                                        result -> result.getElement() instanceof PsiFileSystemItem);
        }
    }
}
