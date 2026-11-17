// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatementList;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

// JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
public class PyAddDunderMethodQuickFix implements LocalQuickFix {
    private final String dunderMethod;
    private final SmartPsiElementPointer<PyClass> myClassPointer;
    private final String myClassName;

    public PyAddDunderMethodQuickFix(PyClass pyClass, String dunderMethod) {
        this.dunderMethod = dunderMethod;
        myClassPointer = SmartPointerManager.getInstance(pyClass.getProject()).createSmartPsiElementPointer(pyClass);
        String name = pyClass.getName();
        myClassName = name != null ? name : "?";
    }

    public String getDunderMethod() {
        return dunderMethod;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixAddDunderMethodToClass(dunderMethod, myClassName);
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PyClass cls = myClassPointer.getElement();
        if (cls == null) {
            return;
        }
        PyStatementList stmtList = cls.getStatementList();

        PyFunctionBuilder builder = new PyFunctionBuilder(dunderMethod, cls);
        builder.parameter("self");
        if (dunderMethod.equals(PyNames.DUNDER_FORMAT)) {
            builder.parameter("format_spec", PyNames.TYPE_STR);
        }
        builder.statement("raise NotImplementedError('" + myClassName + "." + dunderMethod + " not implemented')");
        PyFunction method = builder.buildFunction();

        PyPsiRefactoringUtil.addElementToStatementList(method, stmtList, false);
    }
}
