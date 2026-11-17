// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.impl.codeInsight.typing.PyTypedDictTypeProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.psi.PyDisjointBaseUtil;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyKnownDecorators;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecoratable;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class PyDisjointBaseInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameDisjointBaseClassInheritance();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonTypeHinting();
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

    private static class Visitor extends PyInspectionVisitor {
        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyClass(PyClass node) {
            super.visitPyClass(node);

            List<PyClassType> superClassTypes = new ArrayList<>();
            for (PyClassLikeType type : node.getSuperClassTypes(myTypeEvalContext)) {
                if (type instanceof PyClassType classType) {
                    superClassTypes.add(classType);
                }
            }
            if (superClassTypes.size() < 2) {
                return;
            }

            for (int i = 0; i < superClassTypes.size(); i++) {
                PyClassType type1 = superClassTypes.get(i);
                String name1 = type1.getName();
                if (name1 == null) {
                    continue;
                }

                for (int j = i + 1; j < superClassTypes.size(); j++) {
                    PyClassType type2 = superClassTypes.get(j);
                    String name2 = type2.getName();
                    if (name2 == null) {
                        continue;
                    }

                    if (PyDisjointBaseUtil.areDisjoint(type1, type2, myTypeEvalContext)) {
                        PsiElement nameIdentifier = node.getNameIdentifier();
                        registerProblem(
                            nameIdentifier != null ? nameIdentifier : node,
                            PyInspectionMessages.problemMessage(
                                a -> PyLocalize.inspDisjointBaseClassCannotInheritFromDisjointBases(a[0], a[1]),
                                CodifiedParam.ofReference(type1.getPyClass(), name1),
                                CodifiedParam.ofReference(type2.getPyClass(), name2)
                            )
                        );
                        return;
                    }
                }
            }
        }

        @Override
        public void visitPyDecorator(PyDecorator node) {
            PyKnownDecorators knownDecorators = PyKnownDecoratorUtil.knownDecoratorsOf(node, myTypeEvalContext);
            if (knownDecorators.containsAny(PyKnownDecorator.DISJOINT_BASE, PyKnownDecorator.DISJOINT_BASE_EXT)) {
                PyDecoratable parent = PsiTreeUtil.getParentOfType(node, PyDecoratable.class);
                if (parent instanceof PyFunction) {
                    registerProblem(node, PyLocalize.inspDisjointBaseOnFunction().get());
                    return;
                }
                if (parent instanceof PyClass pyClass) {
                    if (PyProtocolsKt.isProtocol(pyClass, myTypeEvalContext)) {
                        registerProblem(node, PyLocalize.inspDisjointBaseOnProtocol().get());
                        return;
                    }

                    if (PyTypedDictTypeProvider.Helper.isTypingTypedDictInheritor(pyClass, myTypeEvalContext)) {
                        registerProblem(node, PyLocalize.inspDisjointBaseOnTypedDict().get());
                        return;
                    }
                }
            }
            super.visitPyDecorator(node);
        }
    }
}
