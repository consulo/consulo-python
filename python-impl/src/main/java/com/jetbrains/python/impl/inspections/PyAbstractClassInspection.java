// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.localize.LocalizeValue;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.util.collection.SmartList;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.impl.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@ExtensionImpl
public final class PyAbstractClassInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameAbstractClass();
    }


    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        if (context.getUsesExternalTypeEngine()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new Visitor(holder, context);
    }

    private static final class Visitor extends PyInspectionVisitor {

        private Visitor(ProblemsHolder holder,
                        TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            if (node.getCallee() instanceof PyReferenceExpression calleeReferenceExpression) {
                QualifiedResolveResult resolveResult = calleeReferenceExpression.followAssignmentsChain(getResolveContext());
                if (resolveResult.getElement() instanceof PyClass pyClass) {
                    if (PyClassImpl.canHaveAbstractMethods(pyClass, myTypeEvalContext)) {
                        boolean hasAbstractMethod =
                            ContainerUtil.exists(pyClass.getMethods(), method -> PyKnownDecoratorUtil.hasAbstractDecorator(method, myTypeEvalContext));
                        if (hasAbstractMethod || !getAllSuperAbstractMethods(pyClass).isEmpty()) {
                            registerProblem(node, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspAbstractClassCannotInstantiateAbstractClass(msgArgs[0]), CodifiedParam.ofReference(pyClass)),
                                            ProblemHighlightType.WARNING);
                        }
                        else if (isAbstract(pyClass)) {
                            registerProblem(node, PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspAbstractClassCannotInstantiateAbstractClass(msgArgs[0]), CodifiedParam.ofReference(pyClass)),
                                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                        }
                    }
                }
            }
        }

        @Override
        public void visitPyClass(PyClass pyClass) {
            if (isAbstract(pyClass) || PyProtocolsKt.isProtocol(pyClass, myTypeEvalContext)) {
                return;
            }

            List<PyDecorator> abstractDecorators = new ArrayList<>();
            for (PyFunction method : pyClass.getMethods()) {
                PyDecoratorList decoratorList = method.getDecoratorList();
                if (decoratorList == null) {
                    continue;
                }

                for (PyDecorator decorator : decoratorList.getDecorators()) {
                    boolean isAbstract = PyKnownDecoratorUtil.knownDecoratorsOf(decorator, myTypeEvalContext)
                        .hasTrait(PyKnownDecorator::isAbstract);
                    if (isAbstract) {
                        abstractDecorators.add(decorator);
                    }
                }
            }

            if (!PyClassImpl.canHaveAbstractMethods(pyClass, myTypeEvalContext)) {
                for (PyDecorator decorator : abstractDecorators) {
                    SmartList<LocalQuickFix> quickFixes = new SmartList<>();
                    addMakeClassAbstractFixes(pyClass, quickFixes);
                    registerProblem(decorator,
                                    PyLocalize.inspAbstractClassAbstractMethodsAreAllowedInClassesWhoseMetaclassIsAbcmeta().get(),
                                    quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
                }
            }

            if (abstractDecorators.isEmpty()) {
                List<PyFunction> toImplement = getAllSuperAbstractMethods(pyClass);

                ASTNode nameNode = pyClass.getNameNode();
                if (!toImplement.isEmpty() && nameNode != null) {
                    SmartList<LocalQuickFix> quickFixes = new SmartList<>();

                    LocalQuickFix qf = PythonUiService.getInstance().createPyImplementMethodsQuickFix(pyClass, toImplement);
                    if (qf != null) {
                        quickFixes.add(qf);
                    }
                    addMakeClassAbstractFixes(pyClass, quickFixes);

                    registerProblem(nameNode.getPsi(),
                                    PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspAbstractClassClassMustImplementAllAbstractMethods(msgArgs[0]), CodifiedParam.ofReference(pyClass)),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                    quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
                }
            }
        }

        private boolean isAbstract(PyClass pyClass) {
            PyClassLikeType metaClass = pyClass.getMetaClassType(false, myTypeEvalContext);
            if (metaClass != null && PyNames.ABC_META.equals(metaClass.getClassQName())) {
                return true;
            }
            for (PyClassLikeType superClassType : pyClass.getSuperClassTypes(myTypeEvalContext)) {
                if (superClassType != null && PyNames.ABC.equals(superClassType.getClassQName())) {
                    return true;
                }
            }
            return false;
        }

        private List<PyFunction> getAllSuperAbstractMethods(PyClass pyClass) {
        /* Do not report problem if class contains only methods that raise NotImplementedError without any abc.* decorators
           but keep ability to implement them via quickfix (see PY-38680) */
            return ContainerUtil.filter(PyPsiRefactoringUtil.getAllSuperAbstractMethods(pyClass, myTypeEvalContext),
                                        function -> PyKnownDecoratorUtil.hasAbstractDecorator(function, myTypeEvalContext));
        }

        private static void addMakeClassAbstractFixes(PsiElement element, Collection<LocalQuickFix> fixes) {
            fixes.add(new SetABCMetaAsMetaclassQuickFix());

            if (LanguageLevel.forElement(element).isPy3K()) {
                fixes.add(new AddABCToSuperclassesQuickFix());
            }
        }

        private static @Nullable PyClass getPyClass(PsiElement element) {
            // element is a class name node
            if (element.getParent() instanceof PyClass pyClass) {
                return pyClass;
            }
            // element is a method decorator
            if (element instanceof PyDecorator decorator) {
                return Optional.ofNullable(decorator.getTarget())
                    .map(PyFunction::getContainingClass)
                    .orElse(null);
            }
            return null;
        }

        private static class AddABCToSuperclassesQuickFix implements LocalQuickFix {

            @Override
            public LocalizeValue getName() {
                return PyLocalize.inspAbstractClassAddToSuperclasses(PyNames.ABC);
            }

            @Override
            public void applyFix(Project project, ProblemDescriptor descriptor) {
                PsiElement element = descriptor.getPsiElement();
                PyClass cls = getPyClass(element);
                if (cls == null) {
                    return;
                }

                PyClass abcClass = PyPsiFacade.getInstance(project).createClassByQName(PyNames.ABC, cls);
                if (abcClass == null) {
                    return;
                }

                PyPsiRefactoringUtil.addSuperclasses(project, cls, abcClass);
            }
        }

        private static class SetABCMetaAsMetaclassQuickFix implements LocalQuickFix {

            @Override
            public LocalizeValue getName() {
                return PyLocalize.inspAbstractClassSetAsMetaclass(PyNames.ABC_META);
            }

            @Override
            public void applyFix(Project project, ProblemDescriptor descriptor) {
                PsiElement element = descriptor.getPsiElement();
                PyClass cls = getPyClass(element);
                if (cls == null) {
                    return;
                }

                PyClass abcMetaClass = PyPsiFacade.getInstance(project).createClassByQName(PyNames.ABC_META, cls);
                if (abcMetaClass == null) {
                    return;
                }

                TypeEvalContext context = TypeEvalContext.userInitiated(cls.getProject(), cls.getContainingFile());
                PyPsiRefactoringUtil.addMetaClassIfNotExist(cls, abcMetaClass, context);
            }
        }
    }
}
