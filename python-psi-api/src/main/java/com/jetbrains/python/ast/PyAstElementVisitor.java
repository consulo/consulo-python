package com.jetbrains.python.ast;

import consulo.language.psi.PsiElementVisitor;

/**
 * Visitor for python-specific nodes.
 */
public class PyAstElementVisitor extends PsiElementVisitor {
    public void visitPyElement(PyAstElement node) {
        visitElement(node);
    }

    public void visitPyReferenceExpression(PyAstReferenceExpression node) {
        visitPyExpression(node);
    }

    public void visitPyTargetExpression(PyAstTargetExpression node) {
        visitPyExpression(node);
    }

    public void visitPyCallExpression(PyAstCallExpression node) {
        visitPyExpression(node);
    }

    public void visitPyDecoratorList(PyAstDecoratorList node) {
        visitPyElement(node);
    }

    public void visitPyDecorator(PyAstDecorator node) {
        visitPyElement(node);
    }

    public void visitPyComprehensionElement(PyAstComprehensionElement node) {
        visitPyExpression(node);
    }

    public void visitPyGeneratorExpression(PyAstGeneratorExpression node) {
        visitPyComprehensionElement(node);
    }

    public void visitPyBinaryExpression(PyAstBinaryExpression node) {
        visitPyExpression(node);
    }

    public void visitPyPrefixExpression(PyAstPrefixExpression node) {
        visitPyExpression(node);
    }

    public void visitPySequenceExpression(PyAstSequenceExpression node) {
        visitPyExpression(node);
    }

    public void visitPyTupleExpression(PyAstTupleExpression<?> node) {
        visitPySequenceExpression(node);
    }

    public void visitPyParenthesizedExpression(PyAstParenthesizedExpression node) {
        visitPyExpression(node);
    }

    public void visitPyDictLiteralExpression(PyAstDictLiteralExpression node) {
        visitPyExpression(node);
    }

    public void visitPyListLiteralExpression(PyAstListLiteralExpression node) {
        visitPySequenceExpression(node);
    }

    public void visitPySetLiteralExpression(PyAstSetLiteralExpression node) {
        visitPySequenceExpression(node);
    }

    public void visitPyListCompExpression(PyAstListCompExpression node) {
        visitPyComprehensionElement(node);
    }

    public void visitPyDictCompExpression(PyAstDictCompExpression node) {
        visitPyComprehensionElement(node);
    }

    public void visitPySetCompExpression(PyAstSetCompExpression node) {
        visitPyComprehensionElement(node);
    }

    public void visitPyLambdaExpression(PyAstLambdaExpression node) {
        visitPyExpression(node);
    }

    public void visitPyAssignmentStatement(PyAstAssignmentStatement node) {
        visitPyStatement(node);
    }

    public void visitPyAugAssignmentStatement(PyAstAugAssignmentStatement node) {
        visitPyStatement(node);
    }

    public void visitPyDelStatement(PyAstDelStatement node) {
        visitPyStatement(node);
    }

    public void visitPyReturnStatement(PyAstReturnStatement node) {
        visitPyStatement(node);
    }

    public void visitPyPassStatement(PyAstPassStatement node) {
        visitPyStatement(node);
    }

    public void visitPyYieldExpression(PyAstYieldExpression node) {
        visitPyExpression(node);
    }

    public void visitPyTryExceptStatement(PyAstTryExceptStatement node) {
        visitPyStatement(node);
    }

    public void visitPyRaiseStatement(PyAstRaiseStatement node) {
        visitPyStatement(node);
    }

    public void visitPyBreakStatement(PyAstBreakStatement node) {
        visitPyStatement(node);
    }

    public void visitPyContinueStatement(PyAstContinueStatement node) {
        visitPyStatement(node);
    }

    public void visitPyGlobalStatement(PyAstGlobalStatement node) {
        visitPyStatement(node);
    }

    public void visitPyFromImportStatement(PyAstFromImportStatement node) {
        visitPyStatement(node);
    }

    public void visitPyIfStatement(PyAstIfStatement node) {
        visitPyStatement(node);
    }

    public void visitPyForStatement(PyAstForStatement node) {
        visitPyStatement(node);
    }

    public void visitPyWhileStatement(PyAstWhileStatement node) {
        visitPyStatement(node);
    }

    public void visitPyWithStatement(PyAstWithStatement node) {
        visitPyStatement(node);
    }

    public void visitPyExpressionStatement(PyAstExpressionStatement node) {
        visitPyStatement(node);
    }

    public void visitPyStatement(PyAstStatement node) {
        visitPyElement(node);
    }

    public void visitPyExpression(PyAstExpression node) {
        visitPyElement(node);
    }

    public void visitPyParameterList(PyAstParameterList node) {
        visitPyElement(node);
    }

    public void visitPyParameter(PyAstParameter node) {
        visitPyElement(node);
    }

    public void visitPyNamedParameter(PyAstNamedParameter node) {
        visitPyParameter(node);
    }

    public void visitPyTupleParameter(PyAstTupleParameter node) {
        visitPyParameter(node);
    }

    public void visitPyArgumentList(PyAstArgumentList node) {
        visitPyElement(node);
    }

    public void visitPyStatementList(PyAstStatementList node) {
        visitPyElement(node);
    }

    public void visitPyExceptBlock(PyAstExceptPart node) {
        visitPyElement(node);
    }

    public void visitPyFunction(PyAstFunction node) {
        visitPyElement(node);
    }

    public void visitPyClass(PyAstClass node) {
        visitPyElement(node);
    }

    public void visitPyFile(PyAstFile node) {
        visitPyElement(node);
    }

    public void visitPyStringLiteralExpression(PyAstStringLiteralExpression node) {
        visitPyElement(node);
    }

    public void visitPyFormattedStringElement(PyAstFormattedStringElement node) {
        visitPyElement(node);
    }

    public void visitPyFStringFragment(PyAstFStringFragment node) {
        visitPyElement(node);
    }

    public void visitPyNumericLiteralExpression(PyAstNumericLiteralExpression node) {
        visitPyElement(node);
    }

    public void visitPyPrintStatement(PyAstPrintStatement node) {
        visitPyStatement(node);
    }

    public void visitPyImportStatement(PyAstImportStatement node) {
        visitPyStatement(node);
    }

    public void visitPyReprExpression(PyAstReprExpression node) {
        visitPyExpression(node);
    }

    public void visitPyNonlocalStatement(PyAstNonlocalStatement node) {
        visitPyStatement(node);
    }

    public void visitPyStarExpression(PyAstStarExpression node) {
        visitPyExpression(node);
    }

    public void visitPyDoubleStarExpression(PyAstDoubleStarExpression node) {
        visitPyExpression(node);
    }

    public void visitPyStarArgument(PyAstStarArgument node) {
        visitPyExpression(node);
    }

    public void visitPySubscriptionExpression(PyAstSubscriptionExpression node) {
        visitPyExpression(node);
    }

    public void visitPyImportElement(PyAstImportElement node) {
        visitPyElement(node);
    }

    public void visitPyStarImportElement(PyAstStarImportElement node) {
        visitPyElement(node);
    }

    public void visitPyConditionalStatementPart(PyAstConditionalStatementPart node) {
        visitPyElement(node);
    }

    public void visitPyAssertStatement(PyAstAssertStatement node) {
        visitPyElement(node);
    }

    public void visitPyNoneLiteralExpression(PyAstNoneLiteralExpression node) {
        visitPyElement(node);
    }

    public void visitPyEllipsisLiteralExpression(PyAstEllipsisLiteralExpression node) {
        visitPyElement(node);
    }

    public void visitPyBoolLiteralExpression(PyAstBoolLiteralExpression node) {
        visitPyElement(node);
    }

    public void visitPyConditionalExpression(PyAstConditionalExpression node) {
        visitPyElement(node);
    }

    public void visitPyKeywordArgument(PyAstKeywordArgument node) {
        visitPyElement(node);
    }

    public void visitPyWithItem(PyAstWithItem node) {
        visitPyElement(node);
    }

    public void visitPyTypeDeclarationStatement(PyAstTypeDeclarationStatement node) {
        visitPyStatement(node);
    }

    public void visitPyAnnotation(PyAstAnnotation node) {
        visitPyElement(node);
    }

    public void visitPySlashParameter(PyAstSlashParameter node) {
        visitPyElement(node);
    }

    public void visitPySingleStarParameter(PyAstSingleStarParameter node) {
        visitPyElement(node);
    }

    public void visitPyAssignmentExpression(PyAstAssignmentExpression node) {
        visitPyExpression(node);
    }

    public void visitPyPattern(PyAstPattern node) {
        visitPyElement(node);
    }

    public void visitPyAsPattern(PyAstAsPattern node) {
        visitPyPattern(node);
    }

    public void visitPyCapturePattern(PyAstCapturePattern node) {
        visitPyPattern(node);
    }

    public void visitWildcardPattern(PyAstWildcardPattern node) {
        visitPyPattern(node);
    }

    public void visitPyClassPattern(PyAstClassPattern node) {
        visitPyPattern(node);
    }

    public void visitPyDoubleStarPattern(PyAstDoubleStarPattern node) {
        visitPyPattern(node);
    }

    public void visitPySingleStarPattern(PyAstSingleStarPattern node) {
        visitPyPattern(node);
    }

    public void visitPyGroupPattern(PyAstGroupPattern node) {
        visitPyPattern(node);
    }

    public void visitPyKeyValuePattern(PyAstKeyValuePattern node) {
        visitPyPattern(node);
    }

    public void visitPyMappingPattern(PyAstMappingPattern node) {
        visitPyPattern(node);
    }

    public void visitPyOrPattern(PyAstOrPattern node) {
        visitPyPattern(node);
    }

    public void visitPySequencePattern(PyAstSequencePattern node) {
        visitPyPattern(node);
    }

    public void visitPyValuePattern(PyAstValuePattern node) {
        visitPyPattern(node);
    }

    public void visitPyKeywordPattern(PyAstKeywordPattern node) {
        visitPyPattern(node);
    }

    public void visitPyLiteralPattern(PyAstLiteralPattern node) {
        visitPyPattern(node);
    }

    public void visitPyPatternArgumentList(PyAstPatternArgumentList node) {
        visitPyElement(node);
    }

    public void visitPyMatchStatement(PyAstMatchStatement node) {
        visitPyStatement(node);
    }

    public void visitPyCaseClause(PyAstCaseClause node) {
        visitPyElement(node);
    }

    public void visitPyTypeAliasStatement(PyAstTypeAliasStatement node) {
        visitPyStatement(node);
    }

    public void visitPyTypeParameter(PyAstTypeParameter node) {
        visitPyElement(node);
    }

    public void visitPyTypeParameterList(PyAstTypeParameterList node) {
        visitPyElement(node);
    }

    public void visitPyKeyValueExpression(PyAstKeyValueExpression node) {
        visitPyExpression(node);
    }
}
