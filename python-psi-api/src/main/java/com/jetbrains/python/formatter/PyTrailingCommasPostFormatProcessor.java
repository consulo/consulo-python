package com.jetbrains.python.formatter;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.ast.PyAstArgumentList;
import com.jetbrains.python.ast.PyAstDictLiteralExpression;
import com.jetbrains.python.ast.PyAstElement;
import com.jetbrains.python.ast.PyAstListLiteralExpression;
import com.jetbrains.python.ast.PyAstParameterList;
import com.jetbrains.python.ast.PyAstParenthesizedExpression;
import com.jetbrains.python.ast.PyAstRecursiveElementVisitor;
import com.jetbrains.python.ast.PyAstSequenceExpression;
import com.jetbrains.python.ast.PyAstSetLiteralExpression;
import com.jetbrains.python.ast.PyAstSubscriptionExpression;
import com.jetbrains.python.ast.PyAstTupleExpression;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.psi.PyAstElementGenerator;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.codeStyle.PostFormatProcessor;
import consulo.language.codeStyle.PostFormatProcessorHelper;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.project.Project;

import java.util.Arrays;
import java.util.List;

@ExtensionImpl
public class PyTrailingCommasPostFormatProcessor implements PostFormatProcessor {

  private TextRange processTextRange(PsiElement psiElement, TextRange range, CodeStyleSettings settings) {
    if (psiElement.getLanguage() != PythonLanguage.getInstance()) {
      return range;
    }

    PyPsiUtilsCore.assertValid(psiElement);

    PsiFile psiFile = psiElement.isValid() ? psiElement.getContainingFile() : null;
    if (psiFile == null) {
      return range;
    }
    Project project = psiFile.getProject();
    TrailingCommaInsertVisitor visitor = new TrailingCommaInsertVisitor(settings, range, project);

    psiElement.accept(visitor);
    return visitor.getResultTextRange();
  }

  @Override
  public PsiElement processElement(PsiElement source, CodeStyleSettings settings) {
    processTextRange(source, source.getTextRange(), settings);
    return source;
  }

  @Override
  public TextRange processText(PsiFile source, TextRange rangeToReformat, CodeStyleSettings settings) {
    return processTextRange(source, rangeToReformat, settings);
  }

  private static class TrailingCommaInsertVisitor extends PyAstRecursiveElementVisitor {

    private final PyCodeStyleSettings pySettings;
    private final CommonCodeStyleSettings pyCommonSettings;
    private final PostFormatProcessorHelper helper;
    private final CodeStyleManager codeStyleManager;

    TrailingCommaInsertVisitor(CodeStyleSettings settings, TextRange range, Project project) {
      pySettings = settings.getCustomSettings(PyCodeStyleSettings.class);
      pyCommonSettings = settings.getCommonSettings(PythonLanguage.getInstance());
      helper = new PostFormatProcessorHelper(pyCommonSettings);
      helper.setResultTextRange(range);
      codeStyleManager = CodeStyleManager.getInstance(project);
    }

    TextRange getResultTextRange() {
      return helper.getResultTextRange();
    }

    @Override
    public void visitPyDictLiteralExpression(PyAstDictLiteralExpression node) {
      processCollectionLiteral(node);
      super.visitPyDictLiteralExpression(node);
    }

    @Override
    public void visitPyListLiteralExpression(PyAstListLiteralExpression node) {
      processCollectionLiteral(node);
      super.visitPyListLiteralExpression(node);
    }

    @Override
    public void visitPySetLiteralExpression(PyAstSetLiteralExpression node) {
      processCollectionLiteral(node);
      super.visitPySetLiteralExpression(node);
    }

    @Override
    public void visitPyParameterList(PyAstParameterList node) {
      if (pySettings.USE_TRAILING_COMMA_IN_PARAMETER_LIST) {
        processContainer(node, Arrays.asList(node.getParameters()));
      }
      super.visitPyParameterList(node);
    }

    @Override
    public void visitPyArgumentList(PyAstArgumentList node) {
      if (pySettings.USE_TRAILING_COMMA_IN_ARGUMENTS_LIST) {
        processContainer(node, Arrays.asList(node.getArguments()));
      }
      super.visitPyArgumentList(node);
    }

    @Override
    public void visitPyTupleExpression(PyAstTupleExpression<?> node) {
      if (pySettings.USE_TRAILING_COMMA_IN_COLLECTIONS && helper.isElementFullyInRange(node)) {
        processTupleExpression(node);
      }
      super.visitPyTupleExpression(node);
    }

    private void processCollectionLiteral(PyAstSequenceExpression node) {
      if (pySettings.USE_TRAILING_COMMA_IN_COLLECTIONS) {
        processContainer(node, Arrays.asList(node.getElements()));
      }
    }

    private void processContainer(PsiElement container, List<? extends PyAstElement> elements) {
      if (!helper.isElementFullyInRange(container)) {
        return;
      }
      PyAstElement lastElement = elements.isEmpty() ? null : elements.get(elements.size() - 1);
      if (lastElement != null) {
        if (!isMultiLine(lastElement)) {
          return;
        }
        insertComma(container, lastElement);
      }
    }

    private void processTupleExpression(PyAstTupleExpression<?> node) {
      if ((!(node.getParent() instanceof PyAstParenthesizedExpression) && !(node.getParent() instanceof PyAstSubscriptionExpression)) ||
          !isMultiLine(node)) {
        return;
      }
      PsiElement[] elements = node.getElements();
      PsiElement lastElement = elements.length == 0 ? null : elements[elements.length - 1];
      if (lastElement != null) {
        PsiElement nextSibling = lastElement.getNextSibling();
        ASTNode nextNode = nextSibling != null ? nextSibling.getNode() : null;
        if ((nextNode != null ? nextNode.getElementType() : null) != PyTokenTypes.COMMA) {
          insertComma(node, lastElement);
        }
      }
    }

    private void insertComma(PsiElement container, PsiElement after) {
      int initialLength = container.getTextLength();
      ASTNode comma = PyAstElementGenerator.getInstance(after.getProject()).createComma();
      PsiElement addedComma = container.addAfter(comma.getPsi(), after);
      codeStyleManager.reformatRange(container, after.getTextRange().getEndOffset(), addedComma.getTextRange().getEndOffset());
      helper.updateResultRange(initialLength, container.getTextLength());
    }

    private static boolean isMultiLine(PsiElement element) {
      PsiElement lookForNewLineAfter = PyPsiUtilsCore.getNextNonWhitespaceSiblingOnSameLine(element) instanceof PsiComment comment ? comment : element;
      return lookForNewLineAfter.getNextSibling() instanceof PsiWhiteSpace whiteSpace && whiteSpace.textContains('\n');
    }
  }
}
