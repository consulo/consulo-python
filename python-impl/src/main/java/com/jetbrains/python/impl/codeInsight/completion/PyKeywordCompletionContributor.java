// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.PyUnindentingInsertHandler;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.mlcompletion.PyCompletionMlElementInfo;
import com.jetbrains.python.impl.codeInsight.mlcompletion.PyCompletionMlElementKind;
import com.jetbrains.python.impl.documentation.doctest.PyDoctestFile;
import com.jetbrains.python.impl.inspections.PyLazyImportInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyCaseClause;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyConditionalStatementPart;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElsePart;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFinallyPart;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyLoopStatement;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTryExceptStatement;
import com.jetbrains.python.psi.PyTryPart;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyWithItem;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.editor.completion.lookup.TailTypeDecorator;
import consulo.language.impl.file.FreeThreadedFileViewProvider;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.FilterPattern;
import consulo.language.pattern.PatternCondition;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.OuterLanguageElement;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import org.jspecify.annotations.Nullable;

import static consulo.language.pattern.PlatformPatterns.psiElement;
import static consulo.language.pattern.PlatformPatterns.psiFile;
import static consulo.language.pattern.StandardPatterns.not;
import static consulo.language.pattern.StandardPatterns.or;

/**
 * Python keyword completion contributor.
 * <br/>
 * <b>NOTE: many matchers here use original tree, not the grafted identifier in the copied tree.</b>
 * This should not lead to any problems, because all such code is private, and the user data used to pass the original
 * element is cleared after matching.
 */
@ExtensionImpl(order = "after PyDictKeyNames")
@SuppressWarnings({"InstanceVariableOfConcreteClass"})
public final class PyKeywordCompletionContributor extends CompletionContributor implements DumbAware {
    /**
     * Matches places where a keyword-based statement might be appropriate.
     */
    private static class StatementFitFilter implements ElementFilter {

        StatementFitFilter() {
        }


        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
            if (element instanceof PsiElement p) {
                ASTNode ctxNode = context.getNode();
                if (ctxNode != null && PyTokenTypes.STRING_NODES.contains(ctxNode.getElementType())) {
                    return false; // no sense inside string
                }
                int firstOffset = p.getTextRange().getStartOffset();
                // we must be a stmt ourselves, not a part of another stmt
                // try to climb to the stmt level with the same offset
                while (true) {
                    if (p == null) {
                        return false;
                    }
                    if (p.getTextRange().getStartOffset() != firstOffset) {
                        return false;
                    }
                    if (p instanceof PyStatement) {
                        break;
                    }
                    p = p.getParent();
                }
                // so, a stmt begins with us
                // isn't there an incorrect stmt before us on the same line?
                PsiElement container = p.getParent();
                if (!(container instanceof PyElement)) {
                    return true;
                }
                if (container instanceof PyStatementList || container instanceof PsiFile) {
                    PsiElement prev = p.getPrevSibling();
                    while (prev instanceof PsiWhiteSpace) {
                        prev = prev.getPrevSibling();
                    }
                    if (prev == null) {
                        return true; // there was only whitespace before us
                    }
                    if (prev instanceof PyStatement ||
                        prev instanceof PsiComment ||
                        prev instanceof OuterLanguageElement) { // a non-stmt would be something strange
                        if (prev.getLastChild() instanceof PsiErrorElement) {
                            // prev stmt ends with an error. are we on the same line?
                            PsiDocumentManager docMgr = PsiDocumentManager.getInstance(p.getProject());
                            Document doc = docMgr.getDocument(p.getContainingFile().getOriginalFile());
                            if (doc != null) {
                                if (doc.getLineNumber(prev.getTextRange().getEndOffset()) == doc.getLineNumber(firstOffset)) {
                                    return false; // same line
                                }
                            }
                        }
                        return true; // we follow a well-formed stmt
                    }
                }
            }
            return false;
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
            return true; // can't tell outright
        }
    }

    /* Note: conserved just in case.
     * Matches if before our element, skipping whitespace but on the same line, comes given string.
     * The string not necessarily begins at the start of a PSI element (and may span several).
     * Note that matching against newlines is possible
     */
    /*
    private static class RightAfterFilter implements ElementFilter {
        String myText;

        public RightAfterFilter(String text) {
            myText = text;
        }

        public boolean isAcceptable(Object what, PsiElement context) {
            if (!(what instanceof PsiElement)) {
                return false;
            }
            PsiElement p = (PsiElement)what;
            p = p.getUserData(ORG_ELT); // saved by fillCompletionVariants().
            if (p == null) {
                return false; // just in case
            }
            PsiElement feeler = p;
            while (true) { // skip all whitespace to the left but the last
                PsiElement seeker = feeler.getPrevSibling();
                if (!(seeker instanceof PsiWhiteSpace) || seeker.getText().indexOf('\n') < 0) {
                    break;
                }
                else {
                    feeler = seeker;
                }
            }
            int endpoint = feeler.getTextOffset()-1;
            PsiDocumentManager docMgr = PsiDocumentManager.getInstance(p.getProject());
            Document doc = docMgr.getDocument(p.getContainingFile());
            if (doc != null) {
                if (myText.equals(doc.getCharsSequence().subSequence(endpoint - myText.length(), endpoint).toString())) {
                    return true;
                }
            }
            return false;
        }

        public boolean isClassAcceptable(Class hintClass) {
            return true;
        }
    }
    */

    /**
     * Matches if an element has nothing but whitespace to the left of it, up to a newline or BOF.
     * NOTE: if lexer detected indents and/or EOLs as separate entities, this filter would not be needed, or would trivially work with PSI.
     */
    private static class StartOfLineFilter implements ElementFilter {
        @Override
        public boolean isAcceptable(Object what, PsiElement context) {
            if (!(what instanceof PsiElement p)) {
                return false;
            }
            if (p instanceof PsiComment) {
                return false; // just in case
            }
            int point = p.getTextOffset();
            PsiDocumentManager docMgr = PsiDocumentManager.getInstance(p.getProject());
            PsiFile file = p.getContainingFile().getOriginalFile();
            Document doc = docMgr.getDocument(file);
            String indentCharacters = file.getViewProvider() instanceof FreeThreadedFileViewProvider ? " \t>" : " \t";

            if (doc != null) {
                CharSequence chs = doc.getCharsSequence();
                char c;
                do { // scan to the left for a EOL
                    point -= 1;
                    if (point < 0) {
                        return true; // we're at BOF
                    }
                    c = chs.charAt(point);
                    if (c == '\n') {
                        return true;
                    }
                }
                while (indentCharacters.indexOf(c) >= 0);
            }
            return false;
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
            return true;
        }
    }

    private static class LanguageLevelAtLeastFilter implements ElementFilter {
        private LanguageLevel myLevel;

        LanguageLevelAtLeastFilter(LanguageLevel level) {
            myLevel = level;
        }

        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
            if (!(element instanceof PsiElement)) {
                return false;
            }
            PsiFile containingFile = ((PsiElement)element).getContainingFile();
            return containingFile instanceof PyFile && ((PyFile)containingFile).getLanguageLevel().isAtLeast(myLevel);
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
            return true;
        }
    }


    // ====== conditions

    private static final PsiElementPattern.Capture<PsiElement> IN_COMMENT =
      psiElement().inside(PsiComment.class);

    private static final PsiElementPattern.Capture<PsiElement> IN_STRING_LITERAL =
      psiElement().inside(PyStringLiteralExpression.class).andNot(
        psiElement().inFile(psiFile(PyDoctestFile.class)));

    private static final ElementPattern<PsiElement> IS_DECLARATION_NAME = or(
      psiElement().withElementType(PyTokenTypes.IDENTIFIER).withParent(PyFunction.class),
      psiElement().withElementType(PyTokenTypes.IDENTIFIER).withParent(PyClass.class),
      psiElement().withElementType(PyTokenTypes.IDENTIFIER).withParent(PyTypeAliasStatement.class)
    );

    public static final PsiElementPattern.Capture<PsiElement> AFTER_QUALIFIER =
      psiElement().afterLeaf(psiElement().withText(".").inside(PyReferenceExpression.class));

    public static final PsiElementPattern.Capture<PsiElement> TARGET_AFTER_QUALIFIER =
      psiElement().afterLeaf(psiElement().withText(".").inside(PyTargetExpression.class));

    public static final FilterPattern FIRST_ON_LINE = new FilterPattern(new StartOfLineFilter());

    private static final PsiElementPattern.Capture<PsiElement> IN_IMPORT_AFTER_REF =
      psiElement()
        .afterLeaf(psiElement().withElementType(PyTokenTypes.IDENTIFIER).inside(PyReferenceExpression.class).inside(PyImportElement.class));

    public static final PsiElementPattern.Capture<PsiElement> IN_FROM_IMPORT_AFTER_REF =
      psiElement().afterLeaf(
        or(psiElement().withElementType(PyTokenTypes.IDENTIFIER).inside(PyReferenceExpression.class),
           psiElement().with(new PatternCondition<>("dotFollowedByWhitespace") {
               @Override
               public boolean accepts(PsiElement element, ProcessingContext context) {
                   return element.getNode().getElementType() == PyTokenTypes.DOT && element.getNextSibling() instanceof PsiWhiteSpace;
               }
           }))
      ).inside(PyFromImportStatement.class);

    public static final ElementPattern<PsiElement> IN_WITH_AFTER_REF =
      psiElement().afterLeaf(psiElement().inside(psiElement(PyWithItem.class).with(new PatternCondition<>("withoutAsKeyword") {
          @Override
          public boolean accepts(PyWithItem item, ProcessingContext context) {
              return item.getNode().findChildByType(PyTokenTypes.AS_KEYWORD) == null;
          }
      })));

    private static final PsiElementPattern.Capture<PsiElement> IN_EXCEPT_AFTER_REF =
      psiElement().afterLeaf(psiElement()
                               .withElementType(PyTokenTypes.IDENTIFIER)
                               .inside(PyReferenceExpression.class)
                               .inside(PyExceptPart.class)
      );

    private static final PsiElementPattern.Capture<PsiElement> IN_COND_STMT =
      psiElement().inside(psiElement(PyStatementList.class).inside(psiElement(PyConditionalStatementPart.class)));

    private static final PsiElementPattern.Capture<PsiElement> IN_IF_BODY =
      psiElement().inside(psiElement(PyStatementList.class).inside(psiElement(PyIfPart.class)));

    private static final PsiElementPattern.Capture<PsiElement> IN_LOOP =
      psiElement().inside(false, psiElement(PyLoopStatement.class), or(psiElement(PyFunction.class), psiElement(PyClass.class)));

    // not exactly a beauty
    private static final PsiElementPattern.Capture<PsiElement> BEFORE_COND =
      psiElement()
        .inside(PyConditionalStatementPart.class)
        .andOr(
          psiElement().afterLeaf(psiElement().withText(PyNames.IF)),
          psiElement().afterLeaf(psiElement().withText(PyNames.ELIF)),
          psiElement().afterLeaf(psiElement().withText(PyNames.WHILE))
        );

    private static final PsiElementPattern.Capture<PsiElement> IN_IMPORT_STMT =
      psiElement().inside(
        or(psiElement(PyImportStatement.class), psiElement(PyFromImportStatement.class))
      );

    private static final PsiElementPattern.Capture<PsiElement> IN_PARAM_LIST = psiElement().inside(PyParameterList.class);
    private static final PsiElementPattern.Capture<PsiElement> IN_ARG_LIST = psiElement().inside(PyArgumentList.class);

    private static final PsiElementPattern.Capture<PsiElement> IN_DEF_BODY =
      psiElement().inside(false, psiElement(PyFunction.class), psiElement(PyClass.class));

    private static final PsiElementPattern.Capture<PsiElement> IN_TRY_BODY =
      psiElement().inside(psiElement(PyStatementList.class).inside(psiElement(PyTryPart.class)));

    private static final PsiElementPattern.Capture<PsiElement> IN_EXCEPT_BODY =
      psiElement().inside(psiElement(PyStatementList.class).inside(psiElement(PyExceptPart.class)));

    private static final PsiElementPattern.Capture<PsiElement> IN_ELSE_BODY_OF_TRY =
      psiElement().inside(psiElement(PyStatementList.class).inside(psiElement(PyElsePart.class).inside(PyTryExceptStatement.class)));

    public static final ElementPattern<PsiElement> IN_PATTERN =
      or(psiElement().inside(false, psiElement(PyPattern.class), psiElement(PyStatement.class)),
         psiElement().inside(true, psiElement(PyCaseClause.class), psiElement(PyStatement.class))
           .andNot(psiElement().inside(false, psiElement(PyExpression.class), psiElement(PyCaseClause.class))));

    private static final PsiElementPattern.Capture<PsiElement> AFTER_IF = afterStatement(psiElement(PyIfStatement.class).withLastChild(
      psiElement(PyIfPart.class)));
    private static final PsiElementPattern.Capture<PsiElement> AFTER_TRY = afterStatement(psiElement(PyTryExceptStatement.class));

    private static final PsiElementPattern.Capture<PsiElement> AFTER_LOOP_NO_ELSE =
      afterStatement(psiElement(PyLoopStatement.class).withLastChild(not(psiElement(PyElsePart.class))));

    private static final PsiElementPattern.Capture<PsiElement> AFTER_COND_STMT_NO_ELSE =
      afterStatement(psiElement().withChild(psiElement(PyConditionalStatementPart.class))
                       .withLastChild(not(psiElement(PyElsePart.class))));

    private static <T extends PsiElement> PsiElementPattern.Capture<PsiElement> afterStatement(PsiElementPattern.Capture<T> statementPattern) {
        return psiElement().atStartOf(psiElement(PyExpressionStatement.class)
                                        .afterSiblingSkipping(psiElement().whitespaceCommentEmptyOrError(), statementPattern));
    }

    private static final PsiElementPattern.Capture<PsiElement> AFTER_EXCEPT = afterStatement(
      psiElement().withLastChild(psiElement(PyExceptPart.class))
    );

    private static final PsiElementPattern.Capture<PsiElement> AFTER_FINALLY = afterStatement(
      psiElement().withLastChild(psiElement(PyFinallyPart.class))
    );

    private static final PsiElementPattern.Capture<PsiElement> AFTER_ELSE = afterStatement(
      psiElement().withLastChild(psiElement(PyElsePart.class))
    );

    private static final PsiElementPattern.Capture<PsiElement> IN_FINALLY_NO_LOOP =
      psiElement().inside(false, psiElement(PyFinallyPart.class), psiElement(PyLoopStatement.class));

    private static final FilterPattern IN_BEGIN_STMT = new FilterPattern(new StatementFitFilter());

    private static final PsiElementPattern.Capture<PsiElement> IS_TYPE_PARAMETER_NAME =
      psiElement().withElementType(PyTokenTypes.IDENTIFIER).withParent(PyTypeParameter.class);

    private static final ElementPattern<PsiElement> IS_PARAMETER_NAME =
      psiElement().withElementType(PyTokenTypes.IDENTIFIER).withParent(PyParameter.class);


    /*
    private static final FilterPattern INSIDE_EXPR = new FilterPattern(new PrecededByFilter(
      psiElement(PyExpression.class)
    ));

    private static final FilterPattern INSIDE_EXPR_AFTER_IF = new FilterPattern(new PrecededByFilter(
      psiElement(PyExpression.class).afterLeaf("if")
    ));
    */

    private static final FilterPattern PY3K =
      new FilterPattern(new PyKeywordCompletionContributor.LanguageLevelAtLeastFilter(LanguageLevel.PYTHON30));
    private static final FilterPattern PY35 = new FilterPattern(new LanguageLevelAtLeastFilter(LanguageLevel.PYTHON35));
    private static final FilterPattern PY310 = new FilterPattern(new LanguageLevelAtLeastFilter(LanguageLevel.PYTHON310));
    private static final FilterPattern PY315 = new FilterPattern(new LanguageLevelAtLeastFilter(LanguageLevel.PYTHON315));

    /**
     * Matches places where a {@code lazy} import (PEP 810) is allowed: outside of any function, class,
     * try/except/finally body, or match statement.
     */
    private static class ValidLazyImportContextFilter implements ElementFilter {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
            if (!(element instanceof PsiElement p)) {
                return false;
            }
            return PsiTreeUtil.getParentOfType(p, PyLazyImportInspection.FORBIDDEN_LAZY_IMPORT_CONTAINERS) == null;
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
            return true;
        }
    }

    private static final FilterPattern VALID_LAZY_IMPORT_CONTEXT = new FilterPattern(new ValidLazyImportContextFilter());

    // ======

    private static void putKeywords(CompletionResultSet result, TailType tail, String ... words) {
        for (String s : words) {
            PythonLookupElement lookupElement = new PythonLookupElement(s, true, null);
            lookupElement.putUserData(PyCompletionMlElementInfo.getKey(), PyCompletionMlElementKind.KEYWORD.asInfo());
            result.addElement(TailTypeDecorator.withTail(lookupElement, tail));
        }
    }

    private static void putKeyword(String keyword, InsertHandler<PythonLookupElement> handler, TailType tail,
                                   CompletionResultSet result) {
        PythonLookupElement lookupElement = new PythonLookupElement(keyword, true, null);
        lookupElement.setHandler(handler);
        lookupElement.putUserData(PyCompletionMlElementInfo.getKey(), PyCompletionMlElementKind.KEYWORD.asInfo());
        result.addElement(TailTypeDecorator.withTail(lookupElement, tail));
    }

    private static final InsertHandler<PythonLookupElement> TRAILING_SPACE_INSERT_HANDLER =
      new InsertHandler<>() {
          @Override
          public void handleInsert(InsertionContext context, PythonLookupElement item) {
              TailType.SPACE.processTail(context.getEditor(), context.getTailOffset());
          }
      };

    private void addPreColonStatements() {
        PsiElementPattern.Capture<PsiElement> startOfLine = psiElement()
          .withLanguage(PythonLanguage.getInstance())
          .and(FIRST_ON_LINE)
          .andNot(IN_IMPORT_STMT)
          .andNot(IN_PARAM_LIST)
          .andNot(IN_ARG_LIST)
          .andNot(BEFORE_COND)
          .andNot(AFTER_QUALIFIER)
          .andNot(IN_STRING_LITERAL);

        extend(
          CompletionType.BASIC,
          startOfLine,
          new CompletionProvider() {
              @Override
              @RequiredReadAction
              public void addCompletions(
                CompletionParameters parameters,
                ProcessingContext context,
                CompletionResultSet result
              ) {
                  putKeywords(result, TailType.NONE, PyNames.DEF, PyNames.CLASS, PyNames.FOR, PyNames.IF, PyNames.WHILE, PyNames.WITH);
                  putKeywords(result, TailType.CASE_COLON, PyNames.TRY);
              }
          }
        );

        extend(
          CompletionType.BASIC,
          startOfLine.and(PY310),
          new CompletionProvider() {
              @Override
              @RequiredReadAction
              public void addCompletions(
                CompletionParameters parameters,
                ProcessingContext context,
                CompletionResultSet result
              ) {
                  putKeywords(result, TailType.NONE, PyNames.MATCH);
              }
          }
        );
    }

    private void addStatements() {
        PsiElementPattern.Capture<PsiElement> inStatement = psiElement()
          .withLanguage(PythonLanguage.getInstance())
          .and(IN_BEGIN_STMT)
          .andNot(IN_IMPORT_STMT)
          .andNot(IN_PARAM_LIST)
          .andNot(IN_ARG_LIST)
          .andNot(BEFORE_COND)
          .andNot(AFTER_QUALIFIER)
          .andNot(IN_STRING_LITERAL);

        extend(
          CompletionType.BASIC,
          inStatement,
          new CompletionProvider() {
              @Override
              @RequiredReadAction
              public void addCompletions(
                CompletionParameters parameters,
                ProcessingContext context,
                CompletionResultSet result
              ) {
                  putKeywords(result, TailType.SPACE, PyNames.ASSERT, PyNames.DEL, PyNames.EXEC, PyNames.FROM, PyNames.IMPORT,
                              PyNames.RAISE);
                  putKeywords(result, TailType.NONE, PyNames.PASS);
              }
          }
        );

        extend(CompletionType.BASIC, inStatement.andNot(PY3K), new PyKeywordCompletionProvider(TailType.SPACE, PyNames.PRINT));
    }

    private void addBreak() {
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .and(IN_BEGIN_STMT)
            .andNot(AFTER_QUALIFIER)
            .andNot(IN_PARAM_LIST)
            .andNot(IN_ARG_LIST)
            .and(IN_LOOP)
          ,
          new PyKeywordCompletionProvider(TailType.NONE, PyNames.BREAK)
        );
    }

    private void addContinue() {
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .and(IN_BEGIN_STMT)
            .andNot(AFTER_QUALIFIER)
            .andNot(IN_PARAM_LIST)
            .andNot(IN_ARG_LIST)
            .andOr(not(IN_FINALLY_NO_LOOP), new FilterPattern(new LanguageLevelAtLeastFilter(LanguageLevel.PYTHON38)))
            .and(IN_LOOP)
          ,
          new PyKeywordCompletionProvider(TailType.NONE, PyNames.CONTINUE)
        );
    }

    private void addCase() {
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .and(PY310)
            .and(IN_BEGIN_STMT)
            .and(psiElement().withSuperParent(4, PyMatchStatement.class)),
          new PyKeywordCompletionProvider(TailType.NONE, PyNames.CASE));
    }

    private void addLazy() {
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .and(PY315)
            .and(IN_BEGIN_STMT)
            .and(VALID_LAZY_IMPORT_CONTEXT)
            .andNot(IN_IMPORT_STMT)
            .andNot(IN_PARAM_LIST)
            .andNot(IN_ARG_LIST)
            .andNot(BEFORE_COND)
            .andNot(AFTER_QUALIFIER)
            .andNot(IN_STRING_LITERAL),
          new PyKeywordCompletionProvider(TailType.SPACE, PyNames.LAZY));
    }

    private void addWithinFuncs() {
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .and(IN_DEF_BODY)
            .and(IN_BEGIN_STMT)
            .andNot(AFTER_QUALIFIER)
          ,
          new PyKeywordCompletionProvider(PyNames.GLOBAL, PyNames.RETURN, PyNames.YIELD)
        );

        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .and(IN_DEF_BODY)
            .and(IN_BEGIN_STMT)
            .and(PY3K)
            .andNot(AFTER_QUALIFIER)
          ,
          new PyKeywordCompletionProvider(PyNames.NONLOCAL)
        );

        extend(CompletionType.BASIC,
               psiElement()
                 .withLanguage(PythonLanguage.getInstance())
                 .and(PY35)
                 .andNot(AFTER_QUALIFIER)
                 .with(new PatternCondition<>("insideAsyncDef") {
                     @Override
                     public boolean accepts(PsiElement element, ProcessingContext context) {
                         ScopeOwner owner = ScopeUtil.getScopeOwner(element);
                         return owner instanceof PyFunction && ((PyFunction)owner).isAsync();
                     }
                 })
                 .andOr(IN_BEGIN_STMT,
                        psiElement()
                          .inside(false, psiElement(PyAssignmentStatement.class), psiElement(PyTargetExpression.class))
                          .afterLeaf(psiElement().withElementType(PyTokenTypes.EQ)),
                        psiElement()
                          .inside(false, psiElement(PyAugAssignmentStatement.class), psiElement(PyTargetExpression.class))
                          .afterLeaf(psiElement().withElementType(PyTokenTypes.AUG_ASSIGN_OPERATIONS)),
                        psiElement().inside(true, psiElement(PyParenthesizedExpression.class))),
               new PyKeywordCompletionProvider(PyNames.AWAIT));
    }

    private void addWithinIf() {
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .and(FIRST_ON_LINE)
            .andOr(IN_IF_BODY, AFTER_IF)
            .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
          ,
          new PyKeywordCompletionProvider(TailType.NONE, PyUnindentingInsertHandler.INSTANCE, PyNames.ELIF));
    }

    private void addWithinTry() {
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .and(FIRST_ON_LINE)
            .andOr(IN_TRY_BODY, IN_EXCEPT_BODY, AFTER_TRY, IN_ELSE_BODY_OF_TRY)
            //.andNot(RIGHT_AFTER_COLON)
            .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
            .andNot(AFTER_FINALLY)
          ,
          new CompletionProvider() {
              @Override
              @RequiredReadAction
              public void addCompletions(
                CompletionParameters parameters,
                ProcessingContext context,
                CompletionResultSet result
              ) {
                  putKeyword(PyNames.FINALLY, PyUnindentingInsertHandler.INSTANCE, TailType.CASE_COLON, result);
              }
          }
        );
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .and(FIRST_ON_LINE)
            .andOr(IN_TRY_BODY, IN_EXCEPT_BODY, AFTER_TRY, IN_ELSE_BODY_OF_TRY)
            //.andNot(RIGHT_AFTER_COLON)
            .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
            .andNot(AFTER_FINALLY).andNot(AFTER_ELSE)
          ,
          new CompletionProvider() {
              @Override
              @RequiredReadAction
              public void addCompletions(
                CompletionParameters parameters,
                ProcessingContext context,
                CompletionResultSet result
              ) {
                  putKeyword(PyNames.EXCEPT, PyUnindentingInsertHandler.INSTANCE, TailType.NONE, result);
              }
          }
        );
    }

    private void addElse() {
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .and(FIRST_ON_LINE)
            .andOr(IN_COND_STMT, IN_EXCEPT_BODY, AFTER_COND_STMT_NO_ELSE, AFTER_LOOP_NO_ELSE, AFTER_EXCEPT)
            .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
          ,
          new PyKeywordCompletionProvider(TailType.CASE_COLON, PyUnindentingInsertHandler.INSTANCE, PyNames.ELSE));
    }

    private void addInfixOperators() {
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .andNot(IN_COMMENT)
            .andNot(BEFORE_COND)
            .andNot(IN_IMPORT_STMT) // expressions there are not logical anyway
            .andNot(IN_PARAM_LIST)
            .andNot(AFTER_QUALIFIER).
            andNot(IN_STRING_LITERAL).and(IN_BEGIN_STMT)
          ,
          new PyKeywordCompletionProvider(PyNames.AND, PyNames.OR, PyNames.IS, PyNames.IN)
        );
    }

    private void addNot() {
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .andNot(IN_COMMENT)
            .andNot(IN_IMPORT_STMT)
            .andNot(IS_PARAMETER_NAME)
            .andNot(IS_TYPE_PARAMETER_NAME)
            .andNot(IS_DECLARATION_NAME)
            .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
            .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL).andNot(TARGET_AFTER_QUALIFIER)
            .andNot(IN_PATTERN)
          ,
          new PyKeywordCompletionProvider(PyNames.NOT, PyNames.LAMBDA)
        );
    }

    private void addPy3kLiterals() {
        // Add literals in normal contexts
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .andNot(IN_COMMENT)
            .andNot(IN_IMPORT_STMT)
            .andNot(IS_PARAMETER_NAME)
            .andNot(IS_TYPE_PARAMETER_NAME)
            .andNot(IS_DECLARATION_NAME)
            .andNot(AFTER_QUALIFIER)
            .andNot(IN_STRING_LITERAL)
            .andNot(TARGET_AFTER_QUALIFIER)
          ,
          new PyKeywordCompletionProvider(TailType.NONE, PyNames.TRUE, PyNames.FALSE, PyNames.NONE));
    }

    private void addAsync() {
        extend(CompletionType.BASIC,
               psiElement()
                 .withLanguage(PythonLanguage.getInstance())
                 .and(PY35)
                 .and(IN_BEGIN_STMT)
                 .andNot(IN_COMMENT)
                 .andNot(IN_IMPORT_STMT)
                 .andNot(IN_PARAM_LIST)
                 .andNot(AFTER_QUALIFIER)
                 .andNot(IN_STRING_LITERAL)
                 .andNot(TARGET_AFTER_QUALIFIER)
                 .andNot(IN_PATTERN),
               new CompletionProvider() {
                   @Override
                   @RequiredReadAction
                   public void addCompletions(
                     CompletionParameters parameters,
                     ProcessingContext context,
                     CompletionResultSet result
                   ) {
                       putKeyword(PyNames.ASYNC + " " + PyNames.DEF, TRAILING_SPACE_INSERT_HANDLER, TailType.NONE, result);
                       putKeyword(PyNames.ASYNC + " " + PyNames.FOR, TRAILING_SPACE_INSERT_HANDLER, TailType.NONE, result);
                       putKeyword(PyNames.ASYNC + " " + PyNames.WITH, TRAILING_SPACE_INSERT_HANDLER, TailType.NONE, result);
                   }
               });
        extend(CompletionType.BASIC,
               psiElement()
                 .withLanguage(PythonLanguage.getInstance())
                 .and(PY35)
                 .and(IN_BEGIN_STMT)
                 .andNot(IN_COMMENT)
                 .andNot(IN_IMPORT_STMT)
                 .andNot(IN_PARAM_LIST)
                 .andNot(AFTER_QUALIFIER)
                 .andNot(IN_STRING_LITERAL)
                 .andNot(TARGET_AFTER_QUALIFIER)
                 .andNot(IN_PATTERN),
               new PyKeywordCompletionProvider(PyNames.ASYNC));
        extend(CompletionType.BASIC,
               psiElement()
                 .withLanguage(PythonLanguage.getInstance())
                 .and(PY35)
                 .afterLeaf(psiElement().withElementType(PyTokenTypes.ASYNC_KEYWORD))
                 .andNot(IN_COMMENT)
                 .andNot(IN_STRING_LITERAL),
               new CompletionProvider() {
                   @Override
                   @RequiredReadAction
                   public void addCompletions(
                     CompletionParameters parameters,
                     ProcessingContext context,
                     CompletionResultSet result
                   ) {
                       putKeywords(result, TailType.NONE, PyNames.DEF, PyNames.WITH, PyNames.FOR);
                       result.stopHere();
                   }
               });
    }

    private void addAs() {
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .andOr(IN_IMPORT_AFTER_REF, IN_WITH_AFTER_REF, IN_EXCEPT_AFTER_REF)
            .andNot(AFTER_QUALIFIER)
            .andNot(IN_COMMENT)
            .andNot(IN_STRING_LITERAL)
          ,
          new PyKeywordCompletionProvider(PyNames.AS)
        );
    }

    private void addImportInFrom() {
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .and(IN_FROM_IMPORT_AFTER_REF)
            .andNot(AFTER_QUALIFIER)
          ,
          new PyKeywordCompletionProvider(PyNames.IMPORT)
        );
    }

    // FIXME: conditions must be severely reworked

    /*
    private void addExprIf() {
        extend(
          CompletionType.BASIC, psiElement()
          .withLanguage(PythonLanguage.getInstance())
          .and(INSIDE_EXPR)
          .andNot(IN_IMPORT_STMT) // expressions there are not logical anyway
            //.andNot(IN_PARAM_LIST)
          .andNot(IN_DEFINITION)
          .andNot(AFTER_QUALIFIER).andNot(IN_STRING_LITERAL)
          ,
          new CompletionProvider<CompletionParameters>() {
              protected void addCompletions(CompletionParameters parameters,
                                            ProcessingContext context,
                                            CompletionResultSet result
              ) {
                  String[] space_strings = {"if"};
                  putKeywords(space_strings, TailType.SPACE, result);
              }
          }
        );
    }
    */

    private void addExprElse() {
        extend(
          CompletionType.BASIC, psiElement()
            .withLanguage(PythonLanguage.getInstance())
            .afterLeafSkipping(psiElement().whitespace(),
                               psiElement().inside(psiElement(PyConditionalExpression.class))
                                 .and(psiElement().afterLeaf(PyNames.IF)))
          ,
          new PyKeywordCompletionProvider(TailType.SPACE, PyNames.ELSE));
    }

    private void addRaiseFrom() {
        extend(CompletionType.BASIC,
               psiElement()
                 .withLanguage(PythonLanguage.getInstance())
                 .and(PY3K)
                 .afterLeaf(psiElement().inside(PyRaiseStatement.class)),
               new PyKeywordCompletionProvider(PyNames.FROM));
    }

    private void addYieldExpression() {
        extend(CompletionType.BASIC,
               psiElement()
                 .withLanguage(PythonLanguage.getInstance())
                 .andOr(psiElement()
                          .inside(false, psiElement(PyAssignmentStatement.class), psiElement(PyTargetExpression.class))
                          .afterLeaf(psiElement().withElementType(PyTokenTypes.EQ)),
                        psiElement()
                          .inside(false, psiElement(PyAugAssignmentStatement.class), psiElement(PyTargetExpression.class))
                          .afterLeaf(psiElement().withElementType(PyTokenTypes.AUG_ASSIGN_OPERATIONS)),
                        psiElement()
                          .inside(true, psiElement(PyParenthesizedExpression.class)))
                 .andNot(IN_STRING_LITERAL)
                 .andNot(IN_COMMENT),
               new PyKeywordCompletionProvider(PyNames.YIELD));
    }

    private void addYieldFrom() {
        extend(CompletionType.BASIC,
               psiElement()
                 .withLanguage(PythonLanguage.getInstance())
                 .and(PY3K)
                 .afterLeaf(psiElement().withElementType(PyTokenTypes.YIELD_KEYWORD)),
               new PyKeywordCompletionProvider(PyNames.FROM));
    }

    public PyKeywordCompletionContributor() {
        addStatements();
        addPreColonStatements();
        addWithinIf();
        addElse();
        addBreak();
        addContinue();
        addCase();
        addLazy();
        addWithinFuncs();
        addWithinTry();
        addInfixOperators();
        addNot();
        addAs();
        addAsync();
        addImportInFrom();
        addPy3kLiterals();
        //addExprIf();
        addExprElse();
        addRaiseFrom();
        addYieldExpression();
        addYieldFrom();
        addForToComprehensions();
        addInToFor();
    }

    private void addForToComprehensions() {
        extend(CompletionType.BASIC,
               psiElement()
                 .withLanguage(PythonLanguage.getInstance())
                 .inside(psiElement(PySequenceExpression.class))
                 .andNot(psiElement()
                           .afterLeaf(or(psiElement(PyTokenTypes.LBRACE), psiElement(PyTokenTypes.LBRACKET), psiElement(PyTokenTypes.LPAR))))
                 .andNot(IN_COMMENT),
               new PyKeywordCompletionProvider(PyNames.FOR));
    }

    private void addInToFor() {
        extend(CompletionType.BASIC,
               psiElement()
                 .withLanguage(PythonLanguage.getInstance())
                 .and(psiElement()).afterLeaf(psiElement().afterLeaf(PyNames.FOR)),
               new PyKeywordCompletionProvider(PyNames.IN));
    }

    private static final class PyKeywordCompletionProvider implements CompletionProvider {
        private final String[] myKeywords;
        private final TailType myTailType;
        private final @Nullable InsertHandler<PythonLookupElement> myInsertHandler;

        private PyKeywordCompletionProvider(String... keywords) {
            this(TailType.SPACE, keywords);
        }

        private PyKeywordCompletionProvider(TailType tailType, String... keywords) {
            this(tailType, null, keywords);
        }

        private PyKeywordCompletionProvider(TailType tailType, @Nullable InsertHandler<PythonLookupElement> insertHandler, String... keywords) {
            myKeywords = keywords;
            myTailType = tailType;
            myInsertHandler = insertHandler;
        }

        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters, ProcessingContext context,
                                         CompletionResultSet result) {
            for (String s : myKeywords) {
                PythonLookupElement element = new PythonLookupElement(s, true, null);
                if (myInsertHandler != null) {
                    element.setHandler(myInsertHandler);
                }
                element.putUserData(PyCompletionMlElementInfo.getKey(), PyCompletionMlElementKind.KEYWORD.asInfo());
                result.addElement(TailTypeDecorator.withTail(element, myTailType));
            }
        }
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
