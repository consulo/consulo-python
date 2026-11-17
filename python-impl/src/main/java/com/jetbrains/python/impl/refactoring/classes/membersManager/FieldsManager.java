// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.collection.MultiMap;
import com.jetbrains.python.impl.NotNullPredicate;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Parent of all field-based plugins (like class fields, instance fields and so on)
 *
 * @author Ilya.Kazakevich
 */
abstract class FieldsManager extends MembersManager<PyTargetExpression> {
    private static final SimpleAssignmentsOnly SIMPLE_ASSIGNMENTS_ONLY = new SimpleAssignmentsOnly();
    private static final AssignmentTransform ASSIGNMENT_TRANSFORM = new AssignmentTransform();
    private final boolean myStatic;

    /**
     * @param isStatic is field static or not?
     */
    protected FieldsManager(boolean isStatic) {
        super(PyTargetExpression.class);
        myStatic = isStatic;
    }


    @Override
    protected Collection<PyElement> getDependencies(MultiMap<PyClass, PyElement> usedElements) {
        return Collections.emptyList();
    }

    @Override
    protected MultiMap<PyClass, PyElement> getDependencies(PyElement member) {
        PyRecursiveElementVisitorWithResult visitor = new MyPyRecursiveElementVisitor();
        member.accept(visitor);
        return visitor.myResult;
    }

    @Override
    protected Collection<? extends PyElement> getElementsToStoreReferences(Collection<PyTargetExpression> elements) {
        // We need to save references from assignments
        return Collections2.transform(elements, ASSIGNMENT_TRANSFORM);
    }

    @Override
    protected List<PyElement> getMembersCouldBeMoved(PyClass pyClass) {
        return Lists.newArrayList(Collections2.filter(getFieldsByClass(pyClass), SIMPLE_ASSIGNMENTS_ONLY));
    }

    @Override
    protected Collection<PyElement> moveMembers(PyClass from,
                                                Collection<PyMemberInfo<PyTargetExpression>> members,
                                                PyClass ... to) {
        return moveAssignments(from, Collections2
                                 .filter(Collections2.transform(fetchElements(members), ASSIGNMENT_TRANSFORM), NotNullPredicate.INSTANCE),
                               to);
    }

    protected abstract Collection<PyElement> moveAssignments(PyClass from,
                                                             Collection<PyAssignmentStatement> statements,
                                                             PyClass ... to);

    /**
     * Checks if class has fields. Only child may know how to obtain field
     *
     * @param pyClass   class to check
     * @param fieldName field name
     * @return true if has one
     */
    protected abstract boolean classHasField(PyClass pyClass, String fieldName);

    /**
     * Returns all fields by class. Only child may know how to obtain fields
     *
     * @param pyClass class to check
     * @return list of fields in target expression (declaration) form
     */
    protected abstract Collection<PyTargetExpression> getFieldsByClass(PyClass pyClass);


    @Override
    public PyMemberInfo<PyTargetExpression> apply(PyTargetExpression input) {
        PyAssignmentStatement parent = ObjectUtil.tryCast(input.getParent(), PyAssignmentStatement.class);
        return new PyMemberInfo<>(input, parent != null ? parent : input, myStatic, input.getText(), isOverrides(input), this, false);
    }

    private @Nullable Boolean isOverrides(PyTargetExpression input) {
        PyClass aClass = input.getContainingClass();
        String name = input.getName();
        if (name == null) {
            return null; //Field with out of name can't override something
        }

        assert aClass != null : "Target expression declared outside of class:" + input;

        return classHasField(aClass, name) ? true : null;
    }


    private static class SimpleAssignmentsOnly extends NotNullPredicate<PyTargetExpression> {
        //Support only simplest cases like CLASS_VAR = 42.
        //Tuples (CLASS_VAR_1, CLASS_VAR_2) = "spam", "eggs" are not supported by now
        @Override
        public boolean applyNotNull(PyTargetExpression input) {
            PsiElement parent = input.getParent();
            return (parent != null) && PyAssignmentStatement.class.isAssignableFrom(parent.getClass());
        }
    }


    //Transforms expressions to its assignment step
    private static class AssignmentTransform implements Function<PyTargetExpression, PyAssignmentStatement> {
        @Override
        public @Nullable PyAssignmentStatement apply(PyTargetExpression input) {
            return PsiTreeUtil.getParentOfType(input, PyAssignmentStatement.class);
        }
    }

    /**
     * Fetches field declarations
     */
    private static class MyPyRecursiveElementVisitor extends PyRecursiveElementVisitorWithResult {

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            PsiElement declaration = node.getReference().resolve();
            if (declaration instanceof PyElement) {
                PyClass parent = PsiTreeUtil.getParentOfType(declaration, PyClass.class);
                if (parent != null) {
                    myResult.putValue(parent, (PyElement)declaration);
                }
            }
        }
    }
}
