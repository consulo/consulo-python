// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyDecoratable;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTryPart;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.controlFlow.ConditionalInstruction;
import consulo.language.controlFlow.Instruction;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ref.SimpleReference;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.impl.psi.impl.PyTypeDeclarationStatementNavigator.isTypeDeclarationTarget;

/**
 * Annotates declarations that unconditionally override others without these being used.
 */
@ExtensionImpl
public final class PyRedeclarationInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameRedeclaration();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        if (context.getUsesExternalTypeEngine()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new Visitor(holder, context);
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            if (!PyKnownDecoratorUtil.hasUnknownDecorator(node, myTypeEvalContext) &&
                !PyKnownDecoratorUtil.hasRedeclarationDecorator(node, myTypeEvalContext)) {
                processElement(node);
            }
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            if (node.isQualified() || PyNames.UNDERSCORE.equals(node.getText()) || isTypeDeclarationTarget(node)) {
                return;
            }
            ScopeOwner owner = ScopeUtil.getScopeOwner(node);
            if (owner instanceof PyFile || owner instanceof PyClass) {
                processElement(node);
            }
        }

        @Override
        public void visitPyClass(PyClass node) {
            if (!isDecorated(node)) {
                processElement(node);
            }
        }

        private static boolean isDecorated(PyDecoratable node) {
            boolean isDecorated = false;
            PyDecoratorList decoratorList = node.getDecoratorList();
            if (decoratorList != null) {
                PyDecorator[] decorators = decoratorList.getDecorators();
                if (decorators.length > 0) {
                    isDecorated = true;
                }
            }
            return isDecorated;
        }

        private void processElement(PsiNameIdentifierOwner element) {
            String name = element.getName();
            ScopeOwner owner = ScopeUtil.getScopeOwner(element);
            if (owner != null && name != null) {
                Instruction[] instructions = ControlFlowCache.getControlFlow(owner).getInstructions();
                PsiElement elementInControlFlow = element;
                if (element instanceof PyTargetExpression) {
                    PyImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PyImportStatement.class);
                    if (importStatement != null) {
                        elementInControlFlow = importStatement;
                    }
                }
                int startInstruction = ControlFlowUtil.findInstructionNumberByElement(instructions, elementInControlFlow);
                if (startInstruction < 0) {
                    return;
                }
                SimpleReference<PsiElement> readElementRef = SimpleReference.create(null);
                SimpleReference<PsiElement> writeElementRef = SimpleReference.create(null);
                SimpleReference<Boolean> underPossiblyFalseCondition = SimpleReference.create(false);
                ControlFlowUtil.iteratePrev(startInstruction, instructions, instruction -> {
                    if (instruction instanceof ReadWriteInstruction rwInstruction && instruction.num() != startInstruction) {
                        if (name.equals(rwInstruction.getName())) {
                            PsiElement originalElement = rwInstruction.getElement();
                            if (originalElement != null) {
                                if (rwInstruction.getAccess().isReadAccess()) {
                                    readElementRef.set(originalElement);
                                }
                                if (rwInstruction.getAccess().isWriteAccess() && originalElement != element) {
                                    if (PyiUtil.isOverload(originalElement, myTypeEvalContext) || isTypeDeclarationTarget(originalElement)) {
                                        return ControlFlowUtil.Operation.NEXT;
                                    }
                                    else if (!underPossiblyFalseCondition.get()) {
                                        writeElementRef.set(originalElement);
                                    }
                                }
                            }
                            return ControlFlowUtil.Operation.CONTINUE;
                        }
                    }
                    if (possiblyFalseCondition(instruction)) {
                        underPossiblyFalseCondition.set(true);
                    }
                    return ControlFlowUtil.Operation.NEXT;
                });
                PsiElement writeElement = writeElementRef.get();
                if (writeElement != null && readElementRef.get() == null) {
                    // Repeated patterns are reported as syntactic errors in an annotator
                    if (PsiTreeUtil.findCommonParent(writeElement, element) instanceof PyPattern) {
                        return;
                    }
                    List<LocalQuickFix> quickFixes = new ArrayList<>();
                    if (suggestRename(element, writeElement)) {
                        LocalQuickFix quickFix = PythonUiService.getInstance().createPyRenameElementQuickFix(element);
                        if (quickFix != null) {
                            quickFixes.add(quickFix);
                        }
                    }
                    PsiElement identifier = element.getNameIdentifier();
                    registerProblem(identifier != null ? identifier : element,
                                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspRedeclaredName(a[0]), name),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                    quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
                }
            }
        }

        private static boolean possiblyFalseCondition(Instruction instruction) {
            if (instruction instanceof ConditionalInstruction conditionalInstruction) {
                if (conditionalInstruction.getCondition() instanceof PyExpression condition) {
                    return conditionalInstruction.getResult()
                           ? !PyEvaluator.evaluateAsBoolean(condition, false)
                           : PyEvaluator.evaluateAsBoolean(condition, true);
                }
                return false;
            }

            PsiElement element = instruction.getElement();
            if (element == null) {
                return false;
            }

            if (element instanceof PyTryPart || element instanceof PyExceptPart || element instanceof PyConditionalExpression) {
                return true;
            }

            if (element instanceof PyForStatement) {
                PyForPart forPart = ((PyForStatement)element).getForPart();
                return !PyEvaluator.evaluateAsBoolean(forPart.getSource(), false);
            }

            return false;
        }

        private static boolean suggestRename(PsiNameIdentifierOwner element, PsiElement originalElement) {
            // Target expressions in the same scope are treated as the same variable
            if ((element instanceof PyTargetExpression) && originalElement instanceof PyTargetExpression) {
                return false;
            }
            // Renaming an __init__ method results in renaming its class
            else if (PyUtil.isInitMethod(element)) {
                return false;
            }
            return true;
        }
    }
}
