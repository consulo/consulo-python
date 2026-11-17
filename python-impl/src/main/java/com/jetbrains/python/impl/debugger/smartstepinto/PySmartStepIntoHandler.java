// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.smartstepinto;

import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.document.util.DocumentUtil;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.frame.XSuspendContext;
import consulo.execution.debug.step.XSmartStepIntoHandler;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.debugger.PyDebugProcess;
import com.jetbrains.python.impl.debugger.PyStackFrame;
import com.jetbrains.python.impl.debugger.settings.PyDebuggerSettings;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PySmartStepIntoHandler implements XSmartStepIntoHandler<PySmartStepIntoVariant> {
    private final XDebugSession mySession;
    private final PyDebugProcess myProcess;

    public PySmartStepIntoHandler(PyDebugProcess process) {
        mySession = process.getSession();
        myProcess = process;
    }

    @Override
    public void startStepInto(PySmartStepIntoVariant smartStepIntoVariant, @Nullable XSuspendContext context) {
        myProcess.startSmartStepInto(smartStepIntoVariant);
    }

    @Override
    public String getPopupTitle(XSourcePosition position) {
        return PyLocalize.debugPopupTitleStepIntoFunction().get();
    }

    @Override
    public CompletableFuture<List<PySmartStepIntoVariant>> computeSmartStepVariantsAsync(XSourcePosition position) {
        CompletableFuture<List<PySmartStepIntoVariant>> promise = new CompletableFuture<>();
        Application.get().executeOnPooledThread(() -> {
            List<PySmartStepIntoVariant> computedVariants = findVariants(position);
            promise.complete(computedVariants);
        });
        return promise;
    }

    private List<PySmartStepIntoVariant> findVariants(XSourcePosition position) {
        PyStackFrame currentFrame = (PyStackFrame)mySession.getCurrentStackFrame();
        if (currentFrame == null || currentFrame.isComprehension()) {
            return Collections.emptyList();
        }

        PySmartStepIntoContext context = ReadAction.compute(() -> createSmartStepIntoContext(currentFrame));
        if (context == null) {
            return Collections.emptyList();
        }

        Document document = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(position.getFile()));
        if (document == null) {
            return Collections.emptyList();
        }

        List<Pair<String, Boolean>> variantsFromPython = myProcess.getSmartStepIntoVariants(context.getStartLine(), context.getEndLine());

        if (variantsFromPython.isEmpty()) {
            return Collections.emptyList();
        }

        return ReadAction.compute(() -> removePossiblyUnreachableVariants(document, position.getLine(), variantsFromPython, context));
    }

    @Override
    public List<PySmartStepIntoVariant> computeSmartStepVariants(XSourcePosition position) {
        return findVariants(position);
    }

    private List<PySmartStepIntoVariant> removePossiblyUnreachableVariants(Document document, int line,
                                                                           List<Pair<String, Boolean>> variantsFromPython,
                                                                           PySmartStepIntoContext context) {
        PsiElement expression = findSmartStepIntoBaseExpression(mySession.getProject(), document, line);
        if (expression == null) {
            return Collections.emptyList();
        }

        List<PySmartStepIntoVariant> result = new ArrayList<>();

        // We are going to filter the variants that PyCharm cannot resolve to be sure we don't suggest stepping into a native function.
        expression.accept(new PySmartStepIntoVariantVisitor(result, variantsFromPython, context));

        return result;
    }

    /**
     * Find an expression within which we are going to search for smart step into variants.
     * <p>
     * Note, that expressions can span multiple lines, e.g. for parenthesized expressions or argument lists.
     */
    public static @Nullable PsiElement findSmartStepIntoBaseExpression(Project project, Document document, int line) {
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (file == null) {
            return null;
        }

        PsiElement element = file.findElementAt(DocumentUtil.getFirstNonSpaceCharOffset(document, line));
        if (element == null) {
            return null;
        }

        // Allow multiline smart step into for argument list case.
        PsiElement argumentList = PsiTreeUtil.getParentOfType(element, PyArgumentList.class);
        if (argumentList != null) {
            return argumentList.getParent();
        }

        // Allow multiline smart step into for parenthesized multiline expressions.
        PsiElement parenthesizedExpression = PsiTreeUtil.getParentOfType(element, PyParenthesizedExpression.class);
        if (parenthesizedExpression != null) {
            return parenthesizedExpression;
        }

        // If it's not an argument list or parenthesized expression, simply find and return top expression at the line.
        PsiElement parent = element.getParent();
        int lineStartOffset = document.getLineStartOffset(line);
        int lineEndOffset = document.getLineEndOffset(line);
        while (parent != null &&  lineStartOffset <= parent.getTextOffset() && parent.getTextOffset() < lineEndOffset) {
            element = parent;
            parent = element.getParent();
        }

        if (element instanceof PyExpression) {
            return element;
        }

        Ref<PsiElement> psiElementRef = new Ref<>();

        element.accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyBinaryExpression(PyBinaryExpression node) {
                storeElement(node);
            }

            @Override
            public void visitPyCallExpression(PyCallExpression node) {
                storeElement(node);
            }

            @Override
            public void visitPyComprehensionElement(PyComprehensionElement node) {
                storeElement(node);
            }

            @Override
            public void visitPyElement(PyElement node) {
                if (node instanceof PyDecorator) {
                    storeElement(node);
                }
                super.visitPyElement(node);
            }

            @Override
            public void visitPyDecoratorList(PyDecoratorList node) {
                storeElement(node);
                super.visitPyDecoratorList(node);
            }

            private void storeElement(PsiElement node) {
                if (psiElementRef.isNull()) {
                    psiElementRef.set(node);
                }
            }
        });

        return psiElementRef.get();
    }

    @Override
    public CompletableFuture<List<PySmartStepIntoVariant>> computeStepIntoVariants(XSourcePosition position) {
        if (PyDebuggerSettings.getInstance().isAlwaysDoSmartStepInto()) {
            return computeSmartStepVariantsAsync(position);
        }
        return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    /**
     * Creates and stores a smart step into context for the given frame.
     */
    public @Nullable PySmartStepIntoContext createSmartStepIntoContext(PyStackFrame frame) {
        XSourcePosition position = frame.getSourcePosition();
        if (position == null) {
            return null;
        }

        Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
        if (document == null) {
            return null;
        }

        PsiElement expression = findSmartStepIntoBaseExpression(mySession.getProject(), document, position.getLine());

        if (expression != null) {
            TextRange range = expression.getTextRange();
            int startLine = document.getLineNumber(range.getStartOffset());
            int endLine = document.getLineNumber(range.getEndOffset());
            return new PySmartStepIntoContext(startLine + 1, endLine + 1, frame);
        }

        return null;
    }
}
