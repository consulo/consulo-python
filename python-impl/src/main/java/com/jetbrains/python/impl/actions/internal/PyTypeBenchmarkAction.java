// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.actions.internal;

import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ActionImpl;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.WriteCommandAction;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationService;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ActionImpl(id = "PyTypeBenchmarkAction")
public class PyTypeBenchmarkAction extends AnAction implements AnActionWithSyncUpdate {
    private static final int ITERATIONS = 10;

    public PyTypeBenchmarkAction() {
        super(PyLocalize.actionPytypebenchmarkactionText());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        Editor editor = e.getData(Editor.KEY);
        if (editor == null) {
            return;
        }
        Document document = editor.getDocument();
        if (!(PsiDocumentManager.getInstance(project).getPsiFile(document) instanceof PyFile psiFile)) {
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(
            project,
            LocalizeValue.localizeTODO("Benchmarking Python type evaluation"),
            false
        ) {
            @Override
            public void run(ProgressIndicator indicator) {
                List<String> results = new ArrayList<>();
                String marker = "\n# bench";

                for (int i = 1; i <= ITERATIONS; i++) {
                    indicator.setText(LocalizeValue.of("Iteration " + i + " of " + ITERATIONS));
                    indicator.setFraction((double) (i - 1) / ITERATIONS);

                    int[] elementCount = {0};
                    long[] elapsedMs = {0L};
                    ProgressManager.getInstance().executeNonCancelableSection(() -> ReadAction.run(() -> {
                        TypeEvalContext context = TypeEvalContext.userInitiated(project, psiFile);
                        Collection<PyTypedElement> elements = PsiTreeUtil.collectElementsOfType(psiFile, PyTypedElement.class);
                        elementCount[0] = elements.size();
                        long start = System.currentTimeMillis();
                        for (PyTypedElement element : elements) {
                            context.getType(element);
                        }
                        elapsedMs[0] = System.currentTimeMillis() - start;
                    }));

                    results.add("Iteration " + i + ": " + elementCount[0] + " elements, " + elapsedMs[0] + " ms");

                    // Invalidate PSI/type caches by making a document edit and reverting it
                    Application.get().invokeAndWait(() -> {
                        WriteCommandAction.writeCommandAction(psiFile).withName("Benchmark Cache Invalidation").run(() -> {
                            document.insertString(document.getTextLength(), marker);
                            PsiDocumentManager.getInstance(project).commitDocument(document);
                        });
                        WriteCommandAction.writeCommandAction(psiFile).withName("Benchmark Cache Invalidation").run(() -> {
                            String text = document.getText();
                            int idx = text.lastIndexOf(marker);
                            if (idx >= 0) {
                                document.deleteString(idx, idx + marker.length());
                                PsiDocumentManager.getInstance(project).commitDocument(document);
                            }
                        });
                    });
                }

                indicator.setFraction(1.0);
                String content = String.join("<br>", results);
                NotificationService.getInstance()
                    .newInfo(PyPsiInternalNotificationGroupContributor.PYTHON_INTERNAL)
                    .title(LocalizeValue.localizeTODO("Python type evaluation benchmark"))
                    .content(LocalizeValue.of(content))
                    .notify(project);
            }
        });
    }

    @Override
    public void update(AnActionEvent e) {
        PsiFile psiFile = e.getData(PsiFile.KEY);
        e.getPresentation().setEnabledAndVisible(psiFile instanceof PyFile);
    }
}
