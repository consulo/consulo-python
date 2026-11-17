// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.google.common.collect.ImmutableMap;
import com.jetbrains.python.impl.codeInsight.intentions.PyTypeHintGenerationUtil.AnnotationInfo;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import one.util.streamex.EntryStream;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyConvertTypeCommentToVariableAnnotationIntention", categories = "Python", fileExtensions = "py")
public final class PyConvertTypeCommentToVariableAnnotationIntention extends PyBaseIntentionAction {
    @Override
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiComment typeComment = findCommentUnderCaret(editor, file);
        if (typeComment != null) {
            SmartPointerManager manager = SmartPointerManager.getInstance(project);
            SmartPsiElementPointer<PsiComment> commentPointer = manager.createSmartPsiElementPointer(typeComment);
            Map<PyTargetExpression, String> map = mapTargetsToAnnotations(typeComment);
            if (!map.isEmpty()) {
                if (typeComment.getParent() instanceof PyAssignmentStatement && map.size() == 1) {
                    PyTargetExpression target = ContainerUtil.getFirstItem(map.keySet());
                    assert target != null;
                    PyTypeHintGenerationUtil.insertVariableAnnotation(target, null, new AnnotationInfo(map.get(target)), false);
                }
                else {
                    for (Map.Entry<PyTargetExpression, String> entry : new ArrayList<>(map.entrySet())) {
                        PyTypeHintGenerationUtil.insertVariableAnnotation(entry.getKey(), null, new AnnotationInfo(entry.getValue()), false);
                    }
                }

                PsiComment staleComment = commentPointer.getElement();
                if (staleComment != null) {
                    staleComment.delete();
                }
            }
        }
    }

    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnConvertTypeCommentToVariableAnnotation();
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
        if (psiFile instanceof PyFile && LanguageLevel.forElement(psiFile).isAtLeast(LanguageLevel.PYTHON36)) {
            PsiComment comment = findCommentUnderCaret(editor, psiFile);
            return comment != null && isSuitableTypeComment(comment);
        }
        return false;
    }

    private static @Nullable PsiComment findCommentUnderCaret(Editor editor, PsiFile file) {
        PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        return PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
    }

    private static boolean isSuitableTypeComment(PsiComment comment) {
        String annotation = PyTypingTypeProvider.getTypeCommentValue(comment.getText());
        return annotation != null && !mapTargetsToAnnotations(comment).isEmpty();
    }

    private static Map<PyTargetExpression, String> mapTargetsToAnnotations(PsiComment typeComment) {
        PsiElement parent = typeComment.getParent();
        if (parent instanceof PyAssignmentStatement assignment) {
            PyExpression[] rawTargets = assignment.getRawTargets();
            if (rawTargets.length == 1) {
                return mapTargetsToAnnotations(rawTargets[0], typeComment);
            }
        }
        else if (parent instanceof PyForPart forPart) {
            PyExpression target = forPart.getTarget();
            if (target != null) {
                return mapTargetsToAnnotations(target, typeComment);
            }
        }
        else if (parent instanceof PyWithStatement) {
            PyWithItem[] withItems = ((PyWithStatement)parent).getWithItems();
            if (withItems.length == 1) {
                PyExpression target = withItems[0].getTarget();
                if (target != null) {
                    return mapTargetsToAnnotations(target, typeComment);
                }
            }
        }
        return Collections.emptyMap();
    }

    private static Map<PyTargetExpression, String> mapTargetsToAnnotations(PyExpression targetExpr,
            PsiComment typeComment) {
        PyTargetExpression firstTarget = PsiTreeUtil.findChildOfType(targetExpr, PyTargetExpression.class, false);
        if (firstTarget == null || firstTarget.getTypeComment() != typeComment) {
            return Collections.emptyMap();
        }

        String annotation = PyTypingTypeProvider.getTypeCommentValue(typeComment.getText());
        if (annotation != null) {
            if (targetExpr instanceof PyTargetExpression) {
                return ImmutableMap.of((PyTargetExpression)targetExpr, annotation);
            }

            PyElementGenerator generator = PyElementGenerator.getInstance(targetExpr.getProject());
            PyExpression parsed;
            try {
                parsed = generator.createExpressionFromText(LanguageLevel.PYTHON36, annotation);
            }
            catch (IncorrectOperationException e) {
                return Collections.emptyMap();
            }
            Map<PyTargetExpression, PyExpression> targetToExpr = PyTypingTypeProvider.mapTargetsToAnnotations(targetExpr, parsed);
            return EntryStream.of(targetToExpr).mapValues(PyExpression::getText).toCustomMap(LinkedHashMap::new);
        }
        return Collections.emptyMap();
    }
}

