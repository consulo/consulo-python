// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import com.jetbrains.python.impl.NotNullPredicate;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.refactoring.classes.PyDependenciesComparator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Moves members between classes via its plugins (managers).
 * To move members use {@link #getAllMembersCouldBeMoved(PyClass)}   and {@link #moveAllMembers(Collection, PyClass, PyClass...)}
 * To add new manager, extend this class and add it to {@link #MANAGERS}
 *
 * @author Ilya.Kazakevich
 */
public abstract class MembersManager<T extends PyElement> implements Function<T, PyMemberInfo<T>> {
    /**
     * List of managers. Class delegates all logic to them.
     */
    private static final Collection<? extends MembersManager<? extends PyElement>> MANAGERS =
        Arrays.asList(new MethodsManager(),
                      new SuperClassesManager(),
                      new ClassFieldsManager(),
                      new InstanceFieldsManager(),
                      new PropertiesManager());

    private final Class<T> myExpectedClass;

    protected MembersManager(Class<T> expectedClass) {
        myExpectedClass = expectedClass;
    }

    /**
     * Get all members that could be moved out of certain class
     *
     * @param pyClass class to find members
     * @return list of members could be moved
     */
    public static List<PyMemberInfo<PyElement>> getAllMembersCouldBeMoved(PyClass pyClass) {
        List<PyMemberInfo<PyElement>> result = new ArrayList<>();

        for (MembersManager<? extends PyElement> manager : MANAGERS) {
            result.addAll(transformSafely(pyClass, manager));
        }
        return result;
    }


    /**
     * Transforms elements, manager says it could move to appropriate {@link PyMemberInfo}.
     * Types are checked at runtime.
     *
     * @param pyClass class whose members we want to move
     * @param manager manager that should check class and report list of members
     * @return member infos
     */
    //TODO: Move to  TypeSafeMovingStrategy
    //We check type at runtime
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Collection<PyMemberInfo<PyElement>> transformSafely(PyClass pyClass,
                                                                                MembersManager<?> manager) {
        List<? extends PyElement> membersCouldBeMoved = manager.getMembersCouldBeMoved(pyClass);
        manager.checkElementTypes((Iterable)membersCouldBeMoved);
        return (Collection<PyMemberInfo<PyElement>>)Collections2.transform(membersCouldBeMoved, (Function)manager);
    }


    /**
     * Moves members from one class to another
     *
     * @param memberInfos members to move
     * @param from        source
     * @param to          destination
     */
    public static void moveAllMembers(
        Collection<? extends PyMemberInfo<PyElement>> memberInfos,
        PyClass from,
        PyClass ... to
    ) {
        List<PyMemberInfo<PyElement>> memberInfosSorted = new ArrayList<>(memberInfos);


        memberInfosSorted.sort((o1, o2) -> PyDependenciesComparator.INSTANCE.compareAndStoreDependency(o1.getElementToStoreDependency(),
                                                                                                       o2.getElementToStoreDependency()));

        for (PyMemberInfo<PyElement> info : memberInfosSorted) {
            TypeSafeMovingStrategy.moveCheckingTypesAtRunTime(from, info.getMembersManager(), Collections.singleton(info), to);
        }

        PyDependenciesComparator.clearDependencyInfo(ContainerUtil.map(memberInfos, o -> o.getElementToStoreDependency()));
    }


    /**
     * Checks that all elements has allowed type for manager
     *
     * @param elements elements to check against manager
     */
    void checkElementTypes(Iterable<T> elements) {
        for (PyElement pyElement : elements) {
            Preconditions.checkArgument(myExpectedClass.isAssignableFrom(pyElement.getClass()),
                                        String.format("Manager %s expected %s but got %s", this, myExpectedClass, pyElement));
        }
    }

    /**
     * Finds member by predicate
     *
     * @param members   where to find
     * @param predicate what to find
     * @return member or null if not found
     */
    public static @Nullable PyMemberInfo<PyElement> findMember(Collection<? extends PyMemberInfo<PyElement>> members,
                                                               Predicate<? super PyMemberInfo<PyElement>> predicate) {
        for (PyMemberInfo<PyElement> pyMemberInfo : members) {
            if (predicate.apply(pyMemberInfo)) {
                return pyMemberInfo;
            }
        }
        return null;
    }

    /**
     * Finds member of class by predicate
     *
     * @param predicate what to find
     * @param pyClass   class to find members
     * @return member or null if not found
     */
    public static @Nullable PyMemberInfo<PyElement> findMember(PyClass pyClass,
                                                               Predicate<? super PyMemberInfo<PyElement>> predicate) {
        return findMember(getAllMembersCouldBeMoved(pyClass), predicate);
    }

    /**
     * Finds member in class.
     *
     * @param pyClass   class to find member in
     * @param pyElement element to find
     * @return member info with element
     */
    public static PyMemberInfo<PyElement> findMember(PyClass pyClass, PyElement pyElement) {
        PyMemberInfo<PyElement> result = findMember(pyClass, new FindByElement(pyElement));
        if (result != null) {
            return result;
        }
        throw new IllegalArgumentException(String.format("Element %s not found in class %s or can't be moved", pyElement, pyClass));
    }

    /**
     * Get list of elements certain plugin could move out of the class
     *
     * @param pyClass class with members
     * @return list of members
     */
    protected abstract List<? extends PyElement> getMembersCouldBeMoved(PyClass pyClass);


    /**
     * Returns list of elements that may require reference storing aid from {@link com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil#rememberNamedReferences(PsiElement, String...)}
     *
     * @param elements members chosen by user. In most cases members their selves could be stored, but different managers may support other strategies
     * @return elements to store
     * @see #moveAllMembers(Collection, PyClass, PyClass...)
     */
    protected Collection<? extends PyElement> getElementsToStoreReferences(Collection<T> elements) {
        return elements;
    }

    /**
     * Moves element from one class to another. Returns members that may require reference restoring aid from
     * ({@link com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil#restoreNamedReferences(PsiElement)})
     * Sort members according to their dependencies, before calling this method
     *
     * @see #getElementsToStoreReferences(Collection)
     */
    protected abstract Collection<PyElement> moveMembers(
        PyClass from,
        Collection<PyMemberInfo<T>> members,
        PyClass ... to);


    /**
     * Creates {@link PyMemberInfo} from {@link PyElement}
     * This process is plugin-specific and should be implemented in each plugin
     *
     * @param input element
     * @return member info
     */
    //IDEA-120100
    @SuppressWarnings("NullableProblems")
    @Override
    public abstract PyMemberInfo<T> apply(T input);

    /**
     * Deletes all elements
     *
     * @param pyElementsToDelete elements to delete
     */
    protected static void deleteElements(Collection<? extends PsiElement> pyElementsToDelete) {
        for (PsiElement element : pyElementsToDelete) {
            element.delete();
        }
    }

    /**
     * Fetches elements from member info.
     *
     * @param memberInfos member info to fetch elements from
     * @param <T>         type of element
     * @return list of elements
     */
    protected static <T extends PyElement> Collection<T> fetchElements(Collection<? extends PyMemberInfo<T>> memberInfos) {
        return memberInfos.stream().map(o -> o.getMember()).filter(o -> o != null).collect(Collectors.toList());
    }

    /**
     * Checks if moving certain member to certain class may lead to conflict (actually that means
     * that class already has this member)
     *
     * @param member member to check
     * @param aClass class where this member wanna be moved
     * @return true if conflict exists.
     */
    public abstract boolean hasConflict(T member, PyClass aClass);

    /**
     * Returns all elements this member depends on.
     *
     * @param classWhereMemberDeclared class where member declared
     * @param member                   member itself
     * @param destinationClass         where this member would be moved (or null if new class is unknown)
     * @return collection of elements this member depends on excluding those, would be available in destination class
     */
    public static Collection<? extends PyElement> getAllDependencies(
        PyClass classWhereMemberDeclared,
        PyElement member,
        final @Nullable PyClass destinationClass) {
        PyMemberInfo<PyElement> memberInfo = findMember(classWhereMemberDeclared, member);


        Collection<? extends PyElement> elementsToCheckDependency =
            memberInfo.getMembersManager().getElementsToStoreReferences(Collections.singleton(member));

        MultiMap<PyClass, PyElement> dependencies = new MultiMap<>();

        Collection<PyElement> result = new HashSet<>();
        for (MembersManager<? extends PyElement> manager : MANAGERS) {
            for (PyElement elementToCheckDependency : elementsToCheckDependency) {
                dependencies.putAllValues(manager.getDependencies(elementToCheckDependency));
            }
        }

        if (destinationClass != null) {
            Iterator<PyClass> classesIterator = dependencies.keySet().iterator();
            while (classesIterator.hasNext()) {
                PyClass memberClass = classesIterator.next();
                if (memberClass.equals(destinationClass) ||
                    ArrayUtil.contains(memberClass, destinationClass.getSuperClasses(null))) { // IF still would be available
                    classesIterator.remove();
                }
            }
        }

        for (MembersManager<? extends PyElement> manager : MANAGERS) {
            result.addAll(manager.getDependencies(dependencies));
        }
        result.addAll(dependencies.values());
        return result;
    }

    /**
     * Fetch dependencies this element depends on.
     * Manager should return them in format "class, where member declared" -- "member itself".
     * For example: if parameter is function, and this function uses field "foo" declared in class "bar", then manager (responsible for fields)
     * returns "bar" -] reference to "foo"
     *
     * @param member member to check dependencies for
     * @return dependencies
     */
    protected abstract MultiMap<PyClass, PyElement> getDependencies(PyElement member);

    /**
     * Get dependencies by members and classes they declared in (obtained from {@link #getDependencies(PyElement)})
     * For example manager, responsible for "extends SomeClass" members may return list of classes
     *
     * @param usedElements class-to-element dependencies
     * @return dependencies
     */
    protected abstract Collection<PyElement> getDependencies(MultiMap<PyClass, PyElement> usedElements);


    private static final class FindByElement extends NotNullPredicate<PyMemberInfo<PyElement>> {
        private final PyElement myPyElement;

        private FindByElement(PyElement pyElement) {
            myPyElement = pyElement;
        }

        @Override
        public boolean applyNotNull(PyMemberInfo<PyElement> input) {
            return input.getMember().equals(myPyElement);
        }
    }
}


