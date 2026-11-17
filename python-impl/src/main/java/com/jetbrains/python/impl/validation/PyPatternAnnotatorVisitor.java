// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.inspections.PyRemoveElementFix;
import com.jetbrains.python.psi.PyAsPattern;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCaseClause;
import com.jetbrains.python.psi.PyDoubleStarPattern;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyGroupPattern;
import com.jetbrains.python.psi.PyKeyValuePattern;
import com.jetbrains.python.psi.PyKeywordPattern;
import com.jetbrains.python.psi.PyLiteralPattern;
import com.jetbrains.python.psi.PyMappingPattern;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyOrPattern;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PyPatternArgumentList;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PySequencePattern;
import com.jetbrains.python.psi.PySingleStarPattern;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyValuePattern;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Stack;
import consulo.util.lang.StringUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jetbrains.python.impl.psi.PyUtil.as;

final class PyPatternAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    PyPatternAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitPySingleStarPattern(PySingleStarPattern starPattern) {
        PsiElement parent = starPattern.getParent();
        if (!(parent instanceof PySequencePattern)) {
            myHolder.markError(starPattern, PyLocalize.annPatternsSingleStarPatternCannotBeUsedOutsideSequencePatterns().get());
        }
    }

    @Override
    public void visitPyDoubleStarPattern(PyDoubleStarPattern starPattern) {
        PsiElement parent = starPattern.getParent();
        if (!(parent instanceof PyMappingPattern)) {
            myHolder.markError(starPattern, PyLocalize.annPatternsDoubleStarPatternCannotBeUsedOutsideMappingPatterns().get());
        }
    }

    @Override
    public void visitPyLiteralPattern(PyLiteralPattern literalPattern) {
        PyBinaryExpression expression = as(literalPattern.getExpression(), PyBinaryExpression.class);
        if (expression != null) {
            PyNumericLiteralExpression rightOperand = as(expression.getRightExpression(), PyNumericLiteralExpression.class);
            if (rightOperand != null && rightOperand.getFirstChild().getNode().getElementType() != PyTokenTypes.IMAGINARY_LITERAL) {
                myHolder.markError(expression, PyLocalize.annPatternsInvalidComplexNumberLiteral().get());
            }
        }
    }

    @Override
    public void visitPyKeyValuePattern(PyKeyValuePattern keyValuePattern) {
        PyPattern keyPattern = keyValuePattern.getKeyPattern();
        if (!(keyPattern instanceof PyValuePattern || keyPattern instanceof PyLiteralPattern)) {
            myHolder.markError(keyPattern, PyLocalize.annPatternsKeyPatternCanOnlyBeValueOrLiteralPattern().get());
        }
    }

    @Override
    public void visitPyOrPattern(PyOrPattern orPattern) {
        List<PyPattern> alternatives = orPattern.getAlternatives();
        PyPattern lastAlternative = alternatives.get(alternatives.size() - 1);
        Map<PyPattern, Set<String>> patternToBoundNames = new HashMap<>();
        Set<String> allBoundNames = new HashSet<>();

        for (PyPattern alternative : alternatives) {
            Set<String> boundNames = SyntaxTraverser.psiTraverser(alternative)
                .filter(PyTargetExpression.class)
                .map(PyTargetExpression::getName)
                .toSet();
            patternToBoundNames.put(alternative, boundNames);
            allBoundNames.addAll(boundNames);

            if (alternative != lastAlternative && alternative.isIrrefutable()) {
                myHolder.markError(alternative, PyLocalize.annPatternsPatternMakesRemainingAlternativesUnreachable().get());
            }
        }

        for (Map.Entry<PyPattern, Set<String>> entry : patternToBoundNames.entrySet()) {
            Set<String> boundNames = entry.getValue();
            if (!boundNames.equals(allBoundNames)) {
                Collection<String> missingNames = ContainerUtil.subtract(allBoundNames, boundNames);
                String nameList = StringUtil.join(ContainerUtil.sorted(missingNames), ", ");
                myHolder.markError(entry.getKey(), PyLocalize.annPatternsPatternDoesNotBindNames(missingNames.size(), nameList).get());
            }
        }
    }

    @Override
    public void visitPyMatchStatement(PyMatchStatement matchStatement) {
        List<PyCaseClause> clauses = matchStatement.getCaseClauses();
        if (clauses.isEmpty()) {
            return;
        }

        for (PyCaseClause clause : clauses.subList(0, clauses.size() - 1)) {
            PyPattern pattern = clause.getPattern();
            if (pattern == null) {
                continue;
            }
            PyPattern unwrappedPattern = unwrapGroupAndAsPatterns(pattern);
            // There will be another warning for top-level star patterns
            if (unwrappedPattern instanceof PySingleStarPattern || unwrappedPattern instanceof PyDoubleStarPattern) {
                continue;
            }
            if (clause.getGuardCondition() == null && pattern.isIrrefutable()) {
                myHolder.markError(pattern, PyLocalize.annPatternsPatternMakesRemainingCaseClausesUnreachable().get());
            }
        }
    }

    @Override
    public void visitPyPatternArgumentList(PyPatternArgumentList argumentList) {
        Set<String> usedAttrNames = new HashSet<>();
        boolean seenKeywordPattern = false;
        for (PyPattern attrPattern : argumentList.getPatterns()) {
            PyKeywordPattern keywordPattern = as(attrPattern, PyKeywordPattern.class);
            if (keywordPattern == null) {
                if (seenKeywordPattern) {
                    myHolder.newAnnotation(HighlightSeverity.ERROR,
                            PyLocalize.annPatternsPositionalPatternMustAppearBeforeKeywordPattern().get())
                        .range(attrPattern).withFix(new PyRemoveElementFix(attrPattern)).create();
                }
                continue;
            }
            seenKeywordPattern = true;
            if (!usedAttrNames.add(keywordPattern.getKeyword())) {
                myHolder.newAnnotation(HighlightSeverity.ERROR,
                        PyLocalize.annPatternsAttributeNameIsRepeated(keywordPattern.getKeyword()).get())
                    .range(keywordPattern.getKeywordElement()).withFix(new PyRemoveElementFix(keywordPattern)).create();
            }
        }
    }

    @Override
    public void visitPyCaseClause(PyCaseClause caseClause) {
        PyPattern pattern = caseClause.getPattern();
        if (pattern == null) {
            return;
        }

        Stack<Set<String>> boundNamesPerOrBranch = new Stack<>();
        // Assume that the top-level pattern is a single branch of a synthetic OR pattern
        boundNamesPerOrBranch.push(new HashSet<>());

        pattern.accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyOrPattern(PyOrPattern orPattern) {
                Set<String> allBoundNamesInOrPattern = new HashSet<>();
                for (PyPattern alternative : orPattern.getAlternatives()) {
                    boundNamesPerOrBranch.push(new HashSet<>());
                    alternative.accept(this);
                    allBoundNamesInOrPattern.addAll(boundNamesPerOrBranch.peek());
                    boundNamesPerOrBranch.pop();
                }
                boundNamesPerOrBranch.peek().addAll(allBoundNamesInOrPattern);
            }

            @Override
            public void visitPyTargetExpression(PyTargetExpression target) {
                String name = target.getName();
                boolean alreadyBound = ContainerUtil.exists(boundNamesPerOrBranch, names -> names.contains(name));
                if (alreadyBound) {
                    myHolder.markError(target, PyLocalize.annPatternsNameAlreadyBound(name).get());
                }
                boundNamesPerOrBranch.peek().add(name);
            }
        });
    }

    @Override
    public void visitPySequencePattern(PySequencePattern sequencePattern) {
        List<PySingleStarPattern> starPatterns = PsiTreeUtil.getChildrenOfTypeAsList(sequencePattern, PySingleStarPattern.class);
        if (starPatterns.size() > 1) {
            for (PySingleStarPattern pattern : starPatterns.subList(1, starPatterns.size())) {
                myHolder.markError(pattern, PyLocalize.annPatternsRepeatedStarPattern().get());
            }
        }
    }

    @Override
    public void visitPyMappingPattern(PyMappingPattern mappingPattern) {
        List<PyDoubleStarPattern> starPatterns = PsiTreeUtil.getChildrenOfTypeAsList(mappingPattern, PyDoubleStarPattern.class);
        if (starPatterns.size() > 1) {
            for (PyDoubleStarPattern pattern : starPatterns.subList(1, starPatterns.size())) {
                myHolder.markError(pattern, PyLocalize.annPatternsRepeatedStarPattern().get());
            }
        }
    }

    private static PyPattern unwrapGroupAndAsPatterns(PyPattern pattern) {
        if (pattern instanceof PyGroupPattern) {
            return unwrapGroupAndAsPatterns(((PyGroupPattern)pattern).getPattern());
        }
        if (pattern instanceof PyAsPattern) {
            return unwrapGroupAndAsPatterns(((PyAsPattern)pattern).getPattern());
        }
        return pattern;
    }
}
