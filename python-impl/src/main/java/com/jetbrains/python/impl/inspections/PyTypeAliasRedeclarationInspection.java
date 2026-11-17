// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ref.SimpleReference;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Annotates type alias re-declarations
 */
@ExtensionImpl
public class PyTypeAliasRedeclarationInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameTypeAliasRedeclaration();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonTypeHinting();
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
        @RequiredReadAction
        public void visitPyTypeAliasStatement(PyTypeAliasStatement node) {
            reportRedeclaration(node);
        }

        @RequiredReadAction
        void reportRedeclaration(PsiNameIdentifierOwner element) {
            String name = element.getName();
            SimpleReference<PsiElement> writeElement = SimpleReference.create();
            if (name != null) {
                ScopeOwner owner = ScopeUtil.getScopeOwner(element);
                if (owner != null) {
                    Instruction[] instructions = ControlFlowCache.getControlFlow(owner).getInstructions();
                    int startInstruction = ControlFlowUtil.findInstructionNumberByElement(instructions, element);
                    if (startInstruction >= 0) {
                        ControlFlowUtil.iteratePrev(startInstruction, instructions, instruction -> {
                            if (instruction instanceof ReadWriteInstruction rwInstruction && instruction.num() != startInstruction) {
                                if (name.equals(rwInstruction.getName())) {
                                    PsiElement originalElement = rwInstruction.getElement();
                                    if (originalElement != null) {
                                        if (rwInstruction.getAccess().isWriteAccess() && originalElement != element) {
                                            writeElement.set(originalElement);
                                            return ControlFlowUtil.Operation.BREAK;
                                        }
                                    }
                                }
                            }
                            return ControlFlowUtil.Operation.NEXT;
                        });
                    }
                }
            }

            if (writeElement.get() == null) {
                return;
            }
            List<LocalQuickFix> quickFixes = new ArrayList<>();
            LocalQuickFix quickFix = PythonUiService.getInstance().createPyRenameElementQuickFix(element);
            if (quickFix != null) {
                quickFixes.add(quickFix);
            }
            PsiElement identifier = element.getNameIdentifier();
            registerProblem(identifier != null ? identifier : element,
                            PyInspectionMessages.problemMessage(p -> PyLocalize.inspRedeclaredTypeAlias(p[0]), String.valueOf(name)),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }
    }
}
