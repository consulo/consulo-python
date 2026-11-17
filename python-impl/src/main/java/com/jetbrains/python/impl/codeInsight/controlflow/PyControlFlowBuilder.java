// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.google.common.collect.Lists;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightCounters;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightCounters.Counter;
import com.jetbrains.python.impl.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.impl.psi.types.PyTypeUtilKt;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyType;
import consulo.language.psi.util.QualifiedName;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.ControlFlowBuilder;
import consulo.language.controlFlow.Instruction;
import consulo.language.controlFlow.TransparentInstruction;
import consulo.language.controlFlow.base.ConditionalInstructionImpl;
import consulo.language.controlFlow.base.TransparentInstructionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PyControlFlowBuilder extends PyRecursiveElementVisitor {
    private final @Nullable LanguageLevel myLanguageLevel;

    protected final ControlFlowBuilder builder = new ControlFlowBuilder();

    private @Nullable TrueFalseNodes myTrueFalseNodes;

    // see com.jetbrains.python.PyPatternTypeTest.testMatchClassPatternShadowingCapture
    private final List<String> myPatternBindingNames = new ArrayList<>();

    private record TrueFalseNodes(Instruction trueNode, Instruction falseNode) {
    }

    public PyControlFlowBuilder(@Nullable LanguageLevel languageLevel) {
        myLanguageLevel = languageLevel;
    }

    public PyControlFlow buildControlFlow(ScopeOwner owner) {
        ControlFlow flow = builder.build(this, owner);
        Instruction[] instructions = flow.getInstructions();
        PyCodeInsightCounters.inc(Counter.CFG_BUILDS);
        PyCodeInsightCounters.add(Counter.CFG_INSTRUCTIONS, instructions.length);
        for (int i = 0; i < instructions.length; i++) {
            if (i != instructions[i].num()) {
                throw new IllegalStateException("Check failed.");
            }
        }
        return new PyControlFlow(instructions);
    }


    @Override
    public void visitPyFunction(PyFunction node) {
        // Create node and stop here
        builder.startNode(node);

        visitParameterListExpressions(node.getParameterList());
        visitDecorators(node.getDecoratorList());
        if (node.getTypeParameterList() == null) {
            PyAnnotation annotation = node.getAnnotation();
            if (annotation != null) {
                annotation.acceptChildren(this);
            }
        }

        ReadWriteInstruction instruction = ReadWriteInstruction.write(builder, node, node.getName());
        builder.addNodeAndCheckPending(instruction);
    }

    @Override
    public void visitPyDecoratorList(PyDecoratorList node) {
    }

    private void visitDecorators(@Nullable PyDecoratorList list) {
        if (list != null) {
            for (PyDecorator decorator : list.getDecorators()) {
                decorator.accept(this);
            }
        }
    }

    private void visitParameterListExpressions(PyParameterList parameterList) {
        PsiElement owner = parameterList.getParent();
        boolean visitAnnotations = owner instanceof PyFunction function && function.getTypeParameterList() == null;
        visitNamedParameterExpressions(parameterList.getParameters(), visitAnnotations);
    }

    private void visitNamedParameterExpressions(PyParameter[] parameters, boolean visitAnnotations) {
        for (PyParameter parameter : parameters) {
            if (parameter instanceof PyTupleParameter tupleParameter) {
                visitNamedParameterExpressions(tupleParameter.getContents(), visitAnnotations);
            }
            else if (parameter instanceof PyNamedParameter namedParameter) {
                PyExpression defaultValue = namedParameter.getDefaultValue();
                if (defaultValue != null) {
                    defaultValue.accept(this);
                }
                if (visitAnnotations) {
                    PyAnnotation annotation = namedParameter.getAnnotation();
                    if (annotation != null) {
                        annotation.acceptChildren(this);
                    }
                }
            }
        }
    }

    @Override
    public void visitPyClass(PyClass node) {
        // Create node and stop here
        builder.startNode(node);

        for (PyExpression element : node.getSuperClassExpressions()) {
            element.accept(this);
        }
        visitDecorators(node.getDecoratorList());
        ReadWriteInstruction instruction = ReadWriteInstruction.write(builder, node, node.getName());
        builder.addNodeAndCheckPending(instruction);
    }

    @Override
    public void visitPyStatement(PyStatement node) {
        builder.startNode(node);
        super.visitPyStatement(node);
    }

    @Override
    public void visitPyElement(PyElement node) {
        if (node instanceof PsiNamedElement namedElement && !(node instanceof PyKeywordArgument)) {
            builder.startNode(node);
            builder.addNode(ReadWriteInstruction.newInstruction(builder, node, namedElement.getName(), ReadWriteInstruction.ACCESS.WRITE));
        }
        super.visitPyElement(node);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
        super.visitPyCallExpression(node);

        CallInstruction callInstruction = new CallInstruction(builder, node);
        builder.addNodeAndCheckPending(callInstruction);

        if (node.isCalleeText(PyNames.ASSERT_IS_INSTANCE)) {
            addTypeAssertionNodes(node, true);
        }
    }

    @Override
    public void visitPySubscriptionExpression(PySubscriptionExpression node) {
        builder.startNode(node);
        node.getOperand().accept(this);
        PyExpression expression = node.getIndexExpression();
        if (expression != null) {
            expression.accept(this);
        }
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
        PyExpression qualifier = node.getQualifier();
        if (qualifier != null) {
            qualifier.accept(this);
            return;
        }
        if (PyImportStatementNavigator.getImportStatementByElement(node) != null) {
            return;
        }

        ReadWriteInstruction readWriteInstruction;
        if (PyAugAssignmentStatementNavigator.getStatementByTarget(node) != null) {
            readWriteInstruction = ReadWriteInstruction.readWrite(builder, node, getName(node), augAssignmentTypeCallback(node));
        }
        else {
            readWriteInstruction = ReadWriteInstruction.newInstruction(builder, node, getName(node), ReadWriteInstruction.ACCESS.READ);
        }
        builder.addNodeAndCheckPending(readWriteInstruction);
    }

    @Override
    public void visitPyBoolLiteralExpression(PyBoolLiteralExpression node) {
        ReadWriteInstruction readWriteInstruction = ReadWriteInstruction.newInstruction(
            builder, node, node.getText(),
            ReadWriteInstruction.ACCESS.READ
        );
        builder.addNodeAndCheckPending(readWriteInstruction);
    }

    @Override
    public void visitPyNoneLiteralExpression(PyNoneLiteralExpression node) {
        ReadWriteInstruction readWriteInstruction = ReadWriteInstruction.newInstruction(
            builder, node, node.getText(),
            ReadWriteInstruction.ACCESS.READ
        );
        builder.addNodeAndCheckPending(readWriteInstruction);
    }

    @Override
    public void visitPyTypeDeclarationStatement(PyTypeDeclarationStatement node) {
        builder.startNode(node);
        PyAnnotation annotation = node.getAnnotation();
        if (annotation != null) {
            annotation.accept(this);
        }
        node.getTarget().accept(this);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
        builder.startNode(node);
        PyExpression value = node.getAssignedValue();
        if (value != null) {
            value.accept(this);
        }
        PyAnnotation annotation = node.getAnnotation();
        if (annotation != null) {
            annotation.accept(this);
        }
        for (PyExpression expression : node.getRawTargets()) {
            expression.accept(this);
        }
    }

    @Override
    public void visitPyDelStatement(PyDelStatement node) {
        builder.startNode(node);
        for (PyExpression target : node.getTargets()) {
            if (target instanceof PyReferenceExpression referenceExpression) {
                PyExpression qualifier = referenceExpression.getQualifier();
                if (qualifier != null) {
                    qualifier.accept(this);
                }
                else {
                    builder.addNode(
                        ReadWriteInstruction.newInstruction(
                            builder,
                            target,
                            target.getName(),
                            ReadWriteInstruction.ACCESS.DELETE
                        )
                    );
                }
            }
            else {
                target.accept(this);
            }
        }
    }

    @Override
    public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
        builder.startNode(node);
        PyExpression value = node.getValue();
        if (value != null) {
            value.accept(this);
        }
        node.getTarget().accept(this);
    }

    @Override
    public void visitPyTargetExpression(PyTargetExpression node) {
        QualifiedName qName = node.asQualifiedName();
        if (qName != null) {
            ReadWriteInstruction instruction = ReadWriteInstruction.newInstruction(
                builder, node, qName.toString(),
                ReadWriteInstruction.ACCESS.WRITE
            );
            builder.addNodeAndCheckPending(instruction);
        }

        PyExpression qualifier = node.getQualifier();
        if (qualifier != null) {
            qualifier.accept(this);
        }
    }

    @Override
    public void visitPyNamedParameter(PyNamedParameter node) {
        PyAnnotation annotation = node.getAnnotation();
        if (annotation != null) {
            annotation.accept(this);
        }
        ReadWriteInstruction instruction = ReadWriteInstruction.write(builder, node, node.getName());
        builder.addNodeAndCheckPending(instruction);
    }

    @Override
    public void visitPyAnnotation(PyAnnotation node) {
        // Unless there is a type parameter list, return type and parameter annotations for functions are evaluated in their enclosing scope
        // and processed in visitPyFunction.
        // If there are type parameters, though, we need to put the corresponding instructions *inside* the function's scope to be able to
        // access them from annotations.
        PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, true, PyStatement.class);
        if (function == null || function.getTypeParameterList() != null) {
            super.visitPyAnnotation(node);
        }
    }

    @Override
    public void visitPyImportStatement(PyImportStatement node) {
        visitPyImportStatementBase(node);
    }

    @Override
    public void visitPyFromImportStatement(PyFromImportStatement node) {
        visitPyImportStatementBase(node);
        PyStarImportElement starImportElement = node.getStarImportElement();
        if (starImportElement != null) {
            starImportElement.accept(this);
        }
    }

    @Override
    public void visitPyStarImportElement(PyStarImportElement node) {
        builder.startNode(node);
    }

    private void visitPyImportStatementBase(PyImportStatementBase node) {
        builder.startNode(node);
        for (PyImportElement importElement : node.getImportElements()) {
            ReadWriteInstruction instruction = ReadWriteInstruction.write(builder, importElement, importElement.getVisibleName());
            builder.addNodeAndCheckPending(instruction);
        }
    }

    @Override
    public void visitPyConditionalExpression(PyConditionalExpression node) {
        builder.startNode(node);
        TransparentInstruction trueNode = addTransparentInstruction();
        TransparentInstruction falseNode = addTransparentInstruction();
        TransparentInstruction exitNode = addTransparentInstruction();
        PyExpression condition = node.getCondition();
        if (condition != null) {
            visitCondition(condition, trueNode, falseNode);
        }
        PyExpression truePart = node.getTruePart();
        PyExpression falsePart = node.getFalsePart();
        if (truePart != null) {
            builder.prevInstruction = trueNode;
            truePart.accept(this);
            builder.addEdge(builder.prevInstruction, exitNode);
        }
        if (falsePart != null) {
            builder.prevInstruction = falseNode;
            falsePart.accept(this);
            builder.addEdge(builder.prevInstruction, exitNode);
        }
        builder.prevInstruction = exitNode;
    }

    @Override
    public void visitPyMatchStatement(PyMatchStatement matchStatement) {
        builder.startNode(matchStatement);
        PyExpression subject = matchStatement.getSubject();
        if (subject != null) {
            subject.accept(this);
        }
        Instruction nextClause = builder.prevInstruction;
        boolean unreachable = false;
        for (PyCaseClause clause : matchStatement.getCaseClauses()) {
            builder.prevInstruction = nextClause;
            nextClause = addTransparentInstruction();

            myPatternBindingNames.clear();

            PyPattern pattern = clause.getPattern();
            if (pattern != null) {
                pattern.accept(this);
                addTypeAssertionNodes(clause, true, myPatternBindingNames);
            }

            PyExpression guard = clause.getGuardCondition();
            if (guard != null) {
                TransparentInstruction trueNode = addTransparentInstruction();
                visitCondition(guard, trueNode, nextClause);
                builder.prevInstruction = trueNode;
                addTypeAssertionNodes(guard, true);
            }

            if (unreachable) {
                builder.addNode(new PyUnreachableInstruction(builder, false));
            }
            if (pattern != null && pattern.isIrrefutable() && (guard == null || PyEvaluator.evaluateAsBooleanNoResolve(guard, false))) {
                unreachable = true;
            }
            builder.startNode(clause.getStatementList());
            clause.getStatementList().accept(this);
            builder.addPendingEdge(matchStatement, builder.prevInstruction);
            builder.updatePendingElementScope(clause.getStatementList(), matchStatement);
        }
        builder.prevInstruction = nextClause;
        builder.addNodeAndCheckPending(new TransparentInstructionImpl(builder, matchStatement, ""));
        if (!builder.prevInstruction.allPred().isEmpty()) {
            addTypeAssertionNodes(matchStatement, false);
        }
        builder.addPendingEdge(matchStatement, builder.prevInstruction);
        builder.prevInstruction = null;
    }

    @Override
    public void visitPyPattern(PyPattern node) {
        boolean isRefutable = !node.isIrrefutable();
        if (isRefutable) {
            builder.addNodeAndCheckPending(new RefutablePatternInstruction(builder, node, false));
        }
        else {
            builder.startNode(node);
        }
        builder.addPendingEdge(node.getParent(), builder.prevInstruction);

        node.acceptChildren(this);
        builder.updatePendingElementScope(node, node.getParent());

        if (isRefutable) {
            builder.addNode(new RefutablePatternInstruction(builder, node, true));
        }
    }

    @Override
    public void visitPyOrPattern(PyOrPattern node) {
        builder.addNodeAndCheckPending(new RefutablePatternInstruction(builder, node, false));

        TransparentInstruction onSuccess = new TransparentInstructionImpl(builder, node, "onSuccess");
        List<PyPattern> alternatives = node.getAlternatives();
        PyPattern lastAlternative = alternatives.isEmpty() ? null : alternatives.get(alternatives.size() - 1);

        for (PyPattern alternative : alternatives) {
            alternative.accept(this);
            if (alternative != lastAlternative) {
                // Allow next alternative to handle the fail edge of this alternative
                builder.updatePendingElementScope(node, alternative);
            }
            builder.addEdge(builder.prevInstruction, onSuccess);
            builder.prevInstruction = null;
        }
        builder.addNode(onSuccess);
        builder.addNode(new RefutablePatternInstruction(builder, node, true));
        builder.updatePendingElementScope(node, node.getParent());
    }

    @Override
    public void visitPyClassPattern(PyClassPattern node) {
        builder.addNodeAndCheckPending(new RefutablePatternInstruction(builder, node, false));

        node.getClassNameReference().accept(this);
        builder.addPendingEdge(node.getParent(), builder.prevInstruction);

        node.getArgumentList().acceptChildren(this);
        builder.updatePendingElementScope(node, node.getParent());

        builder.addNode(new RefutablePatternInstruction(builder, node, true));
    }

    @Override
    public void visitPyValuePattern(PyValuePattern node) {
        builder.addNodeAndCheckPending(new RefutablePatternInstruction(builder, node, false));

        node.getValue().accept(this);
        builder.addPendingEdge(node.getParent(), builder.prevInstruction);

        builder.addNode(new RefutablePatternInstruction(builder, node, true));
    }

    @Override
    public void visitPyAsPattern(PyAsPattern node) {
        // AsPattern can't fail by itself – it fails only if its child fails.
        // So no need to create an additional fail edge
        builder.startNode(node);
        node.acceptChildren(this);
        if (node.getTarget() != null) {
            myPatternBindingNames.add(node.getTarget().getName());
        }
        builder.updatePendingElementScope(node, node.getParent());
    }

    @Override
    public void visitPyCapturePattern(PyCapturePattern node) {
        node.acceptChildren(this);
        // Although capture pattern is irrefutable, I add fail edge
        // here to add some connection to the next case clause.
        // Perhaps this can be reworked and simplified later.
        builder.addPendingEdge(node.getParent(), builder.prevInstruction);
        myPatternBindingNames.add(node.getTarget().getName());
    }

    @Override
    public void visitPyGroupPattern(PyGroupPattern node) {
        // GroupPattern can't fail by itself – it fails only if its child fails.
        // So no need to create an additional fail edge
        // Also no need for a dedicated node for GroupPattern itself
        node.acceptChildren(this);
        builder.updatePendingElementScope(node, node.getParent());
    }

    @Override
    public void visitPyIfStatement(PyIfStatement node) {
        builder.startNode(node);

        List<@Nullable Instruction> exitInstructions = new ArrayList<>();
        PyExpression alwaysTrueCondition = null;
        List<PyIfPart> ifParts = new ArrayList<>();
        ifParts.add(node.getIfPart());
        ifParts.addAll(List.of(node.getElifParts()));
        for (PyIfPart ifPart : ifParts) {
            TransparentInstruction thenNode = addTransparentInstruction();
            TransparentInstruction elseNode = addTransparentInstruction();
            PyExpression condition = ifPart.getCondition();
            if (condition != null) {
                visitCondition(condition, thenNode, elseNode);
            }
            builder.prevInstruction = thenNode;

            Boolean conditionResult = PyEvaluator.evaluateAsBooleanNoResolve(condition, myLanguageLevel);
            boolean unreachable = alwaysTrueCondition != null || Boolean.FALSE.equals(conditionResult);
            if (unreachable) {
                boolean isUnreachableForTypeChecking;
                if (alwaysTrueCondition != null) {
                    isUnreachableForTypeChecking = isTypeCheckingCondition(alwaysTrueCondition);
                }
                else {
                    isUnreachableForTypeChecking = isTypeCheckingCondition(condition);
                }
                builder.addNode(new PyUnreachableInstruction(builder, isUnreachableForTypeChecking));
            }
            if (Boolean.TRUE.equals(conditionResult)) {
                alwaysTrueCondition = condition;
            }
            visitPyStatementPart(ifPart);

            if (!unreachable) {
                exitInstructions.add(builder.prevInstruction);
            }
            builder.prevInstruction = elseNode;
        }

        PyElsePart elsePart = node.getElsePart();
        if (elsePart != null) {
            if (alwaysTrueCondition != null) {
                builder.addNode(new PyUnreachableInstruction(builder, isTypeCheckingCondition(alwaysTrueCondition)));
            }
            visitPyStatementPart(elsePart);
        }

        if (alwaysTrueCondition == null) {
            exitInstructions.add(builder.prevInstruction);
        }
        builder.prevInstruction = addTransparentInstruction(node);

        for (Instruction exitInstruction : Lists.reverse(exitInstructions)) {
            builder.addEdge(exitInstruction, builder.prevInstruction);
        }
    }

    @Override
    public void visitPyPrefixExpression(PyPrefixExpression node) {
        TrueFalseNodes trueFalseNodes = myTrueFalseNodes;
        if (trueFalseNodes != null && node.getOperator() == PyTokenTypes.NOT_KEYWORD) {
            PyExpression operand = node.getOperand();
            if (operand != null) {
                visitCondition(operand, trueFalseNodes.falseNode(), trueFalseNodes.trueNode());
            }
        }
        else {
            super.visitPyPrefixExpression(node);
        }
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
        PyElementType operator = node.getOperator();
        if (operator == PyTokenTypes.AND_KEYWORD || operator == PyTokenTypes.OR_KEYWORD) {
            PyExpression left = node.getLeftExpression();
            PyExpression right = node.getRightExpression();
            if (left == null || right == null) {
                return;
            }

            builder.startNode(node);

            Instruction trueNode;
            Instruction falseNode;
            Instruction exitNode;
            TrueFalseNodes trueFalseNodes = myTrueFalseNodes;
            if (trueFalseNodes == null) {
                exitNode = addTransparentInstruction();
                falseNode = exitNode;
                trueNode = falseNode;
            }
            else {
                trueNode = trueFalseNodes.trueNode();
                falseNode = trueFalseNodes.falseNode();
                exitNode = null;
            }

            Instruction rightNode = addTransparentInstruction();
            if (operator == PyTokenTypes.AND_KEYWORD) {
                visitCondition(left, rightNode, falseNode);
            }
            else {
                visitCondition(left, trueNode, rightNode);
            }

            builder.prevInstruction = rightNode;
            visitCondition(right, trueNode, falseNode);

            if (exitNode != null) {
                builder.prevInstruction = exitNode;
            }
        }
        else {
            super.visitPyBinaryExpression(node);
        }
    }

    @Override
    public void visitPyWhileStatement(PyWhileStatement node) {
        Instruction entryNode = builder.startNode(node);

        TransparentInstruction thenNode = addTransparentInstruction();
        TransparentInstruction elseNode = addTransparentInstruction();

        PyConditionalStatementPart whilePart = node.getWhilePart();
        PyExpression condition = whilePart.getCondition();
        if (condition != null) {
            visitCondition(condition, thenNode, elseNode);
        }

        Boolean conditionResult = PyEvaluator.evaluateAsBooleanNoResolve(condition);

        builder.prevInstruction = Boolean.FALSE.equals(conditionResult) ? null : thenNode;
        visitPyStatementPart(whilePart);

        builder.checkPending(entryNode);
        builder.addEdge(builder.prevInstruction, entryNode);

        builder.prevInstruction = Boolean.TRUE.equals(conditionResult) ? null : elseNode;
        PyElsePart elsePart = node.getElsePart();
        if (elsePart != null) {
            visitPyStatementPart(elsePart);
        }

        collectInternalPendingEdges(node);
    }

    @Override
    public void visitPyForStatement(PyForStatement node) {
        builder.startNode(node);
        PyForPart forPart = node.getForPart();
        PyExpression source = forPart.getSource();
        if (source != null) {
            source.accept(this);
        }
        Instruction head = builder.prevInstruction;
        PyElsePart elsePart = node.getElsePart();
        if (elsePart == null && !loopHasAtLeastOneIteration(node)) {
            builder.addPendingEdge(node, builder.prevInstruction);
        }
        PyStatementList list = forPart.getStatementList();
        Instruction body;
        PyExpression target = forPart.getTarget();
        if (target != null) {
            body = builder.startNode(target);
            target.accept(this);
        }
        else {
            body = builder.startNode(list);
        }
        list.accept(this);
        if (builder.prevInstruction != null) {
            builder.addEdge(builder.prevInstruction, body); //loop
            builder.addPendingEdge(list, builder.prevInstruction); // exit
        }
        builder.processPending((pendingScope, instruction) -> {
            if (pendingScope != null && PsiTreeUtil.isAncestor(list, pendingScope, false)) {
                builder.addEdge(instruction, body); //loop
                builder.addPendingEdge(list, instruction); // exit
            }
            else {
                builder.addPendingEdge(pendingScope, instruction);
            }
        });
        builder.prevInstruction = head;
        if (elsePart != null) {
            elsePart.accept(this);
            builder.addPendingEdge(node, builder.prevInstruction);
        }


        builder.flowAbrupted();
        collectInternalPendingEdges(node);
    }

    @Override
    public void visitPyBreakStatement(PyBreakStatement node) {
        builder.startNode(node);
        PyLoopStatement loop = node.getLoopStatement();
        if (loop != null) {
            builder.addPendingEdge(loop, builder.prevInstruction);
        }
        else {
            builder.addPendingEdge(null, builder.prevInstruction);
        }
        builder.flowAbrupted();
    }

    @Override
    public void visitPyContinueStatement(PyContinueStatement node) {
        builder.startNode(node);
        PyLoopStatement loop = node.getLoopStatement();
        if (loop != null) {
            Instruction instruction = builder.findInstructionByElement(loop);
            if (instruction != null) {
                builder.addEdge(builder.prevInstruction, instruction);
            }
            else {
                builder.addPendingEdge(null, null);
            }

            // There is no edge between loop statement and next after loop instruction
            // when loop has at least one iteration
            // so `continue` is marked as one more last instruction in the loop
            // see visitPyWhileStatement
            // see visitPyForStatement
            if (loopHasAtLeastOneIteration(loop)) {
                builder.addPendingEdge(loop, builder.prevInstruction);
            }
        }
        builder.flowAbrupted();
    }

    @Override
    public void visitPyYieldExpression(PyYieldExpression node) {
        builder.startNode(node);
        PyExpression expression = node.getExpression();
        if (expression != null) {
            expression.accept(this);
        }
    }

    @Override
    public void visitPyRaiseStatement(PyRaiseStatement node) {
        builder.startNode(node);
        PyExpression[] expressions = node.getExpressions();
        for (PyExpression expression : expressions) {
            expression.accept(this);
        }
        builder.addNode(new PyRaiseInstruction(builder, node));
        builder.addPendingEdge(null, builder.prevInstruction);
        builder.flowAbrupted();
    }

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
        builder.startNode(node);
        PyExpression expression = node.getExpression();
        if (expression != null) {
            expression.accept(this);
        }
        builder.addPendingEdge(null, builder.prevInstruction);
        builder.flowAbrupted();
    }

    @Override
    public void visitPyTryExceptStatement(PyTryExceptStatement node) {
        builder.startNode(node);

        // Process try part
        PyTryPart tryPart = node.getTryPart();
        builder.startNode(tryPart);
        tryPart.accept(this);

        // Goto else part after execution, or exit
        PyElsePart elsePart = node.getElsePart();
        if (elsePart != null) {
            builder.startNode(elsePart);
            elsePart.accept(this);
        }
        builder.addPendingEdge(node, builder.prevInstruction);

        // Process except parts
        List<Instruction> exceptInstructions = new ArrayList<>();
        List<Pair<PsiElement, Instruction>> pendingBackup = new ArrayList<>();
        for (PyExceptPart exceptPart : node.getExceptParts()) {
            pendingBackup.addAll(builder.pending);
            builder.pending = new ArrayList<>();
            builder.flowAbrupted();
            Instruction exceptInstruction = builder.startNode(exceptPart);
            exceptPart.accept(this);
            builder.addPendingEdge(node, builder.prevInstruction);
            exceptInstructions.add(exceptInstruction);
        }
        for (Pair<PsiElement, Instruction> pair : pendingBackup) {
            builder.addPendingEdge(pair.first, pair.second);
        }

        List<Pair<PsiElement, Instruction>> pendingNormalExits = new ArrayList<>();
        PyFinallyPart finallyPart = node.getFinallyPart();
        Instruction finallyFailInstruction;

        // Store pending normal exit instructions from try-except-else parts
        if (finallyPart != null) {
            builder.processPending((pendingScope, instruction) -> {
                PsiElement pendingElement = instruction.getElement();
                if (pendingElement != null) {
                    boolean isPending = PsiTreeUtil.isAncestor(node, pendingElement, false) &&
                        !PsiTreeUtil.isAncestor(finallyPart, pendingElement, false);
                    if (isPending && pendingScope != null) {
                        pendingNormalExits.add(Pair.createNonNull(pendingScope, instruction));
                    }
                    else {
                        builder.addPendingEdge(pendingScope, instruction);
                    }
                }
            });
        }

        // Finally-fail part handling
        if (finallyPart != null) {
            builder.flowAbrupted();
            finallyFailInstruction = builder.startNode(finallyPart);
            finallyPart.accept(this);
            builder.addNodeAndCheckPending(new PyFinallyFailExitInstruction(builder, finallyFailInstruction));
            builder.addPendingEdge(null, builder.prevInstruction);
            builder.flowAbrupted();
        }
        else {
            finallyFailInstruction = null;
        }

        // Create exception edges
        for (Instruction instruction : builder.instructions) {
            PsiElement e = instruction.getElement();
            if (e == null || !canRaiseExceptions(instruction)) {
                continue;
            }
            // All instructions inside the try part have edges to except and finally parts
            if (PsiTreeUtil.getParentOfType(e, PyTryPart.class, false) == tryPart) {
                for (Instruction inst : exceptInstructions) {
                    builder.addEdge(instruction, inst);
                }
                if (finallyPart != null) {
                    builder.addEdge(instruction, finallyFailInstruction);
                }
            }
            if (finallyPart != null) {
                // All instructions inside except parts have edges to the finally part
                for (PyExceptPart exceptPart : node.getExceptParts()) {
                    if (PsiTreeUtil.isAncestor(exceptPart, e, false)) {
                        builder.addEdge(instruction, finallyFailInstruction);
                    }
                }
                // All instructions inside the else part have edges to the finally part
                if (PsiTreeUtil.isAncestor(elsePart, e, false)) {
                    builder.addEdge(instruction, finallyFailInstruction);
                }
            }
        }

        if (finallyPart != null) {
            builder.processPending((pendingScope, instruction) -> {
                PsiElement e = instruction.getElement();
                if (e != null) {
                    // Change the scope of pending edges from finally-fail part to point to the last instruction
                    if (PsiTreeUtil.isAncestor(finallyPart, e, false)) {
                        builder.addPendingEdge(null, instruction);
                    }
                    else if (pendingScope == null && PsiTreeUtil.isAncestor(node, e, false)) {
                        builder.addEdge(instruction, finallyFailInstruction);
                    }
                    else {
                        builder.addPendingEdge(pendingScope, instruction);
                    }
                }
            });

            // Duplicate CFG for finally (-fail and -success) only if there are some successful exits from the
            // try part. Otherwise, a single CFG for finally provides the correct control flow
            Instruction finallyInstruction;
            if (!pendingNormalExits.isEmpty()) {
                // Finally-success part handling
                pendingBackup = new ArrayList<>(builder.pending);
                builder.pending = new ArrayList<>();
                builder.flowAbrupted();
                Instruction finallySuccessInstruction = builder.startNode(finallyPart);
                finallyPart.accept(this);
                for (Pair<PsiElement, Instruction> pair : pendingBackup) {
                    builder.addPendingEdge(pair.first, pair.second);
                }
                finallyInstruction = finallySuccessInstruction;
            }
            else {
                finallyInstruction = finallyFailInstruction;
            }

            // Connect normal exits from try and else parts to the finally part
            for (Pair<PsiElement, Instruction> pendingScopeAndInstruction : pendingNormalExits) {
                PsiElement pendingScope = pendingScopeAndInstruction.first;
                Instruction instruction = pendingScopeAndInstruction.second;

                builder.addEdge(instruction, finallyInstruction);

                // When instruction continues outside try-except statement scope
                // the last instruction in finally-block is marked as pointing to that continuation
                if (PsiTreeUtil.isAncestor(pendingScope, node, true)) {
                    builder.addPendingEdge(pendingScope, builder.prevInstruction);
                }
            }
        }

        collectInternalPendingEdges(node);
    }

    @Override
    public void visitPyComprehensionElement(PyComprehensionElement node) {
        PyExpression prevCondition = null;
        builder.startNode(node);
        List<Instruction> iterators = new ArrayList<>();

        for (PyComprehensionComponent component : node.getComponents()) {
            if (component instanceof PyComprehensionForComponent forComponent) {
                PyExpression iteratedList = forComponent.getIteratedList();
                PyExpression iteratorVariable = forComponent.getIteratorVariable();
                if (prevCondition != null) {
                    builder.startConditionalNode(iteratedList, prevCondition, true);
                    prevCondition = null;
                }
                else {
                    builder.startNode(iteratedList);
                }
                iteratedList.accept(this);

                // for-loop continue and exit
                for (Instruction i : iterators) {
                    builder.addEdge(builder.prevInstruction, i);
                }
                builder.addPendingEdge(node, builder.prevInstruction);

                Instruction iterator = builder.startNode(iteratorVariable);
                iteratorVariable.accept(this);

                // Inner "for" and "if" constructs will be linked to all outer iterators
                iterators.add(iterator);
            }
            else if (component instanceof PyComprehensionIfComponent ifComponent) {
                PyExpression condition = ifComponent.getTest();
                if (condition == null) {
                    continue;
                }
                if (prevCondition != null) {
                    builder.startConditionalNode(condition, prevCondition, true);
                }
                else {
                    builder.startNode(condition);
                }
                condition.accept(this);
                addTypeAssertionNodes(condition, true);

                // Condition is true for nested "for" and "if" constructs, next startNode() should create a conditional node
                prevCondition = condition;

                // for-loop continue and exit
                for (Instruction i : iterators) {
                    builder.addEdge(builder.prevInstruction, i);
                }
                builder.addPendingEdge(node, builder.prevInstruction);
            }
        }

        PyExpression result = node.getResultExpression();
        if (result != null) {
            if (prevCondition != null) {
                builder.startConditionalNode(result, prevCondition, true);
            }
            else {
                builder.startNode(result);
            }
            result.accept(this);

            // for-loop continue
            for (Instruction i : iterators) {
                builder.addEdge(builder.prevInstruction, i);
            }
        }

        collectInternalPendingEdges(node);
    }

    @Override
    public void visitPyAssertStatement(PyAssertStatement node) {
        builder.startNode(node);
        PyExpression[] args = node.getArguments();
        for (PyExpression arg : args) {
            arg.accept(this);
        }
        // assert False
        if (args.length > 0) {
            if (!PyEvaluator.evaluateAsBooleanNoResolve(args[0], true)) {
                builder.addNode(new PyRaiseInstruction(builder, node));
                builder.addPendingEdge(null, builder.prevInstruction);
                builder.flowAbrupted();
                return;
            }

            TransparentInstruction trueNode = addTransparentInstruction(node);
            TransparentInstruction falseNode = addTransparentInstruction(node);
            visitCondition(args[0], trueNode, falseNode);

            PyRaiseInstruction raiseInstruction = new PyRaiseInstruction(builder, node);
            builder.instructions.add(raiseInstruction);
            builder.addEdge(falseNode, raiseInstruction);

            builder.addPendingEdge(null, raiseInstruction);
            builder.prevInstruction = trueNode;
        }
    }

    @Override
    public void visitPyLambdaExpression(PyLambdaExpression node) {
        builder.startNode(node);
        visitParameterListExpressions(node.getParameterList());
    }

    @Override
    public void visitPyWithStatement(PyWithStatement node) {
        builder.startNode(node);

        List<Instruction> exits = new ArrayList<>();
        for (PyWithItem item : node.getWithItems()) {
            int itemStart = builder.instructions.size();
            item.accept(this);
            int itemEnd = builder.instructions.size();
            for (int i = itemStart; i < itemEnd; i++) {
                Instruction instruction = builder.instructions.get(i);
                PsiElement e = instruction.getElement();
                if (e == null || !canRaiseExceptions(instruction) || !PsiTreeUtil.isAncestor(node, e, false)) {
                    continue;
                }
                for (Instruction exit : exits) {
                    builder.addEdge(builder.instructions.get(i), exit);
                }
            }

            PyWithContextExitInstruction nextExit = new PyWithContextExitInstruction(builder, item);
            exits.add(nextExit);
            builder.instructions.add(nextExit);
            // ControlFlowUtil.iterate assumes nodes are added to CFG in order they are created
        }

        TransparentInstruction toAllExits = addTransparentInstruction();
        TransparentInstruction fromAllExits = addTransparentInstruction();
        for (Instruction exit : exits) {
            builder.addEdge(toAllExits, exit);
            builder.addEdge(exit, fromAllExits);
        }

        int stmtStart = builder.instructions.size();
        node.getStatementList().accept(this);
        int stmtEnd = builder.instructions.size();

        for (int j = stmtStart; j < stmtEnd; j++) {
            Instruction instruction = builder.instructions.get(j);
            PsiElement e = instruction.getElement();
            if (e == null || !canRaiseExceptions(instruction) || !PsiTreeUtil.isAncestor(node, e, false)) {
                continue;
            }
            builder.addEdge(instruction, toAllExits);
        }
        builder.addEdge(builder.prevInstruction, toAllExits);

        if (!exits.isEmpty()) {
            builder.addPendingEdge(node, fromAllExits);
        }
    }

    @Override
    public void visitPyAssignmentExpression(PyAssignmentExpression node) {
        PyExpression assignedValue = node.getAssignedValue();
        if (assignedValue != null) {
            assignedValue.accept(this);
        }

        PyTargetExpression target = node.getTarget();
        if (target != null) {
            target.accept(this);
        }
    }

    @Override
    public void visitPyTypeAliasStatement(PyTypeAliasStatement node) {
        builder.startNode(node);

        ReadWriteInstruction instruction = ReadWriteInstruction.write(builder, node, node.getName());
        builder.addNodeAndCheckPending(instruction);
    }

    @Override
    public void visitPyTypeParameterList(PyTypeParameterList node) {
    }

    private void visitCondition(PyExpression expression, Instruction trueNode, Instruction falseNode) {
        TrueFalseNodes prevTrueFalseNodes = myTrueFalseNodes;
        myTrueFalseNodes = new TrueFalseNodes(trueNode, falseNode);
        expression.accept(this);
        myTrueFalseNodes = prevTrueFalseNodes;

        PyExpression condition = PyPsiUtils.flattenParens(expression);
        if (condition != null && !isLogicalExpression(condition)) {
            addConditionalNode(expression, false, falseNode);
            addConditionalNode(expression, true, trueNode);
        }
    }

    private void addConditionalNode(PyExpression condition, boolean result, Instruction target) {
        Instruction prevInstruction = builder.prevInstruction;
        builder.addNode(new ConditionalInstructionImpl(builder, null, condition, result));
        addTypeAssertionNodes(condition, result);
        builder.addEdge(builder.prevInstruction, target);
        builder.prevInstruction = prevInstruction;
    }

    private void visitPyStatementPart(PyStatementPart statementPart) {
        PyStatementList statementList = statementPart.getStatementList();
        builder.startNode(statementList);
        statementList.accept(this);
    }

    private void addTypeAssertionNodes(PyElement condition, boolean positive) {
        addTypeAssertionNodes(condition, positive, null);
    }

    private void addTypeAssertionNodes(PyElement condition, boolean positive, @Nullable List<String> ignoredNames) {
        PyTypeAssertionEvaluator evaluator = new PyTypeAssertionEvaluator(positive);
        condition.accept(evaluator);
        for (PyTypeAssertionEvaluator.Assertion def : evaluator.getDefinitions()) {
            PyQualifiedExpression e = def.element;
            String name = null;
            if (e != null) {
                name = getName(e);
            }
            if (name != null && ignoredNames != null && ignoredNames.contains(name)) {
                continue;
            }
            builder.addNode(ReadWriteInstruction.assertType(builder, e, name, def.getTypeEvalFunction()));
        }
    }

    private TransparentInstruction addTransparentInstruction() {
        return addTransparentInstruction(null);
    }

    private TransparentInstruction addTransparentInstruction(@Nullable PsiElement element) {
        TransparentInstruction instruction = new TransparentInstructionImpl(builder, element, "");
        builder.instructions.add(instruction);
        return instruction;
    }

    /**
     * Can be used to collect all pending edges
     * that we used to build CFG for {@code node},
     * but are not relevant to other elements.
     * Is almost equivalent to this:
     *
     * <pre>{@code visitPy...(node); myBuilder.startNode(node.nextSibling); // collectInternalPendingEdges does this, without needing nextSibling }</pre>
     */
    private void collectInternalPendingEdges(PyElement node) {
        builder.addNode(new TransparentInstructionImpl(builder, node, "")); // exit
        builder.processPending((pendingScope, instruction) -> {
            if (pendingScope != null && PsiTreeUtil.isAncestor(node, pendingScope, false)) {
                builder.addEdge(instruction, builder.prevInstruction); // to exit
            }
            else {
                builder.addPendingEdge(pendingScope, instruction);
            }
        });
    }

    private static boolean isTypeCheckingCondition(@Nullable PyExpression expression) {
        return isTypeCheckingCheck(expression) || isVersionCheck(expression);
    }

    private static boolean isTypeCheckingCheck(@Nullable PyExpression expression) {
        PyExpression flattened = PyPsiUtils.flattenParens(expression);
        if (flattened instanceof PyPrefixExpression prefixExpression && prefixExpression.getOperator() == PyTokenTypes.NOT_KEYWORD) {
            return isTypeCheckingCondition(prefixExpression.getOperand());
        }
        // A compound condition such as `TYPE_CHECKING or not USE_EXTENSIONS` is type-checking related if any
        // operand is, since the branch taken under a type checker then differs from the one taken at runtime.
        if (flattened instanceof PyBinaryExpression binaryExpression &&
            (binaryExpression.getOperator() == PyTokenTypes.AND_KEYWORD || binaryExpression.getOperator() == PyTokenTypes.OR_KEYWORD)) {
            return isTypeCheckingCondition(binaryExpression.getLeftExpression()) ||
                isTypeCheckingCondition(binaryExpression.getRightExpression());
        }
        return PyEvaluator.isTypeCheckingExpression(flattened);
    }

    private static boolean isVersionCheck(@Nullable PyExpression expression) {
        PyExpression flattened = PyPsiUtils.flattenParens(expression);
        if (flattened instanceof PyPrefixExpression prefixExpression && prefixExpression.getOperator() == PyTokenTypes.NOT_KEYWORD) {
            return isVersionCheck(prefixExpression.getOperand());
        }
        if (flattened instanceof PyBinaryExpression binaryExpression) {
            PyElementType op = binaryExpression.getOperator();
            if (PyTokenTypes.AND_KEYWORD == op || PyTokenTypes.OR_KEYWORD == op) {
                return isVersionCheck(binaryExpression.getLeftExpression()) &&
                    isVersionCheck(binaryExpression.getRightExpression());
            }
            if (PyTokenTypes.RELATIONAL_OPERATIONS.contains(op)) {
                if (PyEvaluator.isSysVersionInfoExpression(binaryExpression.getLeftExpression()) &&
                    PyPsiUtils.flattenParens(binaryExpression.getRightExpression()) instanceof PyTupleExpression) {
                    return true;
                }
                if (PyPsiUtils.flattenParens(binaryExpression.getLeftExpression()) instanceof PyTupleExpression &&
                    PyEvaluator.isSysVersionInfoExpression(binaryExpression.getRightExpression())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean loopHasAtLeastOneIteration(PyLoopStatement loopStatement) {
        PyExpression expression;
        if (loopStatement instanceof PyForStatement forStatement) {
            expression = forStatement.getForPart().getSource();
        }
        else if (loopStatement instanceof PyWhileStatement whileStatement) {
            expression = whileStatement.getWhilePart().getCondition();
        }
        else {
            expression = null;
        }

        return PyEvaluator.evaluateAsBooleanNoResolve(expression, false);
    }

    private static boolean isLogicalExpression(PyExpression expression) {
        if (expression instanceof PyBinaryExpression binaryExpression) {
            PyElementType operator = binaryExpression.getOperator();
            return operator == PyTokenTypes.AND_KEYWORD || operator == PyTokenTypes.OR_KEYWORD;
        }
        if (expression instanceof PyPrefixExpression prefixExpression) {
            return prefixExpression.getOperator() == PyTokenTypes.NOT_KEYWORD;
        }
        return false;
    }

    private static boolean canRaiseExceptions(Instruction instruction) {
        if (instruction instanceof ReadWriteInstruction) {
            return true;
        }
        PsiElement element = instruction.getElement();
        return !(element instanceof PyReturnStatement returnStatement && returnStatement.getExpression() == null ||
            element instanceof PyContinueStatement ||
            element instanceof PyBreakStatement ||
            element instanceof PyPassStatement ||
            element instanceof PyStatementList);
    }

    private static InstructionTypeCallback augAssignmentTypeCallback(PyReferenceExpression target) {
        PyAugAssignmentStatement statement = PsiTreeUtil.getParentOfType(target, PyAugAssignmentStatement.class);
        return context -> {
            PyType assignmentType = statement != null ? context.getType(statement) : null;
            return Ref.create(!PyTypeUtilKt.isUnknown(assignmentType) ? assignmentType : context.getType(target));
        };
    }

    private static @Nullable String getName(PyQualifiedExpression expr) {
        QualifiedName qname = expr.asQualifiedName();
        return qname != null ? qname.toString() : expr.getName();
    }
}
