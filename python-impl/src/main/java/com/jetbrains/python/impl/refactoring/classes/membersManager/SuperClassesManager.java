// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import com.google.common.collect.Lists;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.util.collection.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Plugin that moves superclasses from one class to another
 *
 * @author Ilya.Kazakevich
 */
final class SuperClassesManager extends MembersManager<PyClass> {
    SuperClassesManager() {
        super(PyClass.class);
    }


    @Override
    protected Collection<PyElement> getDependencies(MultiMap<PyClass, PyElement> usedElements) {
        return Lists.newArrayList(usedElements.keySet());
    }

    @Override
    protected MultiMap<PyClass, PyElement> getDependencies(PyElement member) {
        return MultiMap.empty();
    }

    @Override
    public boolean hasConflict(PyClass member, PyClass aClass) {
        List<PyExpression> expressionList = getExpressionsBySuperClass(aClass, Collections.singleton(member));
        return !expressionList.isEmpty();
    }

    @Override
    protected List<PyElement> getMembersCouldBeMoved(PyClass pyClass) {
        return Lists.newArrayList(Arrays.asList(pyClass.getSuperClasses(null)));
    }

    @Override
    protected Collection<PyElement> moveMembers(PyClass from,
                                                                                            Collection<PyMemberInfo<PyClass>> members,
                                                                                            PyClass ... to) {
        Collection<PyClass> elements = fetchElements(members);
        for (PyClass destClass : to) {
            PyPsiRefactoringUtil.addSuperclasses(from.getProject(), destClass, elements.toArray(new PyClass[members.size()]));
        }

        List<PyExpression> expressionsToDelete = getExpressionsBySuperClass(from, elements);
        for (PyExpression expressionToDelete : expressionsToDelete) {
            expressionToDelete.delete();
        }

        return Collections.emptyList(); //Hack: we know that "superclass expression" can't have reference
    }

    /**
     * Returns superclass expressions that are resolved to one or more classes from collection
     *
     * @param from    class to get superclass expressions from
     * @param classes classes to check superclasses against
     * @return collection of expressions that are resolved to one or more class from classes param
     */
    private static List<PyExpression> getExpressionsBySuperClass(PyClass from,
                                                                                                                                                Collection<PyClass> classes) {
        List<PyExpression> expressionsToDelete = new ArrayList<>(classes.size());

        for (PyExpression expression : from.getSuperClassExpressions()) {
            // Remove all superclass expressions that point to class from memberinfo
            if (!(expression instanceof PyQualifiedExpression)) {
                continue;
            }
            PyReferenceExpression reference = (PyReferenceExpression)expression;
            for (PyClass element : classes) {
                if (reference.getReference().isReferenceTo(element)) {
                    expressionsToDelete.add(expression);
                }
            }
        }
        return expressionsToDelete;
    }

    @Override
    public PyMemberInfo<PyClass> apply(PyClass input) {
        String name = RefactoringBundle.message("member.info.extends.0", input.getName());
        //TODO: Check for "overrides"
        return new PyMemberInfo<>(input, false, name, false, this, false);
    }
}