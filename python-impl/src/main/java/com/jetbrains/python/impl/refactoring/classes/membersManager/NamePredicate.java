// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import consulo.navigation.NavigationItem;
import com.jetbrains.python.impl.NotNullPredicate;
import com.jetbrains.python.psi.PyElement;


/**
 * Finds elements by name
 *
 * @author Ilya.Kazakevich
 */
class NamePredicate extends NotNullPredicate<PyElement> {
    private final String myName;


    NamePredicate(String name) {
        myName = name;
    }

    @Override
    protected boolean applyNotNull(PyElement input) {
        return myName.equals(input.getName());
    }

    /**
     * Checks if collection has {@link PyElement} with name equals to name of provided element.
     * If element has no name -- returns false any way.
     * @param needle element to take name from
     * @param stock collection elements to search between
     * @return true if stock contains element with name equal to needle's name
     */
    static boolean hasElementWithSameName(NavigationItem needle, Iterable<? extends PyElement> stock) {
        String name = needle.getName();
        if (name != null) {
            Optional<? extends PyElement> optional = Iterables.tryFind(stock, new NamePredicate(name));
            return optional.isPresent();
        }
        return false;
    }
}
