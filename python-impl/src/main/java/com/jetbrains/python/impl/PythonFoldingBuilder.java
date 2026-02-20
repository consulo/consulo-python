/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyFileElementType;
import com.jetbrains.python.impl.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.application.util.LineTokenizer;
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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl
public class PythonFoldingBuilder extends CustomFoldingBuilder implements DumbAware {

  public static final TokenSet FOLDABLE_COLLECTIONS_LITERALS =
    TokenSet.create(PyElementTypes.SET_LITERAL_EXPRESSION,
                    PyElementTypes.DICT_LITERAL_EXPRESSION,
                    PyElementTypes.GENERATOR_EXPRESSION,
                    PyElementTypes.SET_COMP_EXPRESSION,
                    PyElementTypes.DICT_COMP_EXPRESSION,
                    PyElementTypes.LIST_LITERAL_EXPRESSION,
                    PyElementTypes.LIST_COMP_EXPRESSION,
                    PyElementTypes.TUPLE_EXPRESSION);

  @Override
  protected void buildLanguageFoldRegions(@Nonnull List<FoldingDescriptor> descriptors,
                                          @Nonnull PsiElement root,
                                          @Nonnull Document document,
                                          boolean quick) {
    if (root instanceof PyFile && ((PyFile)root).getVirtualFile() instanceof LightVirtualFile) {
      return;
    }
    appendDescriptors(root.getNode(), descriptors);
  }

  private static void appendDescriptors(ASTNode node, List<FoldingDescriptor> descriptors) {
    IElementType elementType = node.getElementType();
    if (elementType instanceof PyFileElementType) {
      List<PyImportStatementBase> imports = ((PyFile)node.getPsi()).getImportBlock();
      if (imports.size() > 1) {
        PyImportStatementBase firstImport = imports.get(0);
        PyImportStatementBase lastImport = imports.get(imports.size() - 1);
        descriptors.add(new FoldingDescriptor(firstImport,
                                              new TextRange(firstImport.getTextRange().getStartOffset(),
                                                            lastImport.getTextRange().getEndOffset())));
      }
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
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      appendDescriptors(child, descriptors);
      child = child.getTreeNext();
    }
  }

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
      descriptors.add(new FoldingDescriptor(node, TextRange.create(node.getStartOffset(), lastCommentNode.getTextRange().getEndOffset())));
    }

  }

  private static void foldCollectionLiteral(ASTNode node, List<FoldingDescriptor> descriptors) {
    if (StringUtil.countNewLines(node.getChars()) > 0) {
      TextRange range = node.getTextRange();
      int delta = node.getElementType() == PyElementTypes.TUPLE_EXPRESSION ? 0 : 1;
      descriptors.add(new FoldingDescriptor(node, TextRange.create(range.getStartOffset() + delta, range.getEndOffset() - delta)));
    }
  }

  private static void foldStatementList(ASTNode node, List<FoldingDescriptor> descriptors) {
    IElementType elType = node.getTreeParent().getElementType();
    if (elType == PyElementTypes.FUNCTION_DECLARATION || elType == PyElementTypes.CLASS_DECLARATION || ifFoldBlocks(node, elType)) {
      ASTNode colon = node.getTreeParent().findChildByType(PyTokenTypes.COLON);
      if (colon != null && colon.getStartOffset() + 1 < node.getTextRange().getEndOffset() - 1) {
        CharSequence chars = node.getChars();
        int nodeStart = node.getTextRange().getStartOffset();
        int endOffset = node.getTextRange().getEndOffset();
        while (endOffset > colon.getStartOffset() + 2 && endOffset > nodeStart && Character.isWhitespace(chars.charAt(endOffset - nodeStart - 1))) {
          endOffset--;
        }
        descriptors.add(new FoldingDescriptor(node, new TextRange(colon.getStartOffset() + 1, endOffset)));
      }
      else {
        TextRange range = node.getTextRange();
        if (range.getStartOffset() < range.getEndOffset() - 1) { // only for ranges at least 1 char wide
          descriptors.add(new FoldingDescriptor(node, range));
        }
      }
    }
  }

  private static boolean ifFoldBlocks(ASTNode statementList, IElementType parentType) {
    if (!PyElementTypes.PARTS.contains(parentType) && parentType != PyElementTypes.WITH_STATEMENT) {
      return false;
    }
    PsiElement element = statementList.getPsi();
    if (element instanceof PyStatementList) {
      return StringUtil.countNewLines(element.getText()) > 0;
    }
    return false;
  }

  private static void foldLongStrings(ASTNode node, List<FoldingDescriptor> descriptors) {
    //don't want to fold docstrings like """\n string \n """
    boolean shouldFoldDocString = getDocStringOwnerType(node) != null && StringUtil.countNewLines(node.getChars()) > 1;
    boolean shouldFoldString = getDocStringOwnerType(node) == null && StringUtil.countNewLines(node.getChars()) > 0;
    if (shouldFoldDocString || shouldFoldString) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }
  }

  @Nullable
  private static IElementType getDocStringOwnerType(ASTNode node) {
    ASTNode treeParent = node.getTreeParent();
    IElementType parentType = treeParent.getElementType();
    if (parentType == PyElementTypes.EXPRESSION_STATEMENT && treeParent.getTreeParent() != null) {
      ASTNode parent2 = treeParent.getTreeParent();
      if (parent2.getElementType() == PyElementTypes.STATEMENT_LIST && parent2.getTreeParent() != null && treeParent == parent2.getFirstChildNode()) {
        ASTNode parent3 = parent2.getTreeParent();
        if (parent3.getElementType() == PyElementTypes.FUNCTION_DECLARATION || parent3.getElementType() == PyElementTypes.CLASS_DECLARATION) {
          return parent3.getElementType();
        }
      }
      else if (parent2.getElementType() instanceof PyFileElementType) {
        return parent2.getElementType();
      }
    }
    return null;
  }

  @Override
  protected String getLanguagePlaceholderText(@Nonnull ASTNode node, @Nonnull TextRange range) {
    if (isImport(node)) {
      return "import ...";
    }
    if (node.getElementType() == PyElementTypes.STRING_LITERAL_EXPRESSION) {
      PyStringLiteralExpression stringLiteralExpression = (PyStringLiteralExpression)node.getPsi();
      if (stringLiteralExpression.isDocString()) {
        String stringValue = stringLiteralExpression.getStringValue().trim();
        String[] lines = LineTokenizer.tokenize(stringValue, true);
        if (lines.length > 2 && lines[1].trim().length() == 0) {
          return "\"\"\"" + lines[0].trim() + "...\"\"\"";
        }
        return "\"\"\"...\"\"\"";
      }
      else {
        return getLanguagePlaceholderForString(stringLiteralExpression);
      }
    }
    return "...";
  }

  private static String getLanguagePlaceholderForString(PyStringLiteralExpression stringLiteralExpression) {
    String stringText = stringLiteralExpression.getText();
    Pair<String, String> quotes = PyStringLiteralUtil.getQuotes(stringText);
    if (quotes != null) {
      return quotes.second + "..." + quotes.second;
    }
    return "...";
  }

  @Override
  protected boolean isRegionCollapsedByDefault(@Nonnull ASTNode node) {
    if (isImport(node)) {
      return CodeFoldingSettings.getInstance().COLLAPSE_IMPORTS;
    }
    if (node.getElementType() == PyElementTypes.STRING_LITERAL_EXPRESSION) {
      if (getDocStringOwnerType(node) == PyElementTypes.FUNCTION_DECLARATION && CodeFoldingSettings.getInstance().COLLAPSE_METHODS) {
        // method will be collapsed, no need to also collapse docstring
        return false;
      }
      if (getDocStringOwnerType(node) != null) {
        return CodeFoldingSettings.getInstance().COLLAPSE_DOC_COMMENTS;
      }
      return PythonFoldingSettings.getInstance().isCollapseLongStrings();
    }
    if (node.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) {
      return PythonFoldingSettings.getInstance().isCollapseSequentialComments();
    }
    if (node.getElementType() == PyElementTypes.STATEMENT_LIST && node.getTreeParent()
                                                                      .getElementType() == PyElementTypes.FUNCTION_DECLARATION) {
      return CodeFoldingSettings.getInstance().COLLAPSE_METHODS;
    }
    if (FOLDABLE_COLLECTIONS_LITERALS.contains(node.getElementType())) {
      return PythonFoldingSettings.getInstance().isCollapseLongCollections();
    }
    return false;
  }

  @Override
  protected boolean isCustomFoldingCandidate(@Nonnull ASTNode node) {
    return node.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT;
  }

  @Override
  protected boolean isCustomFoldingRoot(@Nonnull ASTNode node) {
    return node.getPsi() instanceof PyFile || node.getElementType() == PyElementTypes.STATEMENT_LIST;
  }

  private static boolean isImport(@Nonnull ASTNode node) {
    return PyElementTypes.IMPORT_STATEMENTS.contains(node.getElementType());
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
