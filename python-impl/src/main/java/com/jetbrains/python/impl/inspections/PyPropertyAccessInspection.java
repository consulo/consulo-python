/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.jetbrains.python.impl.inspections.quickfix.PyCreatePropertyQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.toolbox.Maybe;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;

import java.util.HashMap;

/**
 * Checks that properties are accessed correctly.
 *
 * @author dcheryasov
 * @since 2010-06-29
 */
@ExtensionImpl
public class PyPropertyAccessInspection extends PyInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNamePropertyAccess();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        Object state
    ) {
        return new Visitor(holder, session);
    }

    public static class Visitor extends PyInspectionVisitor {
        private final HashMap<Pair<PyClass, String>, Property> myPropertyCache = new HashMap<>();

        public Visitor(@Nonnull ProblemsHolder holder, LocalInspectionToolSession session) {
            super(holder, session);
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            super.visitPyReferenceExpression(node);
            checkPropertyExpression(node);
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            super.visitPyTargetExpression(node);
            checkPropertyExpression(node);
        }

        private void checkPropertyExpression(PyQualifiedExpression node) {
            PyExpression qualifier = node.getQualifier();
            if (qualifier != null) {
                PyType type = myTypeEvalContext.getType(qualifier);
                if (type instanceof PyClassType) {
                    PyClass cls = ((PyClassType) type).getPyClass();
                    String name = node.getName();
                    if (name != null) {
                        Pair<PyClass, String> key = Pair.create(cls, name);
                        Property property;
                        if (myPropertyCache.containsKey(key)) {
                            property = myPropertyCache.get(key);
                        }
                        else {
                            property = cls.findProperty(name, true, myTypeEvalContext);
                        }
                        myPropertyCache.put(key, property); // we store nulls, too, to know that a property does not exist
                        if (property != null) {
                            AccessDirection dir = AccessDirection.of(node);
                            checkAccessor(node, name, dir, property);
                            if (dir == AccessDirection.READ) {
                                PsiElement parent = node.getParent();
                                if (parent instanceof PyAugAssignmentStatement && ((PyAugAssignmentStatement) parent).getTarget() == node) {
                                    checkAccessor(node, name, AccessDirection.WRITE, property);
                                }
                            }
                        }
                    }
                }
            }
        }

        private void checkAccessor(PyExpression node, String name, AccessDirection dir, Property property) {
            Maybe<PyCallable> accessor = property.getByDirection(dir);
            if (accessor.isDefined() && accessor.value() == null) {
                LocalizeValue message;
                if (dir == AccessDirection.WRITE) {
                    message = PyLocalize.inspProperty$0CantBeSet(name);
                }
                else if (dir == AccessDirection.DELETE) {
                    message = PyLocalize.inspProperty$0CantBeDeleted(name);
                }
                else {
                    message = PyLocalize.inspProperty$0CantBeRead(name);
                }
                registerProblem(node, message.get(), new PyCreatePropertyQuickFix(dir));
            }
        }
    }
}
