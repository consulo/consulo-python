// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.FoldRegion;
import consulo.document.Document;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Folds type annotations in Python code
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyHideTypeAnnotationsIntention", categories = "Python", fileExtensions = "py")
public class PyHideTypeAnnotationsIntention extends PyBaseIntentionAction {
    public PyHideTypeAnnotationsIntention() {
        setText(PyLocalize.intnNameHideTypeAnnotations());
    }

    @Override
    public boolean isAvailable(Project project, @Nullable Editor editor, @Nullable PsiFile psiFile) {
        if (!(psiFile instanceof PyFile)) {
            return false;
        }
        if (editor == null) {
            return false;
        }
        Document document = editor.getDocument();
        int offset = TargetElementUtil.adjustOffset(psiFile, document, editor.getCaretModel().getOffset());
        PsiElement element = PyUtil.findNonWhitespaceAtOffset(psiFile, offset);
        PyAnnotation annotation = PsiTreeUtil.getParentOfType(element, PyAnnotation.class);
        setText(PyLocalize.intnHideTypeAnnotations());
        return annotation != null;
    }

    @Override
    public void doInvoke(Project project, Editor editor, PsiFile file) {
        FoldRegion[] allRegions = editor.getFoldingModel().getAllFoldRegions();
        List<FoldRegion> result = new ArrayList<>();
        for (FoldRegion region : allRegions) {
            if (region.getGroup() != null && region.getGroup().toString().equals("Python type annotation")) {
                result.add(region);
            }
        }

        editor.getFoldingModel().runBatchFoldingOperation(() -> {
            for (FoldRegion region : result) {
                region.setExpanded(false);
            }
        });
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
