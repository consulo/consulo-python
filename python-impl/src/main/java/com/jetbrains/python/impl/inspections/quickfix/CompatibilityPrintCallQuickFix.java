// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.CommonProblemDescriptor;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.BatchQuickFix;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// Consulo: JB ModCommandBatchQuickFix is expressed as LocalQuickFix + BatchQuickFix (ModCommand API is not available in Consulo)
public class CompatibilityPrintCallQuickFix implements LocalQuickFix, BatchQuickFix<CommonProblemDescriptor> {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixStatementEffect();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        perform(project, List.of(descriptor));
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project,
                         CommonProblemDescriptor[] descriptors,
                         List<PsiElement> psiElementsToIgnore,
                         Runnable refreshViews) {
        List<ProblemDescriptor> problemDescriptors = new ArrayList<>();
        for (CommonProblemDescriptor descriptor : descriptors) {
            if (descriptor instanceof ProblemDescriptor problemDescriptor) {
                problemDescriptors.add(problemDescriptor);
            }
        }
        perform(project, problemDescriptors);
    }

    // JB: ModCommand perform(Project, List<ProblemDescriptor>)
    @RequiredWriteAction
    private static void perform(Project project, List<ProblemDescriptor> descriptors) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        // Consulo: no ModPsiUpdater.getWritable, the fix runs on the physical files
        List<PsiElement> writableElements = ContainerUtil.mapNotNull(descriptors, d -> d.getStartElement());
        MultiMap<PsiFile, PsiElement> groupedElements =
            ContainerUtil.groupBy(writableElements, element -> element.getContainingFile());
        for (Map.Entry<PsiFile, Collection<PsiElement>> entry : groupedElements.entrySet()) {
            PsiFile file = entry.getKey();
            for (PsiElement element : entry.getValue()) {
                if (element.isValid()) {
                    replace(element, elementGenerator);
                }
            }
            AddImportHelper.addOrUpdateFromImportStatement(file,
                "__future__",
                "print_function",
                null,
                AddImportHelper.ImportPriority.FUTURE,
                null);
        }
    }

    private static void replace(PsiElement expression, PyElementGenerator elementGenerator) {
        StringBuilder stringBuilder = new StringBuilder("print(");
        PyExpression[] target = PsiTreeUtil.getChildrenOfType(expression, PyExpression.class);
        if (target != null) {
            stringBuilder.append(StringUtil.join(target, o -> o.getText(), ", "));
        }
        stringBuilder.append(")");
        expression.replace(elementGenerator.createFromText(LanguageLevel.forElement(expression), PyElement.class,
            stringBuilder.toString()));
    }
}
