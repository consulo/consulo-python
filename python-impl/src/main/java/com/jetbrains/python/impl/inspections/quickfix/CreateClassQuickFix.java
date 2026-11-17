// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import consulo.language.editor.inspection.FileModifier;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PythonTemplateRunner;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class CreateClassQuickFix implements LocalQuickFix {
    private final String myClassName;
    private final SmartPsiElementPointer<PsiFile> myTargetFile;

    public CreateClassQuickFix(String className, PsiFile targetFile) {
        myClassName = className;
        myTargetFile = SmartPointerManager.getInstance(targetFile.getProject()).createSmartPsiElementPointer(targetFile);
    }

    @Override
    public LocalizeValue getName() {
        PsiFile targetFile = myTargetFile.getElement();
        if (targetFile != null) {
            return PyLocalize.qfixCreateClassInModule(myClassName, targetFile.getName());
        }
        return PyLocalize.qfixCreateClass0(myClassName);
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement unresolvedReference = descriptor.getPsiElement();
        if (unresolvedReference == null) {
            return;
        }

        PsiFile targetFile = myTargetFile.getElement();
        if (targetFile == null) {
            return;
        }

        boolean isPy3K = LanguageLevel.forElement(targetFile).isPy3K();
        String superClass = isPy3K ? "" : "(object)";
        PyClass pyClass = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyClass.class,
                "class " + myClassName + superClass + ":\n" +
                "    pass");
        InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(unresolvedReference.getProject());
        PsiLanguageInjectionHost injectionHost = injectionManager.getInjectionHost(unresolvedReference);
        if (unresolvedReference.getContainingFile() == targetFile) {
            PsiElement insertionAnchor = PyPsiUtils.getParentRightBefore(unresolvedReference, targetFile);
            assert insertionAnchor != null;
            pyClass = (PyClass)insertionAnchor.getParent().addBefore(pyClass, insertionAnchor);
        }
        else if (injectionHost != null && injectionHost.getContainingFile() == targetFile) {
            PsiElement insertionAnchor = PyPsiUtils.getParentRightBefore(injectionHost, targetFile);
            assert insertionAnchor != null;
            pyClass = (PyClass)insertionAnchor.getParent().addBefore(pyClass, insertionAnchor);
        }
        else {
            pyClass = (PyClass)targetFile.add(pyClass);
        }
        pyClass = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(pyClass);
        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(pyClass);
        if (!isPy3K) {
            builder.replaceElement(pyClass.getSuperClassExpressions()[0], "object");
        }
        builder.replaceElement(pyClass.getStatementList(), PyNames.PASS);

        PythonTemplateRunner.runTemplate(pyClass.getContainingFile(), builder);
    }

    @Override
    public FileModifier getFileModifierForPreview(PsiFile target) {
        return new CreateClassQuickFix(myClassName, target);
    }
}
