// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import org.jspecify.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public final class PyMemberInfo<T extends PyElement> extends MemberInfoBase<T> {
    private final MembersManager<T> myMembersManager;
    private final boolean myCouldBeAbstract;
    private final PyElement myElementToStoreDependency;


    /**
     * @see #PyMemberInfo(PyElement, PyElement, boolean, String, Boolean, MembersManager, boolean)
     */
    PyMemberInfo(T member,
                 final boolean isStatic,
                 String displayName,
                 final @Nullable Boolean overrides,
                 MembersManager<T> membersManager,
                 final boolean couldBeAbstract) {
        this(member, member, isStatic, displayName, overrides, membersManager, couldBeAbstract);
    }

    /**
     * @param couldBeAbstract if element could be marked as abstract (like abstract method)
     * @param member          element itself
     * @param elementToStoreDependency if memeber's parent is copied and used to calculate dependency, provide it here
     * @param isStatic        is it static or not?
     * @param displayName     element display name
     * @param overrides       does it overrides something? TRUE if is overriden, FALSE if implemented, null if not implemented or overriden
     *                                              TODO: use primitive instead? "Implemeneted" has nothing to do with python duck-typing
     * @param membersManager  manager that knows how to handle this member
     */
    PyMemberInfo(T member,
                 PyElement elementToStoreDependency,
                 final boolean isStatic,
                 String displayName,
                 final @Nullable Boolean overrides,
                 MembersManager<T> membersManager,
                 final boolean couldBeAbstract) {
        super(member);
        this.isStatic = isStatic;
        this.myElementToStoreDependency = elementToStoreDependency;
        this.displayName = displayName;
        this.overrides = overrides;
        myMembersManager = membersManager;
        myCouldBeAbstract = couldBeAbstract;
    }

    public PyElement getElementToStoreDependency() {
        return myElementToStoreDependency;
    }

    MembersManager<T> getMembersManager() {
        return myMembersManager;
    }

    public boolean isCouldBeAbstract() {
        return myCouldBeAbstract;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PyMemberInfo) {
            return getMember().equals(((PyMemberInfo<?>)obj).getMember());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getMember().hashCode();
    }

    /**
     * Checks if moving this member to some class may create conflict.
     * @param destinationClass destination class to check
     * @return true if conflict.
     */
    public boolean hasConflict(PyClass destinationClass) {
        return myMembersManager.hasConflict(getMember(), destinationClass);
    }
}
