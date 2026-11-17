// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.MultiMap;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyPossibleClassMember;
import com.jetbrains.python.psi.PyTargetExpression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * Plugin that moves class properties.
 * It represents property (whatever old or new) as one of its methods.
 *
 * @author Ilya.Kazakevich
 */
class PropertiesManager extends MembersManager<PyElement> {

    PropertiesManager() {
        super(PyElement.class);
    }

    @Override
    protected Collection<? extends PyElement> getElementsToStoreReferences(Collection<PyElement> elements) {
        List<PyElement> result = new ArrayList<>();
        for (PyElement element : elements) {
            PyPossibleClassMember classMember = as(element, PyPossibleClassMember.class);
            if (classMember == null) {
                continue;
            }
            PyClass pyClass = classMember.getContainingClass();
            if (pyClass == null) {
                continue;
            }
            Property property = getProperty(pyClass, element);
            result.addAll(getAllFunctions(property));
            PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(property.getDefinitionSite(), PyAssignmentStatement.class);
            if (assignment != null) {
                result.add(assignment);
            }
        }
        return result;
    }

    @Override
    protected List<? extends PyElement> getMembersCouldBeMoved(PyClass pyClass) {
        List<PyElement> elements = new ArrayList<>(pyClass.getProperties().size());
        for (Property property : pyClass.getProperties().values()) {
            elements.add(getElement(property));
        }
        return elements;
    }

    private static PyElement getElement(Property property) {
        PyCallable getter = property.getGetter().valueOrNull();
        PyCallable setter = property.getSetter().valueOrNull();
        PyCallable deleter = property.getDeleter().valueOrNull();

        if (getter != null) {
            return getter;
        }
        else if (setter != null) {
            return setter;
        }
        else if (deleter != null) {
            return deleter;
        }
        else {
            PyTargetExpression site = property.getDefinitionSite();
            assert site != null : "Property has no methods nor declaration. That is not property";
            return site;
        }
    }

    private static Property getProperty(PyClass pyClass, PyElement element) {
        Collection<Property> properties = pyClass.getProperties().values();
        if (element instanceof PyTargetExpression) {
            return getPropertyByTargetExpression(properties, (PyTargetExpression)element);
        }
        if (element instanceof PyFunction) {
            return getPropertyByFunction(properties, (PyFunction)element);
        }
        throw new IllegalArgumentException("Not function nor target");
    }

    private static Property getPropertyByFunction(Collection<Property> properties,
                                                           PyFunction functionToSearch) {
        for (Property property : properties) {
            for (PyFunction function : getAllFunctions(property)) {
                if (function.equals(functionToSearch)) {
                    return property;
                }
            }
        }
        throw new IllegalArgumentException("No property found");
    }

    private static Property getPropertyByTargetExpression(Iterable<Property> properties,
                                                                   PyTargetExpression element) {
        for (Property property : properties) {
            if (element.equals(property.getDefinitionSite())) {
                return property;
            }
        }
        throw new IllegalArgumentException("No property found");
    }

    private static Collection<PyFunction> getAllFunctions(Property property) {
        Collection<PyFunction> result = new ArrayList<>(3);
        PyCallable getter = property.getGetter().valueOrNull();
        PyCallable setter = property.getSetter().valueOrNull();
        PyCallable deleter = property.getDeleter().valueOrNull();

        if (getter instanceof PyFunction) {
            result.add((PyFunction)getter);
        }
        if (setter instanceof PyFunction) {
            result.add((PyFunction)setter);
        }
        if (deleter instanceof PyFunction) {
            result.add((PyFunction)deleter);
        }
        return result;
    }

    @Override
    protected Collection<PyElement> moveMembers(PyClass from,
                                                Collection<PyMemberInfo<PyElement>> members,
                                                PyClass ... to) {
        Collection<PyElement> result = new ArrayList<>();

        Collection<PyElement> elements = fetchElements(members);
        for (PyElement element : elements) {
            Property property = getProperty(from, element);
            Collection<PyFunction> functions = getAllFunctions(property);
            result.addAll(MethodsManager.moveMethods(from, functions, false, to));
            PyTargetExpression definitionSite = property.getDefinitionSite();
            if (definitionSite != null) {
                PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(definitionSite, PyAssignmentStatement.class);
                result.addAll(ClassFieldsManager.moveAssignmentsImpl(from, Collections.singleton(assignmentStatement), to));
            }
        }
        return result;
    }

    @Override
    public PyMemberInfo<PyElement> apply(PyElement input) {
        return new PyMemberInfo<>(input, false, getName(input), false, this, false);
    }

    private static String getName(PyElement input) {
        PyClass clazz = PsiTreeUtil.getParentOfType(input, PyClass.class);
        assert clazz != null : "Element not declared in class";
        Property property = getProperty(clazz, input);
        return property.getName();
    }

    @Override
    public boolean hasConflict(PyElement member, PyClass aClass) {
        return false;
    }

    @Override
    protected MultiMap<PyClass, PyElement> getDependencies(PyElement member) {
        PyRecursiveElementVisitorWithResult visitor = new PyReferenceVisitor();
        member.accept(visitor);

        return visitor.myResult;
    }

    @Override
    protected Collection<PyElement> getDependencies(MultiMap<PyClass, PyElement> usedElements) {
        return Collections.emptyList();
    }

    private static class PyReferenceVisitor extends PyRecursiveElementVisitorWithResult {


        @Override
        public void visitPyExpression(PyExpression node) {
            PsiReference reference = node.getReference();
            if (reference == null) {
                return;
            }

            PsiElement declaration = reference.resolve();
            if (!(declaration instanceof PyFunction function)) {
                return;
            }

            Property property = function.getProperty();
            if (property == null) {
                return;
            }

            PyClass aClass = function.getContainingClass();
            if (aClass == null) {
                return;
            }
            Collection<PyFunction> functions = getAllFunctions(property);
            for (PyFunction pyFunction : functions) {
                PyClass functionClass = pyFunction.getContainingClass();
                if (functionClass != null) {
                    myResult.putValue(functionClass, pyFunction);
                }
            }

            PyTargetExpression definitionSite = property.getDefinitionSite();
            if (definitionSite != null) {
                PyClass pyClass = PsiTreeUtil.getParentOfType(definitionSite, PyClass.class);
                if (pyClass != null) {
                    myResult.putValue(pyClass, definitionSite);
                }
            }
        }
    }
}
