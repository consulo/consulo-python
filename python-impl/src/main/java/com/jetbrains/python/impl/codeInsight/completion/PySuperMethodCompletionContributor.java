// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.editor.completion.lookup.TailTypeDecorator;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;

import java.util.HashSet;
import java.util.Set;

import static consulo.language.pattern.PlatformPatterns.psiElement;


@ExtensionImpl
public final class PySuperMethodCompletionContributor extends CompletionContributor implements DumbAware {
    public PySuperMethodCompletionContributor() {
        extend(CompletionType.BASIC,
            psiElement().afterLeafSkipping(psiElement().whitespace(), psiElement().withElementType(PyTokenTypes.DEF_KEYWORD)),
            new CompletionProvider() {
                @Override
                @RequiredReadAction
                public void addCompletions(CompletionParameters parameters,
                                           ProcessingContext context,
                                           CompletionResultSet result) {
                    PsiElement position = parameters.getOriginalPosition();
                    PyClass containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);
                    PsiElement nextElement = position != null ? position.getNextSibling() : null;
                    if (containingClass == null && position instanceof PsiWhiteSpace) {
                        position = PsiTreeUtil.prevLeaf(position);
                        containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);
                    }
                    if (containingClass == null) {
                        return;
                    }
                    Set<String> seenNames = new HashSet<>();
                    for (PyFunction function : containingClass.getMethods()) {
                        seenNames.add(function.getName());
                    }
                    TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(containingClass.getProject(),
                        containingClass.getContainingFile());
                    for (PyFunction superMethod : PyPsiRefactoringUtil.getAllSuperMethods(containingClass, typeEvalContext)) {
                        if (!seenNames.add(superMethod.getName())) {
                            continue;
                        }
                        StringBuilder builder = new StringBuilder();
                        builder.append(superMethod.getName());
                        if (!(nextElement instanceof PyParameterList)) {
                            PyParameterList parameterList;
                            boolean copyAnnotations = PyPsiRefactoringUtil.shouldCopyAnnotations(superMethod, parameters.getOriginalFile());
                            if (copyAnnotations) {
                                parameterList = superMethod.getParameterList();
                            }
                            else {
                                parameterList = stripAnnotations(superMethod.getParameterList());
                            }
                            builder.append(parameterList.getText());
                            if (superMethod.getAnnotation() != null && copyAnnotations && !superMethod.getName().equals("__init__")) {
                                builder.append(" ")
                                    .append(superMethod.getAnnotation().getText())
                                    .append(":");
                            }
                            else if (superMethod.getTypeComment() != null) {
                                builder.append(":  ")
                                    .append(superMethod.getTypeComment().getText());
                            }
                            else {
                                builder.append(":");
                            }
                        }
                        LookupElementBuilder element = LookupElementBuilder.create(builder.toString())
                            .withInsertHandler((insertionContext, item) -> {
                                PsiElement methodName = insertionContext.getFile().findElementAt(insertionContext.getStartOffset());
                                if (methodName == null || !(methodName.getParent() instanceof PyFunction insertedMethod)) {
                                    return;
                                }
                                WriteCommandAction.writeCommandAction(insertionContext.getFile()).run(() -> {
                                    PyClassRefactoringUtil.transplantImportsFromSignature(superMethod, insertedMethod);
                                    addFunctionModifier(superMethod, insertedMethod);
                                });
                            })
                            .withIcon(IconDescriptorUpdaters.getIcon(superMethod, 0));
                        result.addElement(TailTypeDecorator.withTail(element, TailType.NONE));
                    }
                }
            });
    }

    private static void addFunctionModifier(PyFunction superMethod, PyFunction insertedMethod) {
        PyFunction.Modifier newModifier = insertedMethod.getModifier();
        if (newModifier != null) {
            return;
        }

        PyFunction.Modifier modifier = superMethod.getModifier();
        if (modifier == null) {
            return;
        }

        PyUtil.addDecorator(insertedMethod, "@" + modifier.getDecoratorName());
    }

    private static <T extends PsiElement> T stripAnnotations(T element) {
        @SuppressWarnings("unchecked") T result = (T) element.copy();
        result.accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyAnnotation(PyAnnotation node) {
                node.delete();
            }
        });
        return result;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
