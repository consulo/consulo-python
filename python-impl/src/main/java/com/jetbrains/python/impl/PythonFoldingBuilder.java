// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.ast.PyAstAnnotation;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstFile;
import com.jetbrains.python.ast.PyAstFromImportStatement;
import com.jetbrains.python.ast.PyAstImportStatementBase;
import com.jetbrains.python.ast.PyAstStatementList;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.application.util.LineTokenizer;
import consulo.codeEditor.FoldingGroup;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.editor.folding.CodeFoldingSettings;
import consulo.language.editor.folding.CustomFoldingBuilder;
import consulo.language.editor.folding.FoldingDescriptor;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ExtensionImpl
public class PythonFoldingBuilder extends CustomFoldingBuilder implements DumbAware {
    public static final String PYTHON_TYPE_ANNOTATION_GROUP_NAME = "Python type annotation";

    public static final TokenSet FOLDABLE_COLLECTIONS_LITERALS = TokenSet.create(
        PyElementTypes.SET_LITERAL_EXPRESSION,
        PyElementTypes.DICT_LITERAL_EXPRESSION,
        PyElementTypes.GENERATOR_EXPRESSION,
        PyElementTypes.SET_COMP_EXPRESSION,
        PyElementTypes.DICT_COMP_EXPRESSION,
        PyElementTypes.LIST_LITERAL_EXPRESSION,
        PyElementTypes.LIST_COMP_EXPRESSION,
        PyElementTypes.TUPLE_EXPRESSION
    );

    @Override
    @RequiredReadAction
    protected void buildLanguageFoldRegions(List<FoldingDescriptor> descriptors, PsiElement root, Document document, boolean quick) {
        // Consulo: no folding for light (in-memory) python files
        if (root instanceof PyAstFile && ((PyAstFile) root).getVirtualFile() instanceof LightVirtualFile) {
            return;
        }
        appendDescriptors(root.getNode(), descriptors);
    }

    @Override
    @RequiredReadAction
    protected String getLanguagePlaceholderText(ASTNode node, TextRange range) {
        if (isImport(node)) {
            return "import ...";
        }
        if (node.getElementType() == PyElementTypes.STRING_LITERAL_EXPRESSION) {
            PyAstStringLiteralExpression stringLiteralExpression = (PyAstStringLiteralExpression) node.getPsi();
            String prefix = stringLiteralExpression.getStringElements().get(0).getPrefix();
            if (stringLiteralExpression.isDocString()) {
                String stringValue = stringLiteralExpression.getStringValue().trim();
                String[] lines = LineTokenizer.tokenize(stringValue, true);
                if (lines.length > 2 && lines[1].trim().isEmpty()) {
                    return prefix + "\"\"\"" + lines[0].trim() + "...\"\"\"";
                }
                return prefix + "\"\"\"...\"\"\"";
            }
            else {
                return prefix + getLanguagePlaceholderForString(stringLiteralExpression);
            }
        }
        return "...";
    }

    @Override
    @RequiredReadAction
    protected boolean isRegionCollapsedByDefault(ASTNode node) {
        if (isImport(node)) {
            return CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS;
        }
        IElementType elementType = node.getElementType();
        if (elementType == PyElementTypes.STRING_LITERAL_EXPRESSION) {
            IElementType docStringOwnerType = getDocStringOwnerType(node);
            if (isFunction(docStringOwnerType) && CodeFoldingSettings.getInstance().COLLAPSE_METHODS) {
                // method will be collapsed, no need to also collapse docstring
                return false;
            }
            if (docStringOwnerType != null) {
                return CodeFoldingSettings.getInstance().COLLAPSE_DOC_COMMENTS;
            }
            return PythonFoldingSettings.getInstance().isCollapseLongStrings();
        }
        if (elementType == PyTokenTypes.END_OF_LINE_COMMENT) {
            return PythonFoldingSettings.getInstance().isCollapseSequentialComments();
        }
        if (elementType == PyElementTypes.ANNOTATION) {
            return PythonFoldingSettings.getInstance().isCollapseTypeAnnotations();
        }
        if (elementType == PyElementTypes.STATEMENT_LIST && isFunction(node.getTreeParent().getElementType())) {
            return CodeFoldingSettings.getInstance().COLLAPSE_METHODS;
        }
        if (FOLDABLE_COLLECTIONS_LITERALS.contains(elementType)) {
            return PythonFoldingSettings.getInstance().isCollapseLongCollections();
        }
        if (isLanguageSpecificFoldableBlock(elementType)) {
            return CodeFoldingSettings.getInstance().COLLAPSE_METHODS;
        }
        return false;
    }

    // TODO consulo: CustomFoldingBuilder#keepExpandedOnFirstCollapseAll missing
    protected boolean keepExpandedOnFirstCollapseAll(ASTNode node) {
        IElementType elementType = node.getElementType();
        if (elementType == PyElementTypes.ANNOTATION) {
            return !PythonFoldingSettings.getInstance().isCollapseTypeAnnotations();
        }
        return false;
    }

    @Override
    protected boolean isCustomFoldingCandidate(ASTNode node) {
        return node.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT;
    }

    @Override
    protected boolean isCustomFoldingRoot(ASTNode node) {
        return node.getPsi() instanceof PyAstFile || node.getElementType() == PyElementTypes.STATEMENT_LIST;
    }

    protected boolean isLanguageSpecificFoldableBlock(IElementType elementType) {
        return false;
    }

    @RequiredReadAction
    private void appendDescriptors(ASTNode node, List<FoldingDescriptor> descriptors) {
        IElementType elementType = node.getElementType();
        if (node.getPsi() instanceof PyAstFile pyFile) {
            List<? extends PyAstImportStatementBase> imports = pyFile.getImportBlock();
            int firstIndex = 0;
            while (firstIndex < imports.size()
                && imports.get(firstIndex) instanceof PyAstFromImportStatement fromImport
                && fromImport.isFromFuture()) {
                firstIndex++;
            }
            if (imports.size() - firstIndex > 1) {
                PyAstImportStatementBase firstImport = imports.get(firstIndex);
                PyAstImportStatementBase lastImport = imports.get(imports.size() - 1);
                descriptors.add(new FoldingDescriptor(
                    firstImport,
                    new TextRange(firstImport.getTextRange().getStartOffset(), lastImport.getTextRange().getEndOffset())
                ));
            }
        }
        else if (elementType == PyElementTypes.MATCH_STATEMENT) {
            foldMatchStatement(node, descriptors);
        }
        else if (elementType == PyElementTypes.STATEMENT_LIST) {
            foldStatementList(node, descriptors);
        }
        else if (elementType == PyElementTypes.STRING_LITERAL_EXPRESSION) {
            foldLongStrings(node, descriptors);
        }
        else if (FOLDABLE_COLLECTIONS_LITERALS.contains(elementType)) {
            foldCollectionLiteral(node, descriptors);
        }
        else if (elementType == PyTokenTypes.END_OF_LINE_COMMENT) {
            foldSequentialComments(node, descriptors);
        }
        else if (elementType == PyElementTypes.ANNOTATION) {
            PyAstExpression annotationValue = node.getPsi() instanceof PyAstAnnotation annotation ? annotation.getValue() : null;
            if (annotationValue != null) {
                descriptors.add(new FoldingDescriptor(
                    node,
                    annotationValue.getTextRange(),
                    FoldingGroup.newGroup(PYTHON_TYPE_ANNOTATION_GROUP_NAME)
                ));
            }
        }
        else if (isLanguageSpecificFoldableBlock(elementType)) {
            TextRange nodeRange = node.getTextRange();
            if (!nodeRange.isEmpty()) {
                ASTNode colon = node.findChildByType(PyTokenTypes.COLON);
                foldSegment(node, descriptors, nodeRange, colon);
            }
        }
        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            appendDescriptors(child, descriptors);
            child = child.getTreeNext();
        }
    }

    @RequiredReadAction
    private static void foldSequentialComments(ASTNode node, List<FoldingDescriptor> descriptors) {
        //do not start folded comments from custom region
        if (isCustomRegionElement(node.getPsi())) {
            return;
        }
        //need to skip previous comments in sequence
        ASTNode curNode = node.getTreePrev();
        while (curNode != null) {
            if (curNode.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) {
                if (isCustomRegionElement(curNode.getPsi())) {
                    break;
                }
                return;
            }
            curNode = curNode.getPsi() instanceof PsiWhiteSpace ? curNode.getTreePrev() : null;
        }

        //fold sequence comments in one block
        curNode = node.getTreeNext();
        ASTNode lastCommentNode = node;
        while (curNode != null) {
            if (curNode.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) {
                //do not end folded comments with custom region
                if (isCustomRegionElement(curNode.getPsi())) {
                    break;
                }
                lastCommentNode = curNode;
                curNode = curNode.getTreeNext();
                continue;
            }
            curNode = curNode.getPsi() instanceof PsiWhiteSpace ? curNode.getTreeNext() : null;
        }

        if (lastCommentNode != node) {
            descriptors.add(new FoldingDescriptor(
                node,
                TextRange.create(node.getStartOffset(), lastCommentNode.getTextRange().getEndOffset())
            ));
        }
    }

    private static void foldCollectionLiteral(ASTNode node, List<FoldingDescriptor> descriptors) {
        if (StringUtil.countNewLines(node.getChars()) > 0) {
            TextRange range = node.getTextRange();
            int delta = node.getElementType() == PyElementTypes.TUPLE_EXPRESSION ? 0 : 1;
            descriptors.add(new FoldingDescriptor(node, TextRange.create(range.getStartOffset() + delta, range.getEndOffset() - delta)));
        }
    }

    private static void foldMatchStatement(ASTNode node, List<FoldingDescriptor> descriptors) {
        TextRange nodeRange = node.getTextRange();
        if (nodeRange.isEmpty()) {
            return;
        }

        IElementType elType = node.getElementType();
        if (elType == PyElementTypes.MATCH_STATEMENT) {
            ASTNode colon = node.findChildByType(PyTokenTypes.COLON);
            foldSegment(node, descriptors, nodeRange, colon);
        }
    }

    private static void foldSegment(ASTNode node, List<FoldingDescriptor> descriptors, TextRange nodeRange, @Nullable ASTNode colon) {
        int nodeEnd = nodeRange.getEndOffset();
        if (colon != null && nodeEnd - (colon.getStartOffset() + 1) > 1) {
            CharSequence chars = node.getChars();
            int nodeStart = nodeRange.getStartOffset();
            int foldStart = colon.getStartOffset() + 1;
            int foldEnd = nodeEnd;
            while (foldEnd > Math.max(nodeStart, foldStart + 1) && Character.isWhitespace(chars.charAt(foldEnd - nodeStart - 1))) {
                foldEnd--;
            }
            descriptors.add(new FoldingDescriptor(node, new TextRange(foldStart, foldEnd)));
        }
        else if (nodeRange.getLength() > 1) { // only for ranges at least 1 char wide
            descriptors.add(new FoldingDescriptor(node, nodeRange));
        }
    }

    @RequiredReadAction
    private void foldStatementList(ASTNode node, List<FoldingDescriptor> descriptors) {
        TextRange nodeRange = node.getTextRange();
        if (nodeRange.isEmpty()) {
            return;
        }

        IElementType parentType = node.getTreeParent().getElementType();
        if (isFunction(parentType) || isClass(parentType) || checkFoldBlocks(node, parentType)) {
            ASTNode colon = node.getTreeParent().findChildByType(PyTokenTypes.COLON);
            foldSegment(node, descriptors, nodeRange, colon);
        }
    }

    @RequiredReadAction
    protected boolean checkFoldBlocks(ASTNode statementList, IElementType parentType) {
        PsiElement element = statementList.getPsi();
        assert element instanceof PyAstStatementList;

        return PyElementTypes.PARTS.contains(parentType) ||
            parentType == PyElementTypes.WITH_STATEMENT ||
            parentType == PyElementTypes.CASE_CLAUSE;
    }

    private void foldLongStrings(ASTNode node, List<FoldingDescriptor> descriptors) {
        //don't want to fold docstrings like """\n string \n """
        boolean shouldFoldDocString = getDocStringOwnerType(node) != null && StringUtil.countNewLines(node.getChars()) > 1;
        boolean shouldFoldString = getDocStringOwnerType(node) == null && StringUtil.countNewLines(node.getChars()) > 0;
        if (shouldFoldDocString || shouldFoldString) {
            descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
        }
    }

    protected @Nullable IElementType getDocStringOwnerType(ASTNode node) {
        ASTNode treeParent = node.getTreeParent();
        IElementType parentType = treeParent.getElementType();
        if (parentType == PyElementTypes.EXPRESSION_STATEMENT && treeParent.getTreeParent() != null) {
            ASTNode parent2 = treeParent.getTreeParent();
            if (parent2.getElementType() == PyElementTypes.STATEMENT_LIST && parent2.getTreeParent() != null
                && treeParent == parent2.getFirstChildNode()) {
                IElementType parent3 = parent2.getTreeParent().getElementType();
                if (isFunction(parent3) || isClass(parent3)) {
                    return parent3;
                }
            }
            else if (parent2 instanceof PyAstFile) {
                return parent2.getElementType();
            }
        }
        return null;
    }

    @RequiredReadAction
    private static String getLanguagePlaceholderForString(PyAstStringLiteralExpression stringLiteralExpression) {
        String stringText = stringLiteralExpression.getText();
        Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(stringText);
        if (quotes != null) {
            return quotes.second + "..." + quotes.second;
        }
        return "...";
    }

    protected boolean isImport(ASTNode node) {
        return PyElementTypes.IMPORT_STATEMENTS.contains(node.getElementType());
    }

    protected boolean isFunction(@Nullable IElementType elementType) {
        return elementType == PyElementTypes.FUNCTION_DECLARATION;
    }

    protected boolean isClass(@Nullable IElementType elementType) {
        return elementType == PyElementTypes.CLASS_DECLARATION;
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
