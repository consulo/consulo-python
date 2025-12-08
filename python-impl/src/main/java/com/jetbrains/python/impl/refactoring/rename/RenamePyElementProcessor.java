/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.refactoring.rename;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.refactoring.rename.RenamePsiElementProcessor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.util.collection.MultiMap;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;

/**
 * @author yole
 */
public abstract class RenamePyElementProcessor extends RenamePsiElementProcessor {
    @Override
    @RequiredReadAction
    public void findExistingNameConflicts(PsiElement element, String newName, MultiMap<PsiElement, LocalizeValue> conflicts) {
        PyElement container = PsiTreeUtil.getParentOfType(element, ScopeOwner.class);
        if (container instanceof PyFile pyFile) {
            PyClass conflictingClass = pyFile.findTopLevelClass(newName);
            if (conflictingClass != null) {
                conflicts.putValue(
                    conflictingClass,
                    LocalizeValue.localizeTODO("A class named '" + newName + "' is already defined in " + pyFile.getName())
                );
            }
            PyFunction conflictingFunction = pyFile.findTopLevelFunction(newName);
            if (conflictingFunction != null) {
                conflicts.putValue(
                    conflictingFunction,
                    LocalizeValue.localizeTODO("A function named '" + newName + "' is already defined in " + pyFile.getName())
                );
            }
            PyTargetExpression conflictingVariable = pyFile.findTopLevelAttribute(newName);
            if (conflictingVariable != null) {
                conflicts.putValue(
                    conflictingFunction,
                    LocalizeValue.localizeTODO("A variable named '" + newName + "' is already defined in " + pyFile.getName())
                );
            }
        }
        else if (container instanceof PyClass) {
            PyClass pyClass = (PyClass) container;
            PyClass conflictingClass = pyClass.findNestedClass(newName, true);
            if (conflictingClass != null) {
                conflicts.putValue(
                    conflictingClass,
                    LocalizeValue.localizeTODO("A class named '" + newName + "' is already defined in class '" + pyClass.getName() + "'")
                );
            }
            PyFunction conflictingFunction = pyClass.findMethodByName(newName, true, null);
            if (conflictingFunction != null) {
                conflicts.putValue(
                    conflictingFunction,
                    LocalizeValue.localizeTODO("A function named '" + newName + "' is already defined in class '" + pyClass.getName() + "'")
                );
            }
            PyTargetExpression conflictingAttribute = pyClass.findClassAttribute(newName, true, null);
            if (conflictingAttribute != null) {
                conflicts.putValue(
                    conflictingAttribute,
                    LocalizeValue.localizeTODO("An attribute named '" + newName + "' is already defined in class '" + pyClass.getName() + "'")
                );
            }
        }
    }

    @Override
    public String getHelpID(PsiElement element) {
        return "python.reference.rename";
    }
}
