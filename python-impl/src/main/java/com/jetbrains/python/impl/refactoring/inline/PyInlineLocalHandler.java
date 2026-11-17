// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.inline;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.psi.PyQuotesUtil;
import com.jetbrains.python.impl.refactoring.PyDefUseUtil;
import com.jetbrains.python.impl.refactoring.PyReplaceExpressionUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.query.Query;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.event.RefactoringEventData;
import consulo.language.editor.refactoring.event.RefactoringEventListener;
import consulo.language.editor.refactoring.inline.InlineActionHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.refactoring.util.RefactoringMessageDialog;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.undoRedo.CommandProcessor;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dennis.Ushakov
 */
@ExtensionImpl
public final class PyInlineLocalHandler extends InlineActionHandler {
  private static final Logger LOG = Logger.getInstance(PyInlineLocalHandler.class.getName());

  private static final Pair<PyStatement, Boolean> EMPTY_DEF_RESULT = Pair.create(null, false);
  private static final String HELP_ID = "refactoring.inlineVariable";

  public static PyInlineLocalHandler getInstance() {
    return EP_NAME.findExtensionOrFail(PyInlineLocalHandler.class);
  }

  @Override
  public boolean isEnabledForLanguage(Language l) {
    return l instanceof PythonLanguage;
  }

  @Override
  public boolean canInlineElement(PsiElement element) {
    return element instanceof PyTargetExpression;
  }

  @Override
  @RequiredUIAccess
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    if (editor == null) {
      return;
    }
    // TODO consulo: TargetElementUtilBase.findReferenceWithoutExpectedCaret missing, TargetElementUtil.findReference used
    final PsiReference psiReference = TargetElementUtil.findReference(editor);
    PyReferenceExpression refExpr = null;
    if (psiReference != null) {
      final PsiElement refElement = psiReference.getElement();
      if (refElement instanceof PyReferenceExpression) {
        refExpr = (PyReferenceExpression)refElement;
      }
    }
    invoke(project, editor, (PyTargetExpression)element, refExpr, false);
  }

  private static boolean stringContentCanBeInlinedIntoFString(PyStringElement inlinedStringElement,
                                                              PyFormattedStringElement targetFString) {
    if (LanguageLevel.forElement(targetFString).isAtLeast(LanguageLevel.PYTHON312)) return true;
    String content = inlinedStringElement.getContent();
    if (targetFString.isTripleQuoted()) {
      return !content.contains("\\");
    }
    return !content.contains("'") && !content.contains("\"") && !content.contains("\\");
  }

  private static List<PyStringElement> getStringElements(PyExpression expression) {
    List<PyStringElement> result = new ArrayList<>();
    Collection<PyStringLiteralExpression> stringLiteralExprs =
      PsiTreeUtil.findChildrenOfAnyType(expression, false, PyStringLiteralExpression.class);
    for (PyStringLiteralExpression stringLiteralExpr : stringLiteralExprs) {
      result.addAll(stringLiteralExpr.getStringElements());
    }
    return result;
  }

  @RequiredWriteAction
  private static PyExpression replaceQuotesInExpression(PyExpression expression, char desiredQuote) {
    PyExpression expressionCopy = (PyExpression)expression.copy();
    List<PyStringElement> valueStringElements = getStringElements(expressionCopy);
    for (PyStringElement valueStringElement : valueStringElements) {
      char actualQuote = valueStringElement.getQuote().charAt(0);
      PyStringElement elementToReplace = valueStringElement;
      if (actualQuote != desiredQuote) {
        elementToReplace = PyQuotesUtil.createCopyWithConvertedQuotes(valueStringElement);
      }
      valueStringElement.replace(elementToReplace);
    }
    return expressionCopy;
  }

  @RequiredUIAccess
  private static boolean checkPossibleInlineElement(PsiElement element, PyExpression value,
                                                    Project project, Editor editor,
                                                    Map<PsiElement, PsiElement> simpleReplacements,
                                                    Map<PyFStringFragment, PyStringElement> fStringFragmentsReplacements) {
    PyFormattedStringElement targetFString = PsiTreeUtil.getParentOfType(element, PyFormattedStringElement.class, true, PyStatement.class);
    if (targetFString == null) {
      simpleReplacements.put(element, value);
      return true;
    }

    boolean fStringCanContainArbitraryStrings = LanguageLevel.forElement(element).isAtLeast(LanguageLevel.PYTHON312);

    if (!fStringCanContainArbitraryStrings && !targetFString.isTripleQuoted() && value.textContains('\n')) {
      CommonRefactoringUtil.showErrorHint(project, editor, PyLocalize.refactoringInlineCanNotMultilineStringToFString().get(),
                                          getRefactoringName(), HELP_ID);
      return false;
    }

    boolean intoNestedFString = PsiTreeUtil.getParentOfType(targetFString, PyFormattedStringElement.class, true, PyStatement.class) != null;
    List<PyStringElement> valueStringElements = getStringElements(value);

    boolean entireValueIsSingleNonInterpolatedString = value instanceof PyStringLiteralExpression && valueStringElements.size() == 1;
    if (entireValueIsSingleNonInterpolatedString && element.getParent() instanceof PyFStringFragment fStringFragment
        && fStringFragment.getTypeConversion() == null && fStringFragment.getFormatPart() == null
        && !(value.textContains('\n') && !targetFString.isTripleQuoted())) {
      if (intoNestedFString && !stringContentCanBeInlinedIntoFString(valueStringElements.get(0), targetFString)) {
        CommonRefactoringUtil.showErrorHint(project, editor,
                                            PyLocalize.refactoringInlineCanNotStringWithBackslashesOrQuotesToFString().get(),
                                            getRefactoringName(), HELP_ID);
        return false;
      }
      PyExpression valueReplacedQuotes = replaceQuotesInExpression(value, targetFString.getQuote().charAt(0));
      List<PyStringElement> stringElements = getStringElements(valueReplacedQuotes);
      if (stringElements.size() == 1) {
        fStringFragmentsReplacements.put(fStringFragment, stringElements.get(0));
        return true;
      }
    }

    if (!fStringCanContainArbitraryStrings && intoNestedFString) {
      CommonRefactoringUtil.showErrorHint(project, editor, PyLocalize.refactoringInlineCanNotStringToNestedFString().get(),
                                          getRefactoringName(), HELP_ID);
      return false;
    }

    for (PyStringElement valueStringElement : valueStringElements) {
      if (!stringContentCanBeInlinedIntoFString(valueStringElement, targetFString)) {
        String message = PyLocalize.refactoringInlineCanNotStringWithBackslashesOrQuotesToFString().get();
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
        return false;
      }
    }

    char newQuote = PyStringLiteralUtil.flipQuote(targetFString.getQuote().charAt(0));
    PsiElement quoteSafeValue = fStringCanContainArbitraryStrings ? value.copy() : replaceQuotesInExpression(value, newQuote);
    simpleReplacements.put(element, quoteSafeValue);
    return true;
  }

  @RequiredWriteAction
  private static void makeFStringFragmentsReplacements(Map<PyFStringFragment, PyStringElement> fStringFragmentsReplacements) {
    MultiMap<PyFormattedStringElement, Pair<PyFStringFragment, String>> fString2Replacements = new MultiMap<>();

    for (Map.Entry<PyFStringFragment, PyStringElement> entry : fStringFragmentsReplacements.entrySet()) {
      PyFStringFragment fStringFragment = entry.getKey();
      PyFormattedStringElement fString = (PyFormattedStringElement)fStringFragment.getParent();
      String valueProperQuotes = entry.getValue().getContent();
      fString2Replacements.putValue(fString, Pair.create(fStringFragment, valueProperQuotes));
    }

    List<PyFormattedStringElement> fStrings = new ArrayList<>(fString2Replacements.keySet());
    fStrings.sort(Comparator.comparingInt(it -> -it.getTextOffset()));

    for (PyFormattedStringElement fString : fStrings) {
      Collection<Pair<PyFStringFragment, String>> replacements = fString2Replacements.get(fString);
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(fString.getProject());

      List<Pair<TextRange, String>> replacementsSegments =
        ContainerUtil.sorted(ContainerUtil.map(replacements, it -> Pair.create(it.first.getTextRangeInParent(), it.second)),
                             Comparator.comparingInt(it -> -it.first.getStartOffset()));

      StringBuilder elementStringBuilder = new StringBuilder(fString.getText());
      for (Pair<TextRange, String> segment : replacementsSegments) {
        elementStringBuilder.replace(segment.first.getStartOffset(), segment.first.getEndOffset(), segment.second);
      }

      String resultString = elementStringBuilder.toString();

      PyStringElement elementToReplace = (PyStringElement)elementGenerator.createStringLiteralAlreadyEscaped(resultString).getFirstChild();
      fString.replace(elementToReplace);
    }
  }

  @RequiredUIAccess
  public static void invoke(
    final Project project,
    final Editor editor,
    final PyTargetExpression local,
    @Nullable PyReferenceExpression refExpr,
    boolean replaceJustOneOccurrence
  ) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, local)) return;

    final HighlightManager highlightManager = HighlightManager.getInstance(project);

    final String localName = local.getName();
    final ScopeOwner containerBlock = getContext(local);
    LOG.assertTrue(containerBlock != null);


    final Pair<PyStatement, Boolean> defPair = getAssignmentToInline(containerBlock, refExpr, local, project);
    final PyStatement def = defPair.first;
    if (def == null || getValue(def) == null) {
      final String key = defPair.second ? "variable.has.no.dominating.definition" : "variable.has.no.initializer";
      final String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message(key, localName));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
      return;
    }

    if (def instanceof PyAssignmentStatement && ((PyAssignmentStatement)def).getTargets().length > 1) {
      highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{def}, EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES, true, null);
      final String message =
        RefactoringBundle.getCannotRefactorMessage(PyLocalize.refactoringInlineLocalMultiassignment().get());
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
      return;
    }

    PsiElement[] refsToInline;
    if (replaceJustOneOccurrence && refExpr != null) {
      refsToInline = new PsiElement[]{refExpr};
    }
    else {
      refsToInline = PyDefUseUtil.getPostRefs(containerBlock, local, getObject(def));
    }
    if (refsToInline.length == 0) {
      final String message = RefactoringBundle.message("variable.is.never.used", localName);
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
      return;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      highlightManager.addOccurrenceHighlights(editor, refsToInline, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
      final int occurrencesCount = refsToInline.length;
      final String occurrencesString = RefactoringBundle.message("occurrences.string", occurrencesCount);
      final String question = RefactoringBundle.message("inline.local.variable.prompt", localName) + " " + occurrencesString;
      // TODO consulo: RefactoringUiService.showRefactoringMessageDialog missing, RefactoringMessageDialog used
      RefactoringMessageDialog dialog =
        new RefactoringMessageDialog(getRefactoringName(), question, HELP_ID, "OptionPane.questionIcon", true, project);
      boolean result = dialog.showAndGet();
      if (!result) {
        // TODO consulo: StatusBar.setInfo missing; JB: WindowManager.getInstance().getStatusBar(project)
        //  .setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"))
        return;
      }
    }

    final PsiFile workingFile = local.getContainingFile();
    for (PsiElement ref : refsToInline) {
      final PsiFile otherFile = ref.getContainingFile();
      if (!otherFile.equals(workingFile)) {
        final String message = RefactoringBundle.message("variable.is.referenced.in.multiple.files", localName);
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
        return;
      }
    }

    for (final PsiElement ref : refsToInline) {
      final List<PsiElement> elems = new ArrayList<>();
      final List<Instruction> latestDefs =
        PyDefUseUtil.getLatestDefs(containerBlock, local.getName(), ref, false, false, TypeEvalContext.codeInsightFallback(project)).defs();
      for (Instruction i : latestDefs) {
        elems.add(i.getElement());
      }
      final PsiElement[] defs = elems.toArray(PsiElement.EMPTY_ARRAY);
      boolean isSameDefinition = true;
      for (PsiElement otherDef : defs) {
        isSameDefinition &= isSameDefinition(def, otherDef);
      }
      if (!isSameDefinition) {
        highlightManager.addOccurrenceHighlights(editor, defs, EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES, true, null);
        highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{ref}, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
        final String message = RefactoringBundle.getCannotRefactorMessage(
          RefactoringBundle.message("variable.is.accessed.for.writing.and.used.with.inlined", localName));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
        // TODO consulo: StatusBar.setInfo missing; JB: WindowManager.getInstance().getStatusBar(project)
        //  .setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"))
        return;
      }
    }


    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        final RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(local);
        project.getMessageBus().syncPublisher(RefactoringEventListener.class)
          .refactoringStarted(getRefactoringId(), afterData);

        final List<PsiElement> exprs = new ArrayList<>();
        final PyExpression value = prepareValue(def, localName, project);
        final PsiElement lastChild = def.getLastChild();
        if (lastChild != null && lastChild.getNode().getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) {
          final PsiElement parent = def.getParent();
          if (parent != null) parent.addBefore(lastChild, def);
        }

        // Determine possibility to inline all refs
        // Return if at least one ref is impossible to inline
        Map<PsiElement, PsiElement> simpleReplacements = new HashMap<>();
        Map<PyFStringFragment, PyStringElement> fStringFragmentsReplacements = new HashMap<>();
        for (PsiElement refToInline : refsToInline) {
          if (!checkPossibleInlineElement(refToInline, value, project, editor, simpleReplacements, fStringFragmentsReplacements)) {
            return;
          }
        }

        final LanguageLevel level = LanguageLevel.forElement(value);
        for (Map.Entry<PsiElement, PsiElement> entry : simpleReplacements.entrySet()) {
          PsiElement replElement = entry.getKey();
          PsiElement replValue = entry.getValue();
          if (PyReplaceExpressionUtil.isNeedParenthesis((PyExpression)replElement, (PyExpression)replValue)) {
            replValue = PyElementGenerator.getInstance(project).createExpressionFromText(level, "(" + replValue.getText() + ")");
          }
          exprs.add(replElement.replace(replValue));
        }
        makeFStringFragmentsReplacements(fStringFragmentsReplacements);

        if (!replaceJustOneOccurrence) {
          final PsiElement next = def.getNextSibling();
          if (next instanceof PsiWhiteSpace) {
            PyPsiUtils.removeElements(next);
          }
          PyPsiUtils.removeElements(def);
        }

        final List<TextRange> ranges = ContainerUtil.mapNotNull(exprs, element -> {
          final PyStatement parentalStatement = PsiTreeUtil.getParentOfType(element, PyStatement.class, false);
          return parentalStatement != null ? parentalStatement.getTextRange() : null;
        });
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        CodeStyleManager.getInstance(project).reformatText(workingFile, ranges);

        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          highlightManager.addOccurrenceHighlights(editor, exprs.toArray(PsiElement.EMPTY_ARRAY), EditorColors.SEARCH_RESULT_ATTRIBUTES,
                                                   true, null);
          // TODO consulo: StatusBar.setInfo missing; JB: WindowManager.getInstance().getStatusBar(project)
          //  .setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"))
        }
      }
      finally {
        final RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(local);
        project.getMessageBus().syncPublisher(RefactoringEventListener.class)
          .refactoringDone(getRefactoringId(), afterData);
      }
    }), RefactoringBundle.message("inline.command", localName), null);
  }

  private static boolean isSameDefinition(PyStatement def, PsiElement otherDef) {
    if (otherDef instanceof PyTargetExpression) otherDef = otherDef.getParent();
    return otherDef == def;
  }

  private static ScopeOwner getContext(PyTargetExpression local) {
    ScopeOwner context = PsiTreeUtil.getParentOfType(local, PyFunction.class);
    if (context == null) {
      context = PsiTreeUtil.getParentOfType(local, PyClass.class);
    }
    if (context == null) {
      context = (PyFile)local.getContainingFile();
    }
    return context;
  }

  private static Pair<PyStatement, Boolean> getAssignmentToInline(ScopeOwner containerBlock, @Nullable PyReferenceExpression expr,
                                                                  PyTargetExpression local, Project project) {
    if (expr != null) {
      try {
        final List<Instruction> candidates =
          PyDefUseUtil.getLatestDefs(containerBlock, local.getName(), expr, true, true, TypeEvalContext.codeInsightFallback(project)).defs();
        if (candidates.size() == 1) {
          final PyStatement expression = getAssignmentByLeftPart((PyElement)candidates.get(0).getElement());
          return Pair.create(expression, false);
        }
        return Pair.create(null, !candidates.isEmpty());
      }
      catch (PyDefUseUtil.InstructionNotFoundException ignored) {
      }
    }
    final Query<PsiReference> query = ReferencesSearch.search(local, GlobalSearchScope.allScope(project), false);
    final PsiReference first = query.findFirst();

    final PyElement lValue = first != null ? (PyElement)first.resolve() : null;
    return lValue != null ? Pair.create(getAssignmentByLeftPart(lValue), false) : EMPTY_DEF_RESULT;
  }

  private static @Nullable PyStatement getAssignmentByLeftPart(PyElement candidate) {
    final PsiElement parent = candidate.getParent();
    return parent instanceof PyAssignmentStatement || parent instanceof PyAugAssignmentStatement ? (PyStatement)parent : null;
  }

  private static @Nullable PyExpression getValue(@Nullable PyStatement def) {
    if (def == null) return null;
    if (def instanceof PyAssignmentStatement) {
      return ((PyAssignmentStatement)def).getAssignedValue();
    }
    return ((PyAugAssignmentStatement)def).getValue();
  }

  private static @Nullable PyExpression getObject(@Nullable PyStatement def) {
    if (def == null) return null;
    if (def instanceof PyAssignmentStatement) {
      return ((PyAssignmentStatement)def).getTargets()[0];
    }
    return ((PyAugAssignmentStatement)def).getTarget();
  }

  @RequiredReadAction
  private static PyExpression prepareValue(PyStatement def, String localName, Project project) {
    final PyExpression value = getValue(def);
    assert value != null;
    if (def instanceof PyAugAssignmentStatement expression) {
      final PsiElement operation = expression.getOperation();
      assert operation != null;
      final String op = operation.getText().replace('=', ' ');
      final LanguageLevel level = LanguageLevel.forElement(value);
      return PyElementGenerator.getInstance(project).createExpressionFromText(level, localName + " " + op + value.getText() + ")");
    }
    return value;
  }

  public static String getRefactoringId() {
    return "refactoring.python.inline.local";
  }

  private static String getRefactoringName() {
    return RefactoringBundle.message("inline.variable.title");
  }
}
