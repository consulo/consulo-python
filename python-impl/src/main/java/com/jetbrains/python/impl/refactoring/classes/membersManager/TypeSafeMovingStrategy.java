// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import consulo.language.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Moves members checking types at runtime.
 *
 * @author Ilya.Kazakevich
 */
final class TypeSafeMovingStrategy<T extends PyElement> {
    private final PyClass myFrom;
    private final MembersManager<T> myManager;
    private final Collection<PyMemberInfo<T>> myMemberInfoCollection;
    private final PyClass[] myTo;

    /**
     * Move members.
     * @param from source
     * @param manager manager to be used
     * @param memberInfoCollection what to move
     * @param to where
     */
    @SuppressWarnings({"unchecked", "rawtypes"}) //We check types at runtime
    static void moveCheckingTypesAtRunTime(PyClass from,
                                                                                 MembersManager<?> manager,
                                                                                 Collection<? extends PyMemberInfo<PyElement>> memberInfoCollection,
                                                                                 PyClass ... to) {
        manager.checkElementTypes((Collection)MembersManager.fetchElements(memberInfoCollection));
        new TypeSafeMovingStrategy(from, manager, memberInfoCollection, to).moveTyped();
    }

    private TypeSafeMovingStrategy(PyClass from,
                                                                 MembersManager<T> manager,
                                                                 Collection<PyMemberInfo<T>> memberInfoCollection,
                                                                 PyClass[] to) {
        myFrom = from;
        myManager = manager;
        myMemberInfoCollection = new ArrayList<>(memberInfoCollection);
        myTo = to.clone();
    }


    /**
     * While types are already checked at runtime, this method could move everything in type-safe manner.
     */
    private void moveTyped() {
        Collection<T> elementsCollection = MembersManager.fetchElements(myMemberInfoCollection);
        Collection<? extends PyElement> references = myManager.getElementsToStoreReferences(elementsCollection);

        // Store references to add required imports
        for (PyElement element : references) {
            PyClassRefactoringUtil.rememberNamedReferences(element, PyNames.CANONICAL_SELF); //"self" is not reference we need to move
        }

        // Move
        Collection<PyElement> newElements = myManager.moveMembers(myFrom, myMemberInfoCollection, myTo);

        // Restore references to add appropriate imports
        for (PyElement element : newElements) {
            PyClassRefactoringUtil.restoreNamedReferences(element, null, references.toArray(PsiElement.EMPTY_ARRAY));
        }
    }
}
