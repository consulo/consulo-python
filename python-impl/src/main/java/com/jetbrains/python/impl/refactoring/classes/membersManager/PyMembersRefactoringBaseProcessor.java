// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.event.RefactoringEventData;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Processor for member-based refactorings. It moves members from one place to another using {@link MembersManager}.
 * Inheritors only need to implement {@link UsageViewDescriptor} methods (while this class also implements this interface)
 *
 * @author Ilya.Kazakevich
 */
public abstract class PyMembersRefactoringBaseProcessor extends BaseRefactoringProcessor implements UsageViewDescriptor {

    protected final Collection<PyMemberInfo<PyElement>> myMembersToMove;
    protected final PyClass myFrom;
    private final PyClass[] myTo;

    /**
     * @param membersToMove what to move
     * @param from          source
     * @param to            where to move
     */
    protected PyMembersRefactoringBaseProcessor(
        Project project,
        Collection<PyMemberInfo<PyElement>> membersToMove,
        PyClass from,
        PyClass ... to) {
        super(project);
        myFrom = from;
        myMembersToMove = new ArrayList<>(membersToMove);
        myTo = to.clone();
    }

    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
        return this;
    }

    @Override
    public PsiElement[] getElements() {
        return myTo.clone();
    }

    /**
     * @return destinations (so user would be able to choose if she wants to move member to a certain place or not)
     */
    @Override
    protected final PyUsageInfo[] findUsages() {
        List<PyUsageInfo> result = new ArrayList<>(myTo.length);
        for (PyClass pyDestinationClass : myTo) {
            result.add(new PyUsageInfo(pyDestinationClass));
        }
        return result.toArray(new PyUsageInfo[0]);
    }

    @Override
    protected final void performRefactoring(UsageInfo[] usages) {
        Collection<PyClass> destinations = new ArrayList<>(usages.length);
        for (UsageInfo usage : usages) {
            if (!(usage instanceof PyUsageInfo)) {
                throw new IllegalArgumentException("Only PyUsageInfo is accepted here");
            }
            //We collect destination info to pass it to members manager
            destinations.add(((PyUsageInfo)usage).getTo());
        }
        MembersManager.moveAllMembers(myMembersToMove, myFrom, destinations.toArray(PyClass.EMPTY_ARRAY));
        PyClassRefactoringUtil.optimizeImports(myFrom.getContainingFile()); // To remove unneeded imports
    }

    @Override
    protected @Nullable RefactoringEventData getBeforeData() {
        RefactoringEventData data = new RefactoringEventData();
        data.addElement(myFrom);
        data.addMembers(myMembersToMove.toArray(new PyMemberInfo[0]), info -> info.getMember());
        return data;
    }


    @Override
    protected @Nullable RefactoringEventData getAfterData(UsageInfo[] usages) {
        RefactoringEventData data = new RefactoringEventData();
        data.addElements(myTo);
        return data;
    }
}
