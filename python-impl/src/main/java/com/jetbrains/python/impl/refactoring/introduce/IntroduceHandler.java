// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.introduce;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.types.PyLiteralStringType;
import com.jetbrains.python.impl.refactoring.NameSuggesterUtil;
import com.jetbrains.python.impl.refactoring.PyRefactoringUiService;
import com.jetbrains.python.impl.refactoring.PyRefactoringUtil;
import com.jetbrains.python.impl.refactoring.PyReplaceExpressionUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenType;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.event.RefactoringEventData;
import consulo.language.editor.refactoring.event.RefactoringEventListener;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.TemplateState;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Pair;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static com.jetbrains.python.impl.PyStringFormatParser.*;
import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

public abstract class IntroduceHandler implements RefactoringActionHandler {
  protected static PsiElement findAnchor(List<? extends PsiElement> occurrences) {
    PsiElement anchor = occurrences.get(0);
    Pair<PsiElement, TextRange> data = anchor.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    // Search anchor in the origin file, not in dummy.py, if selection breaks statement and thus element was generated
    if (data != null && occurrences.size() == 1) {
      return PsiTreeUtil.getParentOfType(data.getFirst(), PyStatement.class);
    }
    next:
    do {
      PyStatement statement = PsiTreeUtil.getParentOfType(anchor, PyStatement.class);
      if (statement != null) {
        PsiElement parent = statement.getParent();
        for (PsiElement element : occurrences) {
          if (!PsiTreeUtil.isAncestor(parent, element, true)) {
            anchor = statement;
            continue next;
          }
        }
      }
      return statement;
    }
    while (true);
  }

  protected static void ensureName(IntroduceOperation operation) {
    if (operation.getName() == null) {
      Collection<String> suggestedNames = operation.getSuggestedNames();
      if (!suggestedNames.isEmpty()) {
        operation.setName(suggestedNames.iterator().next());
      }
      else {
        operation.setName("x");
      }
    }
  }

  @RequiredReadAction
  public static @Nullable PsiElement findOccurrenceUnderCaret(List<? extends PsiElement> occurrences, Editor editor) {
    if (occurrences.isEmpty()) {
      return null;
    }
    int offset = editor.getCaretModel().getOffset();
    for (PsiElement occurrence : occurrences) {
      if (occurrence != null && occurrence.getTextRange().contains(offset)) {
        return occurrence;
      }
    }
    int line = editor.getDocument().getLineNumber(offset);
    for (PsiElement occurrence : occurrences) {
      PyPsiUtils.assertValid(occurrence);
      if (occurrence.isValid() && editor.getDocument().getLineNumber(occurrence.getTextRange().getStartOffset()) == line) {
        return occurrence;
      }
    }
    for (PsiElement occurrence : occurrences) {
      PyPsiUtils.assertValid(occurrence);
      return occurrence;
    }
    return null;
  }

  public enum InitPlace {
    SAME_METHOD,
    CONSTRUCTOR,
    SET_UP
  }

  @Nullable
  @RequiredWriteAction
  protected PsiElement replaceExpression(PsiElement expression, PyExpression newExpression, IntroduceOperation operation) {
    PyExpressionStatement statement = PsiTreeUtil.getParentOfType(expression, PyExpressionStatement.class);
    if (statement != null) {
      if (statement.getExpression() == expression && expression.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE) == null) {
        statement.delete();
        return null;
      }
    }
    return PyReplaceExpressionUtil.replaceExpression(expression, newExpression);
  }

  private final IntroduceValidator myValidator;
  protected final LocalizeValue myDialogTitle;

  protected IntroduceHandler(IntroduceValidator validator, LocalizeValue dialogTitle) {
    myValidator = validator;
    myDialogTitle = dialogTitle;
  }

  @Override
  @RequiredUIAccess
  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    performAction(new IntroduceOperation(project, editor, file, null));
  }

  @Override
  @RequiredUIAccess
  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
  }

  @RequiredReadAction
  public Collection<String> getSuggestedNames(PyExpression expression) {
    Collection<String> candidates = generateSuggestedNames(expression);

    Collection<String> res = new ArrayList<>();
    for (String name : candidates) {
      if (myValidator.checkPossibleName(name, expression)) {
        res.add(name);
      }
    }

    if (res.isEmpty()) {  // no available names found, generate disambiguated suggestions
      for (String name : candidates) {
        int index = 1;
        while (!myValidator.checkPossibleName(name + index, expression)) {
          index++;
        }
        res.add(name + index);
      }
    }

    return res;
  }

  @RequiredReadAction
  protected Collection<String> generateSuggestedNames(PyExpression expression) {
    Collection<String> candidates = new LinkedHashSet<>() {
      @Override
      public boolean add(String s) {
        if (PyNames.isReserved(s)) {
          return false;
        }
        return super.add(s);
      }
    };
    String text = PyPsiUtils.getStringValue(expression);
    Pair<PsiElement, TextRange> selection = expression.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    if (selection != null) {
      text = selection.getSecond().substring(selection.getFirst().getText());
    }
    if (expression instanceof PyCallExpression) {
      PyExpression callee = ((PyCallExpression)expression).getCallee();
      if (callee != null) {
        text = callee.getText();
      }
    }
    if (text != null) {
      candidates.addAll(NameSuggesterUtil.generateNames(text));
    }
    TypeEvalContext context = TypeEvalContext.userInitiated(expression.getProject(), expression.getContainingFile());
    PyType type = context.getType(expression);
    if (!isUnknown(type) && !isNoneType(type)) {
      String typeName = type.getName();
      if (typeName != null) {
        if (type instanceof PyLiteralStringType) { // we don't want to suggest "L" for inferred LiteralStrings
          typeName = "s";
        }
        else if (type.isBuiltin()) {
          typeName = typeName.substring(0, 1);
        }
        candidates.addAll(NameSuggesterUtil.generateNamesByType(typeName));
      }
    }
    PyKeywordArgument kwArg = PsiTreeUtil.getParentOfType(expression, PyKeywordArgument.class);
    if (kwArg != null && kwArg.getValueExpression() == expression) {
      candidates.add(kwArg.getKeyword());
    }

    Optional
      .ofNullable(PsiTreeUtil.getParentOfType(expression, PyArgumentList.class))
      .map(PyArgumentList::getCallExpression)
      .ifPresent(
        call -> StreamEx
          .of(call.multiMapArguments(PyResolveContext.defaultContext(context)))
          .map(mapping -> mapping.getMappedParameters().get(expression))
          .nonNull()
          .map(PyCallableParameter::getName)
          .nonNull()
          .forEach(candidates::add)
      );

    return candidates;
  }

  @RequiredUIAccess
  public void performAction(IntroduceOperation operation) {
    PsiFile file = operation.getFile();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(file)) {
      return;
    }
    Editor editor = operation.getEditor();
    if (editor.getSettings().isVariableInplaceRenameEnabled()) {
      TemplateState templateState = TemplateManager.getInstance(operation.getProject()).getTemplateState(operation.getEditor());
      if (templateState != null && !templateState.isFinished()) {
        return;
      }
    }

    PsiElement element1 = null;
    PsiElement element2 = null;
    SelectionModel selectionModel = editor.getSelectionModel();
    boolean singleElementSelection = false;
    if (selectionModel.hasSelection()) {
      element1 = file.findElementAt(selectionModel.getSelectionStart());
      element2 = file.findElementAt(selectionModel.getSelectionEnd() - 1);
      if (element1 instanceof PsiWhiteSpace) {
        int startOffset = element1.getTextRange().getEndOffset();
        element1 = file.findElementAt(startOffset);
      }
      if (element2 instanceof PsiWhiteSpace) {
        int endOffset = element2.getTextRange().getStartOffset();
        element2 = file.findElementAt(endOffset - 1);
      }
      if (element1 == element2) {
        singleElementSelection = true;
      }
    }
    else {
      if (smartIntroduce(operation)) {
        return;
      }
      CaretModel caretModel = editor.getCaretModel();
      Document document = editor.getDocument();
      int lineNumber = document.getLineNumber(caretModel.getOffset());
      if ((lineNumber >= 0) && (lineNumber < document.getLineCount())) {
        element1 = file.findElementAt(document.getLineStartOffset(lineNumber));
        element2 = file.findElementAt(document.getLineEndOffset(lineNumber) - 1);
      }
    }
    Project project = operation.getProject();
    if (element1 == null || element2 == null) {
      showCannotPerformError(project, editor);
      return;
    }

    element1 = PyRefactoringUtil.getSelectedExpression(project, file, element1, element2);
    if (element1 == null || referencesComprehensionIteratorValue(element1)) {
      showCannotPerformError(project, editor);
      return;
    }

    if (singleElementSelection && element1 instanceof PyStringLiteralExpression literal) {
      // Currently introduce for substrings of a multi-part string literals is not supported
      if (literal.getStringNodes().size() > 1) {
        showCannotPerformError(project, editor);
        return;
      }
      int offset = element1.getTextOffset();
      TextRange selectionRange = TextRange.create(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      TextRange elementRange = element1.getTextRange();
      if (!elementRange.equals(selectionRange) && elementRange.contains(selectionRange)) {
        TextRange innerRange = literal.getStringValueTextRange();
        TextRange intersection = selectionRange.shiftRight(-offset).intersection(innerRange);
        TextRange finalRange = intersection != null ? intersection : selectionRange;
        String text = literal.getText();
        if (getFormatValueExpression(literal) != null && breaksStringFormatting(text, finalRange) ||
          getNewStyleFormatValueExpression(literal) != null && breaksNewStyleStringFormatting(text, finalRange) ||
          breaksStringEscaping(text, finalRange)) {
          showCannotPerformError(project, editor);
          return;
        }
        element1.putUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE, Pair.create(element1, finalRange));
      }
    }

    if (!checkIntroduceContext(file, editor, element1)) {
      return;
    }
    operation.setElement(element1);
    performActionOnElement(operation);
  }

  @RequiredReadAction
  private static boolean referencesComprehensionIteratorValue(PsiElement element) {
    PyComprehensionElement comprehension = PsiTreeUtil.getParentOfType(element, PyComprehensionElement.class, true);
    if (comprehension != null) {
      List<PyExpression> iteratorVariables = ContainerUtil.map(comprehension.getForComponents(), it -> it.<PyExpression>getIteratorVariable());
      List<PsiElement> referencedInSelection = collectReferencedDefinitionsInSameFile(element, element.getContainingFile());
      if (iteratorVariables.contains(element) || ContainerUtil.intersects(referencedInSelection, iteratorVariables)) {
        return true;
      }
    }
    return false;
  }

  @RequiredReadAction
  protected static List<PsiElement> collectReferencedDefinitionsInSameFile(PsiElement element, PsiFile file) {
    PsiElement selectionElement = getOriginalSelectionCoveringElement(element);
    TextRange textRange = getTextRangeForOperationElement(element);

    return StreamEx.of(PsiTreeUtil.collectElementsOfType(selectionElement, PyReferenceExpression.class))
      .filter(it -> textRange.contains(it.getTextRange()))
      .filter(ref -> !ref.isQualified())
      .flatMap(expr -> PyResolveUtil.resolveLocally(expr).stream())
      .filter(it -> it != null && it.getContainingFile() == file)
      .toList();
  }

  @RequiredReadAction
  protected static TextRange getTextRangeForOperationElement(PsiElement operationElement) {
    Pair<PsiElement, TextRange> userData = operationElement.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    if (userData == null || userData.first == null || userData.second == null) {
      return operationElement.getTextRange();
    }
    else {
      return userData.second.shiftRight(userData.first.getTextOffset());
    }
  }

  protected static PsiElement getOriginalSelectionCoveringElement(PsiElement operationElement) {
    Pair<PsiElement, TextRange> userData = operationElement.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    return userData == null ? operationElement : userData.first;
  }

  private static boolean breaksStringFormatting(String s, TextRange range) {
    return breaksRanges(substitutionsToRanges(filterSubstitutions(parsePercentFormat(s))), range);
  }

  private static boolean breaksNewStyleStringFormatting(String s, TextRange range) {
    return breaksRanges(substitutionsToRanges(filterSubstitutions(parseNewStyleFormat(s))), range);
  }

  private static boolean breaksStringEscaping(String s, TextRange range) {
    return breaksRanges(getEscapeRanges(s), range);
  }

  private static boolean breaksRanges(List<TextRange> ranges, TextRange range) {
    for (TextRange r : ranges) {
      if (range.contains(r)) {
        continue;
      }
      if (range.intersectsStrict(r)) {
        return true;
      }
    }
    return false;
  }

  @RequiredUIAccess
  private void showCannotPerformError(Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project,
                                        editor,
        PyLocalize.refactoringIntroduceSelectionError(),
                                        myDialogTitle,
                                        "refactoring.extractMethod");
  }

  @RequiredUIAccess
  private boolean smartIntroduce(IntroduceOperation operation) {
    Editor editor = operation.getEditor();
    PsiFile file = operation.getFile();
    int offset = editor.getCaretModel().getOffset();
    PsiElement elementAtCaret = file.findElementAt(offset);
    if ((elementAtCaret instanceof PsiWhiteSpace && offset == elementAtCaret.getTextOffset() || elementAtCaret == null) && offset > 0) {
      elementAtCaret = file.findElementAt(offset - 1);
    }
    if (!checkIntroduceContext(file, editor, elementAtCaret)) {
      return true;
    }
    List<PyExpression> expressions = new ArrayList<>();
    while (elementAtCaret != null) {
      if (elementAtCaret instanceof PyStatement || elementAtCaret instanceof PyFile) {
        break;
      }
      if (elementAtCaret instanceof PyExpression && isValidIntroduceVariant(elementAtCaret)) {
        expressions.add((PyExpression)elementAtCaret);
      }
      elementAtCaret = elementAtCaret.getParent();
    }
    if (expressions.size() == 1 || Application.get().isUnitTestMode()) {
      operation.setElement(expressions.get(0));
      performActionOnElement(operation);
      return true;
    }
    else if (expressions.size() > 1) {
      PyRefactoringUiService.getInstance().showIntroduceTargetChooser(operation, editor, expressions, this::performActionOnElement);
      return true;
    }
    return false;
  }

  @RequiredUIAccess
  protected boolean checkIntroduceContext(PsiFile file, Editor editor, PsiElement element) {
    if (!isValidIntroduceContext(element)) {
      CommonRefactoringUtil.showErrorHint(
          file.getProject(),
          editor,
          PyLocalize.refactoringIntroduceSelectionError(),
          myDialogTitle,
          "refactoring.extractMethod"
      );
      return false;
    }
    return true;
  }

  protected boolean isValidIntroduceContext(PsiElement element) {
    PyDecorator decorator = PsiTreeUtil.getParentOfType(element, PyDecorator.class);
    if (decorator != null && PsiTreeUtil.isAncestor(decorator.getCallee(), element, false)) {
      return false;
    }
    return PsiTreeUtil.getParentOfType(element, PyParameterList.class) == null;
  }

  @RequiredReadAction
  public static boolean isValidIntroduceVariant(PsiElement element) {
    PyCallExpression call = as(element.getParent(), PyCallExpression.class);
    if (call != null && call.getCallee() == element) {
      return false;
    }
    if (referencesComprehensionIteratorValue(element)) {
      return false;
    }
    return true;
  }

  @RequiredUIAccess
  public void performActionOnElement(IntroduceOperation operation) {
    if (!checkEnabled(operation)) {
      showCanNotIntroduceErrorHint(operation.getProject(), operation.getEditor());
      return;
    }
    PsiElement element = operation.getElement();
    PyExpression initializer = getInitializerForElement(element);
    operation.setInitializer(initializer);

    if (initializer != null) {
      operation.setOccurrences(getOccurrences(element, initializer));
      operation.setSuggestedNames(getSuggestedNames(initializer));
    }
    if (operation.getOccurrences().isEmpty()) {
      operation.setReplaceAll(false);
    }

    performActionOnElementOccurrences(operation);
  }

  @RequiredUIAccess
  protected void showCanNotIntroduceErrorHint(Project project, Editor editor) { }

  @RequiredUIAccess
  protected void performActionOnElementOccurrences(final IntroduceOperation operation) {
    Editor editor = operation.getEditor();
    if (editor.getSettings().isVariableInplaceRenameEnabled()) {
      ensureName(operation);
      if (operation.isReplaceAll() != null) {
        performInplaceIntroduce(operation);
      }
      else {
        PyRefactoringUiService.getInstance().showOccurrencesChooser(operation, editor, this::performInplaceIntroduce);
      }
    }
    else {
      PyRefactoringUiService.getInstance().performIntroduceWithDialog(operation, myDialogTitle, myValidator, getHelpId(),
                                                                      o -> {
                                                                        PsiElement declaration = performRefactoring(o);
                                                                        Editor editor1 = o.getEditor();
                                                                        editor1.getCaretModel()
                                                                          .moveToOffset(declaration.getTextRange().getEndOffset());
                                                                        editor1.getSelectionModel().removeSelection();
                                                                      });
    }
  }


  protected @Nullable PyExpression getInitializerForElement(@Nullable PsiElement element) {
    if (element == null) return null;
    PsiElement parent = element.getParent();
    return parent instanceof PyAssignmentStatement ? ((PyAssignmentStatement)parent).getAssignedValue() :
           element instanceof PyExpression ? (PyExpression)element : null;
  }

  @RequiredUIAccess
  protected void performInplaceIntroduce(IntroduceOperation operation) {
    PsiElement statement = performRefactoring(operation);
    PyRefactoringUiService.getInstance().performInplaceIntroduceVariable(operation, statement);
  }

  @RequiredUIAccess
  protected PsiElement performRefactoring(IntroduceOperation operation) {
    PsiElement declaration = createDeclaration(operation);

    declaration = performReplace(declaration, operation);
    declaration = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(declaration);
    return declaration;
  }

  @RequiredReadAction
  public PyAssignmentStatement createDeclaration(IntroduceOperation operation) {
    Project project = operation.getProject();
    PyExpression initializer = operation.getInitializer();
    String assignmentText = operation.getName() + " = " + new InitializerTextBuilder(initializer).result();
    PsiElement anchor =
      operation.isReplaceAll() ? findAnchor(operation.getOccurrences()) : PsiTreeUtil.getParentOfType(initializer, PyStatement.class);
    return createDeclaration(project, assignmentText, anchor);
  }

  private static class InitializerTextBuilder extends PyRecursiveElementVisitor {
    private final StringBuilder myResult = new StringBuilder();
    private final boolean myPreserveFormatting;

    @RequiredReadAction
    InitializerTextBuilder(PyExpression expression) {
      myPreserveFormatting = shouldPreserveFormatting(expression);
      if (PsiTreeUtil.findChildOfType(expression, PsiComment.class) != null) {
        myResult.append(expression.getText());
      }
      else {
        expression.accept(this);
      }
      if (needToWrapTopLevelExpressionInParenthesis(expression)) {
        myResult.insert(0, "(").append(")");
      }
    }

    @Override
    public void visitWhiteSpace(PsiWhiteSpace space) {
      String text = space.getText();
      myResult.append(myPreserveFormatting ? text : text.replace('\n', ' ').replace("\\", ""));
    }

    @RequiredReadAction
    @Override
    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
      Pair<PsiElement, TextRange> data = node.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
      if (data != null) {
        PsiElement parent = data.getFirst();
        String text = parent.getText();
        Pair<String, String> detectedQuotes = PyStringLiteralCoreUtil.getQuotes(text);
        Pair<String, String> quotes = detectedQuotes != null ? detectedQuotes : Pair.create("'", "'");
        TextRange range = data.getSecond();
        String substring = range.substring(text);
        myResult.append(quotes.getFirst()).append(substring).append(quotes.getSecond());
      }
      else {
        ASTNode child = node.getNode().getFirstChildNode();
        while (child != null) {
          String text = child.getText();
          if (child.getElementType() == TokenType.WHITE_SPACE) {
            if (text.contains("\n")) {
              if (!text.contains("\\")) {
                myResult.append("\\");
              }
              myResult.append(text);
            }
          }
          else {
            myResult.append(text);
          }
          child = child.getTreeNext();
        }
      }
    }

    @Override
    @RequiredReadAction
    public void visitElement(PsiElement element) {
      if (element.getChildren().length == 0) {
        myResult.append(element.getText());
      }
      else {
        super.visitElement(element);
      }
    }

    private static boolean shouldPreserveFormatting(PyExpression expression) {
      // A collection literal in brackets
      if (expression instanceof PyParenthesizedExpression) {
        return ((PyParenthesizedExpression)expression).getContainedExpression() instanceof PyTupleExpression;
      }
      return expression instanceof PySequenceExpression && !(expression instanceof PyTupleExpression);
    }

    @RequiredReadAction
    private static boolean needToWrapTopLevelExpressionInParenthesis(PyExpression node) {
      if (node instanceof PyGeneratorExpression) {
        PsiElement firstChild = node.getFirstChild();
        if (firstChild != null && firstChild.getNode().getElementType() != PyTokenTypes.LPAR) {
          return true;
        }
      }
      return false;
    }

    public String result() {
      return myResult.toString();
    }
  }

  protected abstract String getHelpId();

  @RequiredReadAction
  protected PyAssignmentStatement createDeclaration(Project project, String assignmentText, PsiElement anchor) {
    LanguageLevel langLevel = ((PyFile)anchor.getContainingFile()).getLanguageLevel();
    return PyElementGenerator.getInstance(project).createFromText(langLevel, PyAssignmentStatement.class, assignmentText);
  }

  protected boolean checkEnabled(IntroduceOperation operation) {
    return true;
  }

  protected List<PsiElement> getOccurrences(PsiElement element, PyExpression expression) {
    return PyRefactoringUtil.getOccurrences(expression, ScopeUtil.getScopeOwner(expression));
  }

  @RequiredUIAccess
  private PsiElement performReplace(PsiElement declaration,
                                    IntroduceOperation operation) {
    PyExpression expression = operation.getInitializer();
    Project project = operation.getProject();
    SmartPsiElementPointer<PsiElement> result =
      WriteCommandAction.writeCommandAction(expression.getContainingFile()).compute(() -> {
        PsiElement insertedDeclaration;
        try {
          RefactoringEventData afterData = new RefactoringEventData();
          afterData.addElement(declaration);
          project.getMessageBus().syncPublisher(RefactoringEventListener.class)
            .refactoringStarted(getRefactoringId(), afterData);

          insertedDeclaration = addDeclaration(operation, declaration);

          PyExpression newExpression = createExpression(project, operation.getName(), declaration);

          if (operation.isReplaceAll()) {
            List<PsiElement> newOccurrences = new ArrayList<>();
            for (PsiElement occurrence : operation.getOccurrences()) {
              PsiElement replaced = replaceExpression(occurrence, newExpression, operation);
              if (replaced != null) {
                newOccurrences.add(replaced);
              }
            }
            operation.setOccurrences(newOccurrences);
          }
          else {
            PsiElement replaced = replaceExpression(expression, newExpression, operation);
            operation.setOccurrences(Collections.singletonList(replaced));
          }

          postRefactoring(operation.getElement());
        }
        finally {
          RefactoringEventData afterData = new RefactoringEventData();
          afterData.addElement(declaration);
          project.getMessageBus().syncPublisher(RefactoringEventListener.class)
            .refactoringDone(getRefactoringId(), afterData);
        }
        return ObjectUtil.doIfNotNull(insertedDeclaration, SmartPointerManager::createPointer);
      });
    return ObjectUtil.doIfNotNull(result, SmartPsiElementPointer::getElement);
  }

  protected abstract String getRefactoringId();

  @Nullable
  public PsiElement addDeclaration(IntroduceOperation operation, PsiElement declaration) {
    PsiElement expression = operation.getInitializer();
    Pair<PsiElement, TextRange> data = expression.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
    if (data == null) {
      return addDeclaration(expression, declaration, operation);
    }
    else {
      return addDeclaration(data.first, declaration, operation);
    }
  }

  protected PyExpression createExpression(Project project, String name, PsiElement declaration) {
    return PyElementGenerator.getInstance(project).createExpressionFromText(LanguageLevel.forElement(declaration), name);
  }

  @Nullable
  protected abstract PsiElement addDeclaration(PsiElement expression,
                                               PsiElement declaration,
                                               IntroduceOperation operation);

  protected void postRefactoring(PsiElement element) {
  }
}
