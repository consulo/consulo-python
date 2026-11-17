/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.validation;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstAnnotation;
import com.jetbrains.python.ast.PyAstCaseClause;
import com.jetbrains.python.ast.PyAstComprehensionElement;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.ast.PyAstFile;
import com.jetbrains.python.ast.PyAstForStatement;
import com.jetbrains.python.ast.PyAstFromImportStatement;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.PyAstImportStatement;
import com.jetbrains.python.ast.PyAstMatchStatement;
import com.jetbrains.python.ast.PyAstPrefixExpression;
import com.jetbrains.python.ast.PyAstTypeAliasStatement;
import com.jetbrains.python.ast.PyAstWithStatement;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.PyElementType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.editor.HighlightRangeExtension;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyKeywordHighlightingAnnotator extends PyAnnotatorBase implements HighlightRangeExtension {

  @Override
  public boolean isForceHighlightParents(PsiFile psiFile) {
    return psiFile instanceof PyAstFile;
  }

  @Override
  public void annotate(PsiElement element, PyAnnotationHolder holder) {
    if (holder.isBatchMode()) return;
    element.accept(new MyVisitor(holder));
  }

  private static class MyVisitor extends PyAstElementVisitor {
    private final PyAnnotationHolder myHolder;

    private MyVisitor(PyAnnotationHolder holder) { myHolder = holder; }

    @Override
    public void visitPyFunction(PyAstFunction node) {
      if (node.isAsyncAllowed()) {
        highlightKeyword(node, PyTokenTypes.ASYNC_KEYWORD);
      }
    }

    @Override
    public void visitPyForStatement(PyAstForStatement node) {
      highlightKeyword(node, PyTokenTypes.ASYNC_KEYWORD);
    }

    @Override
    public void visitPyWithStatement(PyAstWithStatement node) {
      highlightKeyword(node, PyTokenTypes.ASYNC_KEYWORD);
    }

    @Override
    public void visitPyPrefixExpression(PyAstPrefixExpression node) {
      highlightKeyword(node, PyTokenTypes.AWAIT_KEYWORD);
    }

    @Override
    public void visitPyComprehensionElement(PyAstComprehensionElement node) {
      highlightKeywords(node, PyTokenTypes.ASYNC_KEYWORD);
    }

    @Override
    public void visitPyMatchStatement(PyAstMatchStatement node) {
      highlightKeyword(node, PyTokenTypes.MATCH_KEYWORD);
    }

    @Override
    public void visitPyCaseClause(PyAstCaseClause node) {
      highlightKeyword(node, PyTokenTypes.CASE_KEYWORD);
    }

    @Override
    public void visitPyTypeAliasStatement(PyAstTypeAliasStatement node) {
      highlightKeyword(node, PyTokenTypes.TYPE_KEYWORD);
    }

    @Override
    public void visitPyImportStatement(PyAstImportStatement node) {
      highlightKeyword(node, PyTokenTypes.LAZY_KEYWORD);
    }

    @Override
    public void visitPyFromImportStatement(PyAstFromImportStatement node) {
      highlightKeyword(node, PyTokenTypes.LAZY_KEYWORD);
    }

    @Override
    public void visitElement(PsiElement element) {
      // Highlight None, True and False as keywords once again inside annotations after PyHighlighter
      // to keep their original color
      if (PyTokenTypes.EXPRESSION_KEYWORDS.contains(element.getNode().getElementType()) &&
          PsiTreeUtil.getParentOfType(element, PyAstAnnotation.class) != null) {
        myHolder.addHighlightingAnnotation(element, PyHighlighter.PY_KEYWORD);
      }
    }

    private void highlightKeyword(PsiElement node, PyElementType elementType) {
      highlightAsKeyword(node.getNode().findChildByType(elementType));
    }

    private void highlightKeywords(PsiElement node, PyElementType elementType) {
      for (ASTNode astNode : node.getNode().getChildren(TokenSet.create(elementType))) {
        highlightAsKeyword(astNode);
      }
    }

    private void highlightAsKeyword(@Nullable ASTNode astNode) {
      if (astNode != null) {
        myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(astNode)
          .textAttributes(PyHighlighter.PY_KEYWORD).create();
      }
    }
  }
}
