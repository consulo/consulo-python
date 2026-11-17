// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.module.Module;
import consulo.language.util.ModuleUtilCore;
import consulo.project.Project;
import consulo.localize.LocalizeValue;
import consulo.util.lang.ref.Ref;
import consulo.document.util.TextRange;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.ast.IElementType;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PythonUiService;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.inspections.quickfix.CompatibilityPrintCallQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyRemoveArgumentQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyRemoveUnderscoresInNumericLiteralsQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyReplaceStarByUnpackQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.RemovePrefixQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.RemoveTrailingSuffixQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.ReplaceBackquoteExpressionQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.ReplaceBuiltinsQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.ReplaceExceptPartQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.ReplaceListComprehensionsQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.ReplaceNotEqOperatorQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.ReplaceOctalNumericLiteralQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.ReplaceRaiseStatementQuickFix;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyAssignmentExpression;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyBreakStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyContinueStatement;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDoubleStarExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyEllipsisLiteralExpression;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyFStringFragmentFormatPart;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFinallyPart;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.PyLoopStatement;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyPrintStatement;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyReprExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.PySliceItem;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
  * User : catherine
  */
public abstract class PyCompatibilityVisitor extends PyElementVisitor {

    private static final Set<String> PYTHON2_PREFIXES = Set.of("R", "U", "UR", "B", "BR");

    private static final Set<String> PYTHON34_PREFIXES = Set.of("R", "U", "B", "BR", "RB");

    private static final Set<String> PYTHON36_PREFIXES = Set.of("R", "U", "B", "BR", "RB", "F", "FR", "RF");

    private static final Set<String> PYTHON314_PREFIXES =
      Set.of("R", "U", "B", "BR", "RB", "F", "FR", "RF", "T", "TR", "RT");

    protected final List<LanguageLevel> myVersionsToProcess;

    public PyCompatibilityVisitor(List<LanguageLevel> versionsToProcess) {
        myVersionsToProcess = versionsToProcess;
    }

    @Override
    public void visitPyAnnotation(PyAnnotation node) {
        PsiElement parent = node.getParent();
        if (!(parent instanceof PyFunction || parent instanceof PyNamedParameter)) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON36) && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportVariableAnnotations().get(),
                                           node);
        }
    }

    @Override
    public void visitPyExceptBlock(PyExceptPart node) {
        super.visitPyExceptBlock(node);

        PyExpression exceptClass = node.getExceptClass();
        if (exceptClass != null) {
            PsiElement element = exceptClass.getNextSibling();
            while (element instanceof PsiWhiteSpace) {
                element = element.getNextSibling();
            }

            if (element != null && ",".equals(element.getText())) {
                registerForAllMatchingVersions(
                  level -> level.isPy3K() && level.isOlderThan(LanguageLevel.PYTHON314) && registerForLanguageLevel(level),
                  PyLocalize.inspCompatibilityFeatureSupportThisSyntax().get(),
                  node,
                  new ReplaceExceptPartQuickFix());
            }
        }
        PsiElement star = PyPsiUtils.getFirstChildOfType(node, PyTokenTypes.MULT);
        if (star != null) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON311),
                                           PyLocalize.inspCompatibilityFeatureSupportStarredExceptPart().get(),
                                           star);
        }
    }

    @Override
    public void visitPyImportStatement(PyImportStatement node) {
        super.visitPyImportStatement(node);

        if (node.isLazy()) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON315),
                                           PyLocalize.inspCompatibilityFeatureSupportLazyImports().get(),
                                           node.getFirstChild(),
                                           LocalQuickFix.notNullElements(createInterpreterSettingsQuickFix(node)));
        }

        PyIfStatement ifParent = PsiTreeUtil.getParentOfType(node, PyIfStatement.class);
        if (ifParent != null) {
            return;
        }

        for (PyImportElement importElement : node.getImportElements()) {
            QualifiedName qName = importElement.getImportedQName();

            if (qName != null) {
                if (qName.matches("builtins")) {
                    registerForAllMatchingVersions(level -> level.isPython2() && registerForLanguageLevel(level),
                                                   PyLocalize.inspCompatibilityFeatureHaveModuleBuiltins().get(),
                                                   node,
                                                   new ReplaceBuiltinsQuickFix());
                }
                else if (qName.matches("__builtin__")) {
                    registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                                   PyLocalize.inspCompatibilityFeatureHaveModuleBuiltin().get(),
                                                   node,
                                                   new ReplaceBuiltinsQuickFix());
                }
            }
        }
    }

    @Override
    public void visitPyStarExpression(PyStarExpression node) {
        super.visitPyStarExpression(node);

        if (node.isAssignmentTarget()) {
            registerForAllMatchingVersions(level -> level.isPython2() && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportStarredExpressionsAsAssignmentTargets().get(),
                                           node);
        }

        if (node.isUnpacking()) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportStarredExpressionsInTuplesListsAndSets().get(),
                                           node);
            PsiElement container = PsiTreeUtil.skipParentsOfType(node, PyParenthesizedExpression.class);
            if (container instanceof PyTupleExpression) {
                PsiElement tupleParent = container.getParent();
                if (tupleParent instanceof PyReturnStatement) {
                    registerForAllMatchingVersions(level -> level.isAtLeast(LanguageLevel.PYTHON35) &&
                                                            level.isOlderThan(LanguageLevel.PYTHON38) &&
                                                            registerForLanguageLevel(level),
                                                   PyLocalize.inspCompatibilityFeatureSupportUnpackingWithoutParenthesesInReturnStatements().get(),
                                                   node);
                }

                if (tupleParent instanceof PyYieldExpression && !((PyYieldExpression)tupleParent).isDelegating()) {
                    registerForAllMatchingVersions(level -> level.isAtLeast(LanguageLevel.PYTHON35) &&
                                                            level.isOlderThan(LanguageLevel.PYTHON38) &&
                                                            registerForLanguageLevel(level),
                                                   PyLocalize.inspCompatibilityFeatureSupportUnpackingWithoutParenthesesInYieldStatements().get(),
                                                   node);
                }

                if (tupleParent instanceof PySubscriptionExpression) {
                    registerForAllMatchingVersions(level -> level.isAtLeast(LanguageLevel.PYTHON35) &&
                                                            level.isOlderThan(LanguageLevel.PYTHON311) &&
                                                            registerForLanguageLevel(level),
                                                   PyLocalize.inspCompatibilityFeatureSupportStarredExpressionsInSubscriptions().get(),
                                                   node,
                                                   new PyReplaceStarByUnpackQuickFix());
                }
            }
            if (node.getParent() instanceof PySubscriptionExpression || node.getParent() instanceof PySliceItem) {
                registerForAllMatchingVersions(level -> level.isAtLeast(LanguageLevel.PYTHON35) &&
                                                        level.isOlderThan(LanguageLevel.PYTHON311) &&
                                                        registerForLanguageLevel(level),
                                               PyLocalize.inspCompatibilityFeatureSupportStarredExpressionsInSubscriptions().get(),
                                               node,
                                               new PyReplaceStarByUnpackQuickFix());
            }
        }

        // PEP 798: unpacking in comprehensions/generator expressions, e.g. [*it for it in its]
        if (isComprehensionResultExpression(node)) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON315) && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportUnpackingInComprehensions().get(),
                                           node);
        }

        PsiElement parent = node.getParent();
        if (parent instanceof PyAnnotation && PyStarAnnotatorVisitor.isVariadicArg(parent.getParent())) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON311) &&
                                                    registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportStarredExpressionsInTypeAnnotations().get(),
                                           node);
        }
    }

    @Override
    public void visitPyDoubleStarExpression(PyDoubleStarExpression node) {
        super.visitPyDoubleStarExpression(node);

        // PEP 798: double-star unpacking in dict comprehensions, e.g. {**d for d in dicts}
        if (isComprehensionResultExpression(node)) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON315) && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportUnpackingInComprehensions().get(),
                                           node);
            return;
        }

        registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                       PyLocalize.inspCompatibilityFeatureSupportStarredExpressionsInDicts().get(),
                                       node);
    }

    private static boolean isComprehensionResultExpression(PyExpression node) {
        return node.getParent() instanceof PyComprehensionElement comprehension && comprehension.getResultExpression() == node;
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
        super.visitPyBinaryExpression(node);

        if (node.isOperator("<>")) {
            registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportDiamondOperator().get(),
                                           node,
                                           new ReplaceNotEqOperatorQuickFix());
        }
        else if (node.isOperator("@")) {
            checkMatrixMultiplicationOperator(node.getPsiOperator());
        }

        checkBitwiseOrUnionSyntax(node);
    }

    private void checkMatrixMultiplicationOperator(PsiElement node) {
        registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                       PyLocalize.inspCompatibilityFeatureSupportMatrixMultiplicationOperators().get(),
                                       node);
    }

    @Override
    public void visitPyNumericLiteralExpression(PyNumericLiteralExpression node) {
        super.visitPyNumericLiteralExpression(node);

        String text = node.getText();

        if (node.isIntegerLiteral()) {
            String suffix = node.getIntegerLiteralSuffix();
            if ("l".equalsIgnoreCase(suffix)) {
                registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                               PyLocalize.inspCompatibilityFeatureSupportLongIntegerLiteralSuffix(suffix).get(),
                                               node,
                                               new RemoveTrailingSuffixQuickFix());
            }

            if (text.length() > 1 && text.charAt(0) == '0') {
                char secondChar = Character.toLowerCase(text.charAt(1));
                if (secondChar != 'o' && secondChar != 'b' && secondChar != 'x' && text.chars().anyMatch(c -> c != '0')) {
                    registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                                   PyLocalize.inspCompatibilityFeatureSupportOldStyleOctalLiterals().get(),
                                                   node,
                                                   new ReplaceOctalNumericLiteralQuickFix());
                }
            }
        }

        if (text.contains("_")) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON36) && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportUnderscoresInNumericLiterals().get(),
                                           node,
                                           new PyRemoveUnderscoresInNumericLiteralsQuickFix());
        }
    }

    @Override
    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
        super.visitPyStringLiteralExpression(node);

        boolean seenBytes = false;
        boolean seenNonBytes = false;
        for (PyStringElement element : node.getStringElements()) {
            String prefix = StringUtil.toUpperCase(element.getPrefix());
            if (prefix.isEmpty()) {
                continue;
            }

            boolean bytes = element.isBytes();
            seenBytes |= bytes;
            seenNonBytes |= !bytes;

            int elementStart = element.getTextOffset();
            registerForAllMatchingVersions(level -> !getSupportedStringPrefixes(level).contains(prefix) && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportStringLiteralPrefix(prefix).get(),
                                           node,
                                           TextRange.create(elementStart, elementStart + element.getPrefixLength()),
                                           true,
                                           new RemovePrefixQuickFix(prefix));
        }

        if (seenBytes && seenNonBytes) {
            registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureAllowToMixBytesAndNonBytesLiterals().get(),
                                           node);
        }
    }

    private static Set<String> getSupportedStringPrefixes(LanguageLevel level) {
        if (level.isPython2()) {
            return PYTHON2_PREFIXES;
        }
        else if (level.isOlderThan(LanguageLevel.PYTHON36)) {
            return PYTHON34_PREFIXES;
        }
        else if (level.isOlderThan(LanguageLevel.PYTHON314)) {
            return PYTHON36_PREFIXES;
        }
        else {
            return PYTHON314_PREFIXES;
        }
    }

    @Override
    public void visitPyListCompExpression(PyListCompExpression node) {
        super.visitPyListCompExpression(node);

        for (PyComprehensionForComponent component : node.getForComponents()) {
            registerForAllMatchingVersions(
              level -> registerForLanguageLevel(level) && UnsupportedFeaturesUtil.listComprehensionIteratesOverNonParenthesizedTuple(node, level),
              PyLocalize.inspCompatibilityFeatureSupportThisSyntaxInListComprehensions().get(),
              component.getIteratedList(),
              new ReplaceListComprehensionsQuickFix()
            );
        }
    }

    @Override
    public void visitPyRaiseStatement(PyRaiseStatement node) {
        super.visitPyRaiseStatement(node);

        // empty raise under finally
        registerForAllMatchingVersions(
          level -> registerForLanguageLevel(level) && UnsupportedFeaturesUtil.raiseHasNoArgsUnderFinally(node, level),
          PyLocalize.inspCompatibilityFeatureSupportRaiseWithNoArgumentsOutsideExceptBlock().get(),
          node
        );

        // raise 1, 2, 3
        registerForAllMatchingVersions(level -> registerForLanguageLevel(level) && UnsupportedFeaturesUtil.raiseHasMoreThenOneArg(node, level),
                                       PyLocalize.inspCompatibilityFeatureSupportThisSyntax().get(),
                                       node,
                                       new ReplaceRaiseStatementQuickFix());

        // raise exception from cause
        registerForAllMatchingVersions(level -> registerForLanguageLevel(level) && UnsupportedFeaturesUtil.raiseHasFromKeyword(node, level),
                                       PyLocalize.inspCompatibilityFeatureSupportThisSyntax().get(),
                                       node,
                                       new ReplaceRaiseStatementQuickFix());
    }

    @Override
    public void visitPyReprExpression(PyReprExpression node) {
        super.visitPyReprExpression(node);

        registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                       PyLocalize.inspCompatibilityFeatureSupportBackquotes().get(),
                                       node,
                                       new ReplaceBackquoteExpressionQuickFix());
    }


    @Override
    public void visitPyWithStatement(PyWithStatement node) {
        super.visitPyWithStatement(node);
        checkParenthesizedWithItems(node);
        checkAsyncKeyword(node);
    }

    private void checkParenthesizedWithItems(PyWithStatement node) {
        PsiElement lpar = PyPsiUtils.getFirstChildOfType(node, PyTokenTypes.LPAR);
        PsiElement rpar = PyPsiUtils.getFirstChildOfType(node, PyTokenTypes.RPAR);
        if (lpar == null && rpar == null) {
            return;
        }
        // Context expressions such as "(foo(), bar())" or "(foo(),)" are valid in Python < 3.9, but most likely indicate an error anyway
        PyWithItem[] withItems = node.getWithItems();
        boolean canBeParsedAsSingleParenthesizedExpression = (
          withItems.length == 1 &&
          PyPsiUtils.getFirstChildOfType(withItems[0], PyTokenTypes.AS_KEYWORD) == null &&
          PyPsiUtils.getFirstChildOfType(node, PyTokenTypes.COMMA) == null
        );
        if (canBeParsedAsSingleParenthesizedExpression) {
            return;
        }
        for (PsiElement parenthesis : ContainerUtil.skipNulls(Arrays.asList(lpar, rpar))) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON39) && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportParenthesizedContextExpressions().get(),
                                           parenthesis);
        }
    }

    @Override
    public void visitPyForStatement(PyForStatement node) {
        super.visitPyForStatement(node);
        checkAsyncKeyword(node);
    }

    @Override
    public void visitPyPrintStatement(PyPrintStatement node) {
        super.visitPyPrintStatement(node);

        PsiElement[] arguments = node.getChildren();
        Predicate<PsiElement> nonParenthesesPredicate =
          element -> !(element instanceof PyParenthesizedExpression || element instanceof PyTupleExpression);

        if (arguments.length == 0 || ContainerUtil.exists(arguments, nonParenthesesPredicate)) {
            registerForAllMatchingVersions(level -> level.isPy3K() && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportPrintStatement().get(),
                                           node,
                                           new CompatibilityPrintCallQuickFix());
        }
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
        super.visitPyCallExpression(node);

        PsiElement firstChild = node.getFirstChild();
        if (firstChild != null && PyNames.SUPER.equals(firstChild.getText()) && ArrayUtil.isEmpty(node.getArguments())) {
            registerForAllMatchingVersions(level -> level.isPython2() && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportSuperWithoutArguments().get(),
                                           node);
        }

        highlightIncorrectArguments(node);
    }

    @Override
    public void visitPyFunction(PyFunction node) {
        super.visitPyFunction(node);
        checkAsyncKeyword(node);
    }

    @Override
    public void visitPyPrefixExpression(PyPrefixExpression node) {
        super.visitPyPrefixExpression(node);

        if (node.getOperator() == PyTokenTypes.AWAIT_KEYWORD) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportThisSyntax().get(),
                                           node);
        }
    }

    @Override
    public void visitPyYieldExpression(PyYieldExpression node) {
        super.visitPyYieldExpression(node);
        if (!node.isDelegating()) {
            return;
        }
        registerForAllMatchingVersions(level -> level.isPython2() && registerForLanguageLevel(level),
                                       PyLocalize.inspCompatibilityFeatureSupportYieldFrom().get(),
                                       node);
    }

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
        if (ContainerUtil.exists(myVersionsToProcess, level -> level.isPython2() && registerForLanguageLevel(level))) {
            PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, false, PyClass.class);
            if (function != null && node.getExpression() != null) {
                YieldVisitor visitor = new YieldVisitor();
                function.acceptChildren(visitor);
                if (visitor.haveYield()) {
                    registerProblem(node,
                                    PyLocalize.inspCompatibilityPre35VersionsDoNotAllowReturnWithArgumentInsideGenerator().get());
                }
            }
        }

        if (PsiTreeUtil.getParentOfType(node, PyFinallyPart.class, false, PyFunction.class) != null) {
            registerForAllMatchingVersions(
              level -> level.isAtLeast(LanguageLevel.PYTHON314) && registerForLanguageLevel(level),
              PyLocalize.inspCompatibilityFeatureSupportReturnInsideFinallyClause().get(),
              node
            );
        }
    }

    @Override
    public void visitPyEllipsisLiteralExpression(PyEllipsisLiteralExpression node) {
        PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(node, PySubscriptionExpression.class);
        if (subscription != null && PsiTreeUtil.isAncestor(subscription.getIndexExpression(), node, false)) {
            return;
        }
        PySliceItem sliceItem = PsiTreeUtil.getParentOfType(node, PySliceItem.class);
        if (sliceItem != null) {
            return;
        }
        registerForAllMatchingVersions(level -> level.isPython2() && registerForLanguageLevel(level),
                                       PyLocalize.inspCompatibilityFeatureSupportEllipsisOutsideSlices().get(),
                                       node);
    }

    @Override
    public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
        super.visitPyAugAssignmentStatement(node);
        PsiElement operation = node.getOperation();
        if (operation != null) {
            IElementType operationType = operation.getNode().getElementType();
            if (PyTokenTypes.ATEQ.equals(operationType)) {
                checkMatrixMultiplicationOperator(operation);
            }
        }
    }

    private void checkAsyncKeyword(PsiElement node) {
        ASTNode asyncNode = node.getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD);
        if (asyncNode != null) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportThisSyntax().get(),
                                           node,
                                           asyncNode.getTextRange(),
                                           true);
        }
    }

    private static class YieldVisitor extends PyElementVisitor {
        private boolean _haveYield = false;

        public boolean haveYield() {
            return _haveYield;
        }

        @Override
        public void visitPyYieldExpression(PyYieldExpression node) {
            _haveYield = true;
        }

        @Override
        public void visitPyElement(PyElement node) {
            if (!_haveYield) {
                node.acceptChildren(this);
            }
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            // do not go to nested functions
        }
    }

    protected boolean registerForLanguageLevel(LanguageLevel level) {
        return true;
    }

    protected abstract void registerProblem(PsiElement node,
                                            TextRange range,
                                            String message,
                                            boolean asError,
                                            LocalQuickFix ... fixes);

    protected void registerProblem(PsiElement node,
                                   String message,
                                   LocalQuickFix ... fixes) {
        registerProblem(node, node.getTextRange(), message, true, fixes);
    }

    protected void registerForAllMatchingVersions(Predicate<LanguageLevel> levelPredicate,
                                                  String suffix,
                                                  PsiElement node,
                                                  TextRange range,
                                                  boolean asError,
                                                  LocalQuickFix ... fixes) {
        List<LanguageLevel> levels = ContainerUtil.filter(myVersionsToProcess, levelPredicate::test);
        if (!levels.isEmpty()) {
            String versions = StringUtil.join(levels, ", ");
            String message = PyLocalize.inspCompatibilityInspectionUnsupportedFeaturePrefix(levels.size(), versions, suffix).get();
            registerProblem(node, range, message, asError, fixes);
        }
    }

    protected void registerForAllMatchingVersions(Predicate<LanguageLevel> levelPredicate,
                                                  String suffix,
                                                  PsiElement node,
                                                  LocalQuickFix ... fixes) {
        registerForAllMatchingVersions(levelPredicate, suffix, node, node.getTextRange(), true, fixes);
    }

    private static @Nullable LocalQuickFix createInterpreterSettingsQuickFix(PsiElement element) {
        Module module = ModuleUtilCore.findModuleForPsiElement(element);
        return PythonUiService.getInstance().createInterpreterSettingsQuickFix(module);
    }

    @Override
    public void visitPyNonlocalStatement(PyNonlocalStatement node) {
        registerForAllMatchingVersions(level -> level.isPython2() && registerForLanguageLevel(level),
                                       PyLocalize.inspCompatibilityFeatureHaveNonlocalKeyword().get(),
                                       node);
    }

    private void highlightIncorrectArguments(PyCallExpression callExpression) {
        Set<String> keywordArgumentNames = new HashSet<>();

        boolean seenKeywordArgument = false;
        boolean seenKeywordContainer = false;
        boolean seenPositionalContainer = false;

        for (PyExpression argument : callExpression.getArguments()) {
            if (argument instanceof PyKeywordArgument) {
                String keyword = ((PyKeywordArgument)argument).getKeyword();

                if (keywordArgumentNames.contains(keyword)) {
                    registerProblem(argument, PyLocalize.inspCompatibilityKeywordArgumentRepeated().get(), new PyRemoveArgumentQuickFix());
                }
                else if (seenKeywordContainer) {
                    registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                                   PyLocalize.inspCompatibilityFeatureAllowKeywordArgumentsAfterKwargs().get(),
                                                   argument,
                                                   new PyRemoveArgumentQuickFix());
                }

                seenKeywordArgument = true;
                keywordArgumentNames.add(keyword);
            }
            else if (argument instanceof PyStarArgument starArgument) {
                if (starArgument.isKeyword()) {
                    if (seenKeywordContainer) {
                        registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                                       PyLocalize.inspCompatibilityFeatureAllowDuplicateKwargs().get(),
                                                       argument,
                                                       new PyRemoveArgumentQuickFix());
                    }
                    seenKeywordContainer = true;
                }
                else {
                    if (seenPositionalContainer) {
                        registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                                       PyLocalize.inspCompatibilityFeatureAllowDuplicatePositionalVarargs().get(),
                                                       argument,
                                                       new PyRemoveArgumentQuickFix());
                    }
                    seenPositionalContainer = true;
                }
            }
            else {
                if (seenKeywordArgument) {
                    registerProblem(argument, PyLocalize.inspCompatibilityPositionalArgumentAfterKeywordArgument().get(),
                                    new PyRemoveArgumentQuickFix());
                }
                else if (seenPositionalContainer) {
                    registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                                   PyLocalize.inspCompatibilityFeatureAllowPositionalArgumentsAfterExpression().get(),
                                                   argument,
                                                   new PyRemoveArgumentQuickFix());
                }
                else if (seenKeywordContainer) {
                    registerProblem(argument, PyLocalize.inspCompatibilityPositionalArgumentAfterKwargs().get(),
                                    new PyRemoveArgumentQuickFix());
                }
            }
        }

        /* check for trailing comma */
        PyExpression lastArg = ContainerUtil.getLastItem(Arrays.asList(callExpression.getArguments()));
        if (lastArg instanceof PyStarArgument) {
            PsiElement sibling = PyPsiUtils.getNextNonWhitespaceSibling(lastArg);
            if (sibling != null && sibling.getNode().getElementType() == PyTokenTypes.COMMA) {
                boolean isKeyword = ((PyStarArgument)lastArg).isKeyword();
                String message;
                if (isKeyword) {
                    message = PyLocalize.inspCompatibilityFeatureAllowTrailingCommaAfterKwargs().get();
                }
                else {
                    message = PyLocalize.inspCompatibilityFeatureAllowTrailingCommaAfterPositionalVararg().get();
                }
                registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON35) && registerForLanguageLevel(level),
                                               message,
                                               sibling);
            }
        }
    }

    @Override
    public void visitPySlashParameter(PySlashParameter node) {
        super.visitPySlashParameter(node);

        registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON38) && registerForLanguageLevel(level),
                                       PyLocalize.inspCompatibilityFeatureSupportPositionalOnlyParameters().get(),
                                       node);
    }

    @Override
    public void visitPyFStringFragment(PyFStringFragment node) {
        super.visitPyFStringFragment(node);

        ASTNode equalitySignInFStringFragment = node.getNode().findChildByType(PyTokenTypes.EQ);
        if (equalitySignInFStringFragment != null) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON38) && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilitySupportEqualitySignsInFstrings().get(),
                                           equalitySignInFStringFragment.getPsi());
        }

        List<PyFStringFragment> containingFragmentsOfSameFString =
          PsiTreeUtil.collectParents(node, PyFStringFragment.class, false, o -> o instanceof PyStringLiteralExpression);

        if (containingFragmentsOfSameFString.size() > 1) {
            // At the moment, there is a limit of 2 for CPython 3.12, but it's implementation-dependent.
            // See https://peps.python.org/pep-0701/#specification
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON312) && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureAllowDeepExpressionNestingInFStrings().get(), node);
        }

        boolean isTopmostFragment = PsiTreeUtil.getParentOfType(node, PyFStringFragment.class, true) == null;
        if (isTopmostFragment) {
            List<PyFStringFragment> fragments = new ArrayList<>();
            fragments.add(node);
            PyFStringFragmentFormatPart formatPart = node.getFormatPart();
            if (formatPart != null) {
                fragments.addAll(formatPart.getFragments());
            }
            for (PyFStringFragment fragment : fragments) {
                String wholeNodeText = fragment.getText();
                TextRange range = fragment.getExpressionContentRange();
                for (int i = range.getStartOffset(); i < range.getEndOffset(); i++) {
                    if (wholeNodeText.charAt(i) == '\\') {
                        TextRange backslashRange = TextRange.from(i, 1).shiftRight(fragment.getTextRange().getStartOffset());
                        registerForAllMatchingVersions(
                          level -> level.isOlderThan(LanguageLevel.PYTHON312) && registerForLanguageLevel(level),
                          PyLocalize.inspCompatibilityFeatureAllowBackslashesInFStrings().get(),
                          node, backslashRange, true
                        );
                    }
                }
            }
        }


        List<PyFormattedStringElement> containingFStrings =
          PsiTreeUtil.collectParents(node, PyFormattedStringElement.class, false, e -> e instanceof PyStatement);
        assert !containingFStrings.isEmpty();
        PyFormattedStringElement parentFString = containingFStrings.get(0);
        List<PyFormattedStringElement> remainingEnclosingFStrings = ContainerUtil.subList(containingFStrings, 1);
        // Report only on fragments of the topmost single-quoted f-string to avoid duplicates
        // in cases like: f'{f'{1<BR>
        // + 1}'}'
        boolean isFragmentOfTopmostSingleQuotedFString = !parentFString.isTripleQuoted() &&
                                                         ContainerUtil.all(remainingEnclosingFStrings, PyStringElement::isTripleQuoted);
        if (isFragmentOfTopmostSingleQuotedFString && node.textContains('\n')) {
            int lineBreakOffset = node.getText().indexOf('\n');
            if (node.getExpressionContentRange().contains(lineBreakOffset)) {
                PsiElement multiLineLeaf = node.findElementAt(lineBreakOffset);
                assert multiLineLeaf != null;
                registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON312) && registerForLanguageLevel(level),
                                               PyLocalize.inspCompatibilityFeatureAllowNewLinesInFStrings().get(), multiLineLeaf);
            }
        }


        String parentFStringQuote = parentFString.getQuote();
        // Report only on fragments of the topmost f-string with this quote type to avoid duplicates in cases like: f'{f'{f"'"}'}'
        boolean isFragmentOfTopmostFStringWithSuchQuotes = ContainerUtil.all(remainingEnclosingFStrings,
                                                                             fString -> !fString.getQuote().equals(parentFStringQuote));
        if (isFragmentOfTopmostFStringWithSuchQuotes) {
            int illegalQuoteOffset = node.getText().indexOf(parentFStringQuote);
            if (node.getExpressionContentRange().contains(illegalQuoteOffset)) {
                TextRange illegalQuoteRange = TextRange.from(illegalQuoteOffset, parentFStringQuote.length());
                registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON312) && registerForLanguageLevel(level),
                                               PyLocalize.inspCompatibilityFeatureAllowQuoteReuseInFStrings().get(),
                                               node, illegalQuoteRange.shiftRight(node.getTextRange().getStartOffset()), true);
            }
        }
    }

    @Override
    public void visitComment(PsiComment node) {
        boolean insideFStringFragment = PsiTreeUtil.getParentOfType(node, PyFStringFragment.class) != null;
        if (insideFStringFragment) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON312) && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureLineCommentsInFStrings().get(), node);
        }
    }

    @Override
    public void visitPyAssignmentExpression(PyAssignmentExpression node) {
        super.visitPyAssignmentExpression(node);
        registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON38) && registerForLanguageLevel(level),
                                       PyLocalize.inspCompatibilityFeatureSupportAssignmentExpressions().get(), node);
    }

    @Override
    public void visitPyContinueStatement(PyContinueStatement node) {
        super.visitPyContinueStatement(node);

        if (PsiTreeUtil.getParentOfType(node, PyFinallyPart.class, false, PyLoopStatement.class) != null) {
            registerForAllMatchingVersions(
              level -> (level.isOlderThan(LanguageLevel.PYTHON38) || level.isAtLeast(LanguageLevel.PYTHON314)) && registerForLanguageLevel(level),
              PyLocalize.inspCompatibilityFeatureSupportContinueInsideFinallyClause().get(),
              node
            );
        }
    }

    @Override
    public void visitPyBreakStatement(PyBreakStatement node) {
        super.visitPyBreakStatement(node);

        if (PsiTreeUtil.getParentOfType(node, PyFinallyPart.class, false, PyLoopStatement.class) != null) {
            registerForAllMatchingVersions(
              level -> level.isAtLeast(LanguageLevel.PYTHON314) && registerForLanguageLevel(level),
              PyLocalize.inspCompatibilityFeatureSupportBreakInsideFinallyClause().get(),
              node
            );
        }
    }

    @Override
    public void visitPyDecorator(PyDecorator decorator) {
        super.visitPyDecorator(decorator);
        if (PsiTreeUtil.getChildOfType(decorator, PsiErrorElement.class) == null && decorator.getQualifiedName() == null) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON39) && registerForLanguageLevel(level),
                                           PyLocalize.inspCompatibilityFeatureSupportArbitraryExpressionsAsDecorator().get(),
                                           decorator);
        }
    }

    @Override
    public void visitPyMatchStatement(PyMatchStatement matchStatement) {
        registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON310),
                                       PyLocalize.inspCompatibilityFeatureSupportMatchStatements().get(),
                                       matchStatement.getFirstChild());
    }

    @Override
    public void visitPyFromImportStatement(PyFromImportStatement node) {
        super.visitPyFromImportStatement(node);
        if (node.isLazy()) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON315),
                                           PyLocalize.inspCompatibilityFeatureSupportLazyImports().get(),
                                           node.getFirstChild(),
                                           LocalQuickFix.notNullElements(createInterpreterSettingsQuickFix(node)));
        }
    }

    @Override
    public void visitPyTypeAliasStatement(PyTypeAliasStatement node) {
        registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON312),
                                       PyLocalize.inspCompatibilityFeatureSupportTypeAliasStatements().get(),
                                       node);
    }

    @Override
    public void visitPyTypeParameterList(PyTypeParameterList node) {
        // No need to report an error inside the statement which is already reported as unsupported
        if (!(node.getParent() instanceof PyTypeAliasStatement)) {
            registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON312),
                                           PyLocalize.inspCompatibilityFeatureSupportThisSyntax().get(),
                                           node);
        }
    }

    private void checkBitwiseOrUnionSyntax(PyBinaryExpression node) {
        if (node.getOperator() != PyTokenTypes.OR) {
            return;
        }

        PsiFile file = node.getContainingFile();
        boolean isInAnnotation = PsiTreeUtil.getParentOfType(node, PyAnnotation.class, false, PyStatement.class) != null;
        if (file == null ||
            file instanceof PyFile &&
            ((PyFile)file).hasImportFromFuture(FutureFeature.ANNOTATIONS) &&
            isInAnnotation) {
            return;
        }

        TypeEvalContext context = TypeEvalContext.codeAnalysis(node.getProject(), node.getContainingFile());

        // Consider only full expression not parts to have only one registered problem
        if (PsiTreeUtil.getParentOfType(node, PyBinaryExpression.class, true, PyStatement.class) != null) {
            return;
        }

        Ref<PyType> refType = PyTypingTypeProvider.getType(node, context);
        if (refType != null && refType.get() instanceof PyUnionType) {
            if (isInAnnotation) {
                registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON310),
                                               PyLocalize.inspCompatibilityNewUnionSyntaxNotAvailableInEarlierVersion().get(),
                                               node, new ReplaceWithOldStyleUnionQuickFix(), new AddFromFutureImportAnnotationsQuickFix());
            }
            else {
                if (node.getLeftExpression().getType(context) == null) {
                    return;
                }
                registerForAllMatchingVersions(level -> level.isOlderThan(LanguageLevel.PYTHON310),
                                               PyLocalize.inspCompatibilityNewUnionSyntaxNotAvailableInEarlierVersion().get(),
                                               node, new ReplaceWithOldStyleUnionQuickFix());
            }
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class ReplaceWithOldStyleUnionQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixReplaceWithOldUnionStyle();
        }

        @Override
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiFile file = element.getContainingFile();
            if (file == null) {
                return;
            }

            if (!(element instanceof PyBinaryExpression expression)) {
                return;
            }

            List<String> types = collectUnionTypes(expression);
            LanguageLevel languageLevel = LanguageLevel.forElement(file);
            AddImportHelper.ImportPriority priority = languageLevel.isAtLeast(LanguageLevel.PYTHON35) ?
                                                            AddImportHelper.ImportPriority.BUILTIN :
                                                                                                            AddImportHelper.ImportPriority.THIRD_PARTY;

            PyCallExpression call = PsiTreeUtil.getParentOfType(expression, PyCallExpression.class);
            if (call != null && call.isCalleeText(PyNames.ISINSTANCE, PyNames.ISSUBCLASS)) {
                replaceOldExpressionWith(project, languageLevel, expression, "(" + StringUtil.join(types, ", ") + ")");
            }
            else {
                if (types.size() == 2 && types.contains(PyNames.NONE)) {
                    String notNoneType = PyNames.NONE.equals(types.get(0)) ? types.get(1) : types.get(0);
                    AddImportHelper.addOrUpdateFromImportStatement(file, "typing", "Optional", null, priority, expression);
                    replaceOldExpressionWith(project, languageLevel, expression, "Optional[" + notNoneType + "]");
                }
                else {
                    AddImportHelper.addOrUpdateFromImportStatement(file, "typing", "Union", null, priority, expression);
                    replaceOldExpressionWith(project, languageLevel, expression, "Union[" + StringUtil.join(types, ", ") + "]");
                }
            }
        }

        private static void replaceOldExpressionWith(Project project, LanguageLevel languageLevel,
                                                     PyBinaryExpression expression, String text) {
            PyExpression newExpression = PyElementGenerator
              .getInstance(project)
              .createExpressionFromText(languageLevel, text);
            expression.replace(newExpression);
        }

        private static List<String> collectUnionTypes(@Nullable PyExpression expression) {
            if (expression == null) {
                return Collections.emptyList();
            }
            if (expression instanceof PyBinaryExpression) {
                List<String> leftTypes = collectUnionTypes(((PyBinaryExpression)expression).getLeftExpression());
                List<String> rightTypes = collectUnionTypes(((PyBinaryExpression)expression).getRightExpression());
                return ContainerUtil.concat(leftTypes, rightTypes);
            }
            else if (expression instanceof PyParenthesizedExpression) {
                return collectUnionTypes(PyPsiUtils.flattenParens(expression));
            }
            else {
                return Collections.singletonList(expression.getText());
            }
        }
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class AddFromFutureImportAnnotationsQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixAddFromFutureImportAnnotations();
        }

        @Override
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiFile file = element.getContainingFile();
            AddImportHelper.addOrUpdateFromImportStatement(file, "__future__", "annotations", null, AddImportHelper.ImportPriority.FUTURE, null);
        }
    }
}
