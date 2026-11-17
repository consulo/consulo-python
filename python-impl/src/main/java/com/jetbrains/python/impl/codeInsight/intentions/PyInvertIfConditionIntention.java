// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.PyControlFlow;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBreakStatement;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyContinueStatement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStatementListContainer;
import com.jetbrains.python.psi.PyStatementPart;
import com.jetbrains.python.psi.PyWhilePart;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.ast.TokenType;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.inspection.SuppressionUtil;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.impl.psi.CodeEditUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.jetbrains.python.impl.codeInsight.ConditionUtil.getInvertedConditionExpression;
import static com.jetbrains.python.impl.codeInsight.ConditionUtil.isValidConditionExpression;

/**
 * Inverts 'if' condition branches
 *
 * @author Vasya Aksyonov
 */
// TODO consulo: ModCommand API (PsiUpdateModCommandAction, ModPsiUpdater) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyInvertIfConditionIntention", categories = "Python", fileExtensions = "py")
public class PyInvertIfConditionIntention extends PsiElementBaseIntentionAction {
    private static final String PYLINT_COMMENT_PREFIX = "# pylint:";

    private static final TokenSet insignificantTokenSet = TokenSet.create(TokenType.WHITE_SPACE, PyTokenTypes.END_OF_LINE_COMMENT);

    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnInvertIfCondition();
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement element) {
        if (!(element.getContainingFile() instanceof PyFile)) {
            return false;
        }

        PyConditionalExpression conditionalExpression = PsiTreeUtil.getParentOfType(element, PyConditionalExpression.class, false);

        if (conditionalExpression != null &&
            conditionalExpression.getCondition() != null &&
            conditionalExpression.getFalsePart() != null &&
            isValidConditionExpression(conditionalExpression.getCondition())) {
            return true;
        }

        PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PyIfStatement.class, false);

        if (ifStatement != null && ifStatement.getElifParts().length == 0 && isAvailableForIfStatement(element, ifStatement)) {
            PyExpression condition = ifStatement.getIfPart().getCondition();
            return condition == null || isValidConditionExpression(condition);
        }
        return false;
    }


    private static boolean isAvailableForIfStatement(PsiElement element, PyIfStatement statement) {
        // Checking correct if part
        PyExpression condition = statement.getIfPart().getCondition();
        boolean containsConditionErrors = false;
        if (condition != null) {
            boolean afterCondition = false;
            for (PsiElement child : statement.getIfPart().getChildren()) {
                if (!afterCondition) {
                    if (child == condition) {
                        afterCondition = true;
                    }
                    continue;
                }
                if (child == statement.getIfPart().getStatementList()) {
                    break;
                }
                if (child instanceof PsiErrorElement) {
                    containsConditionErrors = true;
                    break;
                }
            }
        }
        if (containsConditionErrors) {
            return false;
        }

        // Checking current element nesting
        List<PsiElement> parents = new ArrayList<>();
        PsiElement parent = element.getParent();
        while (parent != null && parent != statement) {
            parents.add(parent);
            parent = parent.getParent();
        }

        if (parents.contains(statement.getIfPart().getStatementList())) {
            return false;
        }

        PyStatementPart elsePart = statement.getElsePart();
        return elsePart == null || !parents.contains(elsePart.getStatementList());
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
        PsiFile file = element.getContainingFile();
        PyConditionalExpression conditionalExpression = PsiTreeUtil.getParentOfType(element, PyConditionalExpression.class, false);
        if (conditionalExpression != null) {
            invertConditional(project, file, conditionalExpression);
            return;
        }

        PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PyIfStatement.class, false);
        if (ifStatement != null) {
            PyStatementPart elsePart = ifStatement.getElsePart();
            if (elsePart != null) {
                invertIfStatementComplete(project, file, ifStatement);
                return;
            }

            PyStatementListContainer terminableStatement = findTerminableParent(ifStatement);
            if (terminableStatement == null) {
                invertIfStatementIncomplete(project, editor, file, ifStatement);
                return;
            }

            boolean ifIsTerminated = isTerminated(ifStatement.getIfPart().getStatementList());
            if (!ifIsTerminated && getLastSignificantChild(ifStatement.getParent()) != ifStatement) {
                invertIfStatementIncomplete(project, editor, file, ifStatement);
                return;
            }

            if (ifIsTerminated) {
                PyStatementListContainer container = getParentStatementListContainer(ifStatement);
                if (!isTerminableStatement(container) && !isTerminated(container.getStatementList())) {
                    invertIfStatementIncomplete(project, editor, file, ifStatement);
                    return;
                }
            }

            invertIfStatementFollowup(project, file, ifStatement, terminableStatement);
            return;
        }

        throw new IncorrectOperationException("Is not a condition");
    }

    private static void invertConditional(Project project, PsiFile file, PyConditionalExpression expression) {
        PyExpression falsePart = expression.getFalsePart();
        if (falsePart != null) {
            PyExpression condition = expression.getCondition();
            if (condition != null) {
                condition.replace(getInvertedConditionExpression(project, file, condition));
            }
            PsiElement originalFalsePart = falsePart.copy();
            falsePart.replace(expression.getTruePart());
            expression.getTruePart().replace(originalFalsePart);
        }
    }

    private static void invertIfStatementComplete(Project project, PsiFile file, PyIfStatement statement) {
        PyExpression condition = statement.getIfPart().getCondition();
        if (condition != null) {
            condition.replace(getInvertedConditionExpression(project, file, condition));
        }

        PyStatementList ifStatements = statement.getIfPart().getStatementList();
        PyStatementList elseStatements = statement.getElsePart().getStatementList();

        PsiElement ifLastChild = ifStatements.getLastChild();
        PsiElement elseLastChild = elseStatements.getLastChild();

        elseStatements.addRange(ifStatements.getFirstChild(), ifLastChild);
        ifStatements.addRange(elseStatements.getFirstChild(), elseLastChild);

        ifStatements.deleteChildRange(ifStatements.getFirstChild(), ifLastChild);
        elseStatements.deleteChildRange(elseStatements.getFirstChild(), elseLastChild);

        switchAttachedComments(statement, statement.getElsePart());
        switchInlineComments(statement);
    }

    private static void invertIfStatementIncomplete(Project project, Editor editor, PsiFile file, PyIfStatement statement) {
        LanguageLevel level = LanguageLevel.forElement(file);
        PyElementGenerator generator = PyElementGenerator.getInstance(project);

        // Switching statements
        PyIfStatement completeStatement = generator.createFromText(level, PyIfStatement.class, "if a:\n\tpass\nelse:\n\tpass");

        PyExpression condition = statement.getIfPart().getCondition();
        if (condition != null) {
            PyExpression invertedCondition = getInvertedConditionExpression(project, file, condition);
            completeStatement.getIfPart().getCondition().replace(invertedCondition);
        }
        else {
            completeStatement.getIfPart().getCondition().delete();
        }

        completeStatement.getElsePart().getStatementList().replace(statement.getIfPart().getStatementList());

        // Moving inline comment
        PsiComment ifComment = getChildComment(statement.getIfPart());
        if (ifComment != null) {
            appendInlineComment(completeStatement.getElsePart(), ifComment);
        }

        PyIfStatement newStatement = (PyIfStatement) statement.replace(completeStatement);

        switchAttachedComments(newStatement, newStatement.getElsePart());

        // Highlighting placeholder
        TextRange passStatementRange = newStatement.getIfPart().getStatementList().getStatements()[0].getTextRange();
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
        editor.getSelectionModel().setSelection(passStatementRange.getStartOffset(), passStatementRange.getEndOffset());
        editor.getCaretModel().moveToOffset(passStatementRange.getEndOffset());
    }

    private void invertIfStatementFollowup(Project project,
                                           PsiFile file,
                                           PyIfStatement statement,
                                           PyStatementListContainer terminableStatement) {
        PyExpression condition = statement.getIfPart().getCondition();
        if (condition != null) {
            condition.replace(getInvertedConditionExpression(project, file, condition));
        }

        PyStatementList ifStatements = statement.getIfPart().getStatementList();

        // Switching statements
        PsiElement parent = statement.getParent();
        PsiElement ifFirstChild = ifStatements.getFirstChild();
        PsiElement ifLastChild = ifStatements.getLastChild();

        PsiElement followupFirstChild = getNextMovableSibling(statement);
        PsiElement followupLastChild = parent.getLastChild();

        parent.addRange(ifFirstChild, ifLastChild);
        if (followupFirstChild != null) {
            ifStatements.addRange(followupFirstChild, followupLastChild);
        }

        switchAttachedComments(statement, getNextMovableSibling(statement));

        ifStatements.deleteChildRange(ifFirstChild, ifLastChild);
        if (followupFirstChild != null) {
            parent.deleteChildRange(followupFirstChild, followupLastChild);
        }

        // Fixing terminations
        PsiElement followupTerminator = isTerminationStatement(getLastSignificantChild(statement.getParent()))
            ? getLastSignificantChild(statement.getParent())
            : null;
        if (followupTerminator != null && !isValuableTerminationStatement(followupTerminator, terminableStatement)) {
            followupTerminator.delete();
            trimTrailingWhiteSpace(statement.getParent());
        }

        if (!isTerminated(ifStatements) &&
            getLastSignificantChild(statement.getParent()) != statement &&
            findTerminationStatement(ifStatements) == null) {
            ifStatements.add(createTerminationStatement(file, project, terminableStatement));
        }

        CodeEditUtil.markToReformat(parent.getNode(), true);
        CodeEditUtil.markToReformat(ifStatements.getNode(), true);
    }

    private static void switchAttachedComments(PyIfStatement statement, PsiElement oppositeAnchor) {
        List<PsiComment> oppositeComments = getAttachedComments(oppositeAnchor);
        List<PsiComment> ifComments = getAttachedComments(statement);

        if (!ifComments.isEmpty()) {
            oppositeAnchor.getParent().addRangeBefore(ifComments.get(0), ifComments.get(ifComments.size() - 1), oppositeAnchor);
            statement.getParent().deleteChildRange(ifComments.get(0), ifComments.get(ifComments.size() - 1));
        }

        if (!oppositeComments.isEmpty()) {
            statement.getParent().addRangeBefore(oppositeComments.get(0), oppositeComments.get(oppositeComments.size() - 1), statement);
            oppositeAnchor.getParent().deleteChildRange(oppositeComments.get(0), oppositeComments.get(oppositeComments.size() - 1));
        }
    }

    private static void switchInlineComments(PyIfStatement statement) {
        PyStatementPart ifPart = statement.getIfPart();
        PyStatementPart elsePart = statement.getElsePart();
        if (elsePart == null) {
            return;
        }

        PsiComment ifComment = getChildComment(ifPart);
        PsiComment elseComment = getChildComment(elsePart);
        if ((ifComment != null && isInlineServiceComment(ifComment)) || (elseComment != null && isInlineServiceComment(elseComment))) {
            return;
        }

        if (ifComment != null) {
            appendInlineComment(elsePart, ifComment);
            ifComment.delete();
        }

        if (elseComment != null) {
            appendInlineComment(ifPart, elseComment);
            elseComment.delete();
        }
    }

    private static boolean isTerminated(PyStatementList statementList) {
        PyControlFlow controlFlow = ControlFlowCache.getControlFlow(PsiTreeUtil.getParentOfType(statementList, ScopeOwner.class, false));
        Instruction[] instructions = controlFlow.getInstructions();
        Instruction currentInstruction = Arrays.stream(instructions).filter(it -> it.getElement() == statementList).findFirst().orElseThrow();
        boolean[] result = {true};
        ControlFlowUtil.iterate(currentInstruction.num(), instructions, instruction -> {
            if (instruction.allSucc().isEmpty()) {
                result[0] = false;
                return ControlFlowUtil.Operation.BREAK;
            }
            else if (instruction == currentInstruction) {
                return ControlFlowUtil.Operation.NEXT;
            }
            else if (instruction instanceof ReadWriteInstruction) {
                return ControlFlowUtil.Operation.NEXT;
            }
            else if (instruction.getElement() == null) {
                return ControlFlowUtil.Operation.NEXT;
            }
            else if (!PsiTreeUtil.isAncestor(statementList, instruction.getElement(), true)) {
                result[0] = false;
                return ControlFlowUtil.Operation.BREAK;
            }
            else if (isTerminationStatement(instruction.getElement())) {
                return ControlFlowUtil.Operation.CONTINUE;
            }
            else {
                return ControlFlowUtil.Operation.NEXT;
            }
        }, false);
        return result[0];
    }

    /**
     * Terminable statement is a statement which execution could be terminated
     */
    private static boolean isTerminableStatement(@Nullable PsiElement element) {
        return element instanceof PyFunction ||
            element instanceof PyForPart ||
            element instanceof PyWhilePart;
    }

    /**
     * Termination statement is a statement which interrupts the execution flow
     */
    private static boolean isTerminationStatement(@Nullable PsiElement element) {
        return element instanceof PyReturnStatement ||
            element instanceof PyRaiseStatement ||
            element instanceof PyContinueStatement ||
            element instanceof PyBreakStatement;
    }

    private static @Nullable PyStatementListContainer findTerminableParent(PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent != null) {
            if (isTerminableStatement(parent)) {
                return (PyStatementListContainer) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private static @Nullable PsiElement findTerminationStatement(PsiElement element) {
        for (PsiElement child : element.getChildren()) {
            if (isTerminationStatement(child)) {
                return child;
            }
        }
        return null;
    }

    /**
     * Termination statement is valuable if it's removal changes the overall logic
     */
    private static boolean isValuableTerminationStatement(PsiElement element, PyStatementListContainer terminableStatement) {
        return element instanceof PyReturnStatement returnStatement &&
            (returnStatement.getExpression() != null || !(terminableStatement instanceof PyFunction)) ||
            element instanceof PyRaiseStatement ||
            element instanceof PyBreakStatement;
    }

    private PsiElement createTerminationStatement(PsiFile file, Project project, PyStatementListContainer targetStatement) {
        LanguageLevel level = LanguageLevel.forElement(file);
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        if (targetStatement instanceof PyFunction) {
            return generator.createFromText(level, PyReturnStatement.class, "return");
        }
        else if (targetStatement instanceof PyForPart) {
            return generator.createFromText(level, PyContinueStatement.class, "continue");
        }
        else if (targetStatement instanceof PyWhilePart) {
            return generator.createFromText(level, PyContinueStatement.class, "continue");
        }
        else {
            throw new IncorrectOperationException(getClass().getName() + " is not a terminable statement");
        }
    }

    private static PyStatementListContainer getParentStatementListContainer(PsiElement element) {
        PyStatementListContainer container = PsiTreeUtil.getParentOfType(element, PyStatementListContainer.class, false);
        assert container != null;
        return container;
    }

    private static List<PsiComment> getAttachedComments(PsiElement element) {
        List<PsiComment> result = new ArrayList<>();
        for (PsiComment comment : PyPsiUtils.getPrecedingComments(element)) {
            if (isAttachedServiceComment(comment)) {
                break;
            }
            result.add(comment);
        }
        return result;
    }

    private static @Nullable PsiComment getChildComment(PsiElement element) {
        for (ASTNode child : element.getNode().getChildren(null)) {
            if (child instanceof PsiComment comment) {
                return comment;
            }
        }
        return null;
    }

    private static void appendInlineComment(PyStatementPart part, PsiComment comment) {
        ASTNode trailingColonNode = getPrevSignificantNode(part.getStatementList().getNode());
        assert trailingColonNode != null;
        CodeEditUtil.addChild(part.getNode(), comment.getNode().copyElement(), trailingColonNode.getTreeNext());
    }

    private static @Nullable ASTNode getPrevSignificantNode(ASTNode node) {
        return PyPsiUtils.skipSiblingsBackward(node, insignificantTokenSet);
    }

    private static @Nullable PsiElement getLastSignificantChild(PsiElement element) {
        return PyPsiUtils.getPrevNonCommentSibling(element.getLastChild(), false);
    }

    private static @Nullable PsiElement getNextMovableSibling(PsiElement element) {
        PsiElement sibling = element.getNextSibling();
        while (sibling != null) {
            if (!(sibling instanceof PsiWhiteSpace) &&
                (!(sibling instanceof PsiComment comment) || isAttachedServiceComment(comment))) {
                return sibling;
            }
            sibling = sibling.getNextSibling();
        }
        return null;
    }

    private static boolean isAttachedServiceComment(PsiComment comment) {
        return SuppressionUtil.isSuppressionComment(comment) || isPylintComment(comment);
    }

    private static boolean isInlineServiceComment(PsiComment comment) {
        return PyTypeHintGenerationUtil.isTypeHintComment(comment) || isPylintComment(comment);
    }

    private static boolean isPylintComment(PsiComment comment) {
        return comment.getText().startsWith(PYLINT_COMMENT_PREFIX);
    }

    private static void trimTrailingWhiteSpace(PsiElement element) {
        ASTNode[] children = element.getNode().getChildren(null);
        List<PsiWhiteSpace> trailingWhiteSpace = new ArrayList<>();
        for (int i = children.length - 1; i >= 0; i--) {
            if (!(children[i] instanceof PsiWhiteSpace whiteSpace)) {
                break;
            }
            trailingWhiteSpace.add(0, whiteSpace);
        }
        if (!trailingWhiteSpace.isEmpty()) {
            element.deleteChildRange(trailingWhiteSpace.get(0), trailingWhiteSpace.get(trailingWhiteSpace.size() - 1));
        }
    }
}
