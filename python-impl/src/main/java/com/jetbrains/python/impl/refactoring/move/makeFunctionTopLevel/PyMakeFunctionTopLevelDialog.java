// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.move.makeFunctionTopLevel;

import com.jetbrains.python.impl.refactoring.move.PyBaseMoveDialog;
import com.jetbrains.python.impl.refactoring.move.PyMoveRefactoringUtil;
import com.jetbrains.python.psi.PyFunction;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class PyMakeFunctionTopLevelDialog extends PyBaseMoveDialog {

    public PyMakeFunctionTopLevelDialog(Project project, PyFunction function, String sourcePath, String destinationPath) {
        super(project, sourcePath, destinationPath);
        String functionName = PyMoveRefactoringUtil.getPresentableName(function);
        if (function.getContainingClass() != null) {
            setTitle(PyLocalize.refactoringMakeMethodTopLevelDialogTitle());
            myDescription.setText(PyLocalize.refactoringMakeMethodTopLevelDialogDescription(functionName).get());
        }
        else {
            setTitle(PyLocalize.refactoringMakeLocalFunctionTopLevelDialogTitle());
            myDescription.setText(PyLocalize.refactoringMakeLocalFunctionTopLevelDialogDescription(functionName).get());
        }
        myExtraPanel.setVisible(false);
        init();
    }

    @Override
    protected String getHelpId() {
        return "python.reference.makeFunctionTopLevel";
    }

    @Override
    protected @Nullable String getDimensionServiceKey() {
        return "#com.jetbrains.python.refactoring.move.PyMakeFunctionTopLevelDialog";
    }
}
