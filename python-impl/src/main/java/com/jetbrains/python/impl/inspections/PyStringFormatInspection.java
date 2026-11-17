// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.google.common.collect.ImmutableMap;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PyStringFormatParser;
import com.jetbrains.python.impl.PyStringFormatParser.NewStyleSubstitutionChunk;
import com.jetbrains.python.impl.PyStringFormatParser.PercentSubstitutionChunk;
import com.jetbrains.python.impl.codeInsight.PySubstitutionChunkReference;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.impl.inspections.quickfix.PyAddDunderMethodQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyAddSpecifierToFormatQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.types.PyABCUtil;
import com.jetbrains.python.impl.psi.types.PyLegacyDocstringTypeParser;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyFStringFragmentFormatPart;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyLiteralExpression;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PySliceItem;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedRatedResolveResult;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableArgument;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jetbrains.python.impl.psi.PyStringDunderUtil.KNOWN_COMPLEX_TYPES;
import static com.jetbrains.python.impl.psi.PyStringDunderUtil.KNOWN_DECIMAL_TYPES;
import static com.jetbrains.python.impl.psi.PyStringDunderUtil.KNOWN_FORMAT_MINI_LANGUAGE_TYPES;
import static com.jetbrains.python.impl.psi.PyStringDunderUtil.KNOWN_INT_TYPES;
import static com.jetbrains.python.impl.psi.PyStringDunderUtil.isAllowedFormatOverride;
import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;
import static com.jetbrains.python.impl.psi.types.PyTypeChecker.match;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAnyOrUnknown;

@ExtensionImpl
public class PyStringFormatInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameStrFormat();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public PsiElementVisitor buildVisitor(
        ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        Object state
    ) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    public static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        private static class Inspection {
            private static final ImmutableMap<Character, String> PERCENT_FORMAT_CONVERSIONS = ImmutableMap.<Character, String>builder()
                .put('d', "int or long or float")
                .put('i', "int or long or float")
                .put('o', "int or long or float")
                .put('u', "int or long or float")
                .put('x', "int or long or float")
                .put('X', "int or long or float")
                .put('e', "float")
                .put('E', "float")
                .put('f', "float")
                .put('F', "float")
                .put('g', "float")
                .put('G', "float")
                .put('c', "str")
                .put('r', "str")
                .put('a', "str")
                .put('s', "str")
                .put('b', "bytes")
                .build();

            private final Visitor myVisitor;
            private final TypeEvalContext myTypeEvalContext;
            private final Map<String, Boolean> myUsedMappingKeys = new HashMap<>();
            private int myExpectedArguments = 0;
            private boolean myProblem = false;

            private final Map<@Nullable String, String> myFormatSpec = new LinkedHashMap<>();

            private Inspection(Visitor visitor, TypeEvalContext typeEvalContext) {
                myVisitor = visitor;
                myTypeEvalContext = typeEvalContext;
            }

            public boolean isProblem() {
                return myProblem;
            }

            // return number of arguments or null if it can not be computed
            public @Nullable Integer inspectArguments(@Nullable PyExpression rightExpression, PsiElement problemTarget) {
                Class<?>[] SIMPLE_RHS_EXPRESSIONS = {
                    PyLiteralExpression.class,
                    PySubscriptionExpression.class,
                    PyBinaryExpression.class,
                    PyConditionalExpression.class
                };
                PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(problemTarget);
                PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);

                String s = myFormatSpec.get("1");
                if (rightExpression instanceof PySubscriptionExpression subscription &&
                    subscription.getIndexExpression() instanceof PySliceItem && s != null) {
                    PySliceItem sliceItem = (PySliceItem) subscription.getIndexExpression();
                    PyType type = myTypeEvalContext.getType(subscription.getOperand());
                    if (type == null) {
                        return null;
                    }
                    PyType stringType = PyBuiltinCache.getInstance(subscription).getStringType(LanguageLevel.forElement(subscription));
                    PyType listType = PyBuiltinCache.getInstance(subscription).getListType();

                    if (match(listType, type, myTypeEvalContext)
                        || match(stringType, type, myTypeEvalContext)) {
                        checkTypeCompatible(
                            problemTarget, builtinCache.getStrType(),
                            PyLegacyDocstringTypeParser.getTypeByName(problemTarget, s, myTypeEvalContext)
                        );
                        return 1;
                    }
                    PyExpression lower = sliceItem.getLowerBound();
                    PyExpression upper = sliceItem.getUpperBound();
                    PyExpression stride = sliceItem.getStride();
                    if (upper instanceof PyNumericLiteralExpression upperLiteral) {
                        BigInteger lowerVal;
                        if (lower instanceof PyNumericLiteralExpression lowerLiteral) {
                            lowerVal = lowerLiteral.getBigIntegerValue();
                        }
                        else {
                            lowerVal = BigInteger.ZERO;
                        }
                        int count = upperLiteral.getBigIntegerValue().subtract(lowerVal).intValue();
                        int strideVal = stride instanceof PyNumericLiteralExpression strideLiteral
                            ? strideLiteral.getBigIntegerValue().intValue()
                            : 1;
                        int res = count / strideVal;
                        int residue = count % strideVal == 0 ? 0 : 1;
                        return res + residue;
                    }
                    return null;
                }
                else if (PsiTreeUtil.instanceOf(rightExpression, SIMPLE_RHS_EXPRESSIONS)) {
                    if (s != null) {
                        PyType rightType = myTypeEvalContext.getType(rightExpression);
                        for (PyType member : PyTypeUtil.asUnionSequence(rightType)) {
                            if (member instanceof PyTupleType tupleMember) {
                                matchEntireTupleTypes(problemTarget, tupleMember);
                                return tupleMember.getElementCount();
                            }
                            else {
                                checkExpressionType(rightExpression, s, problemTarget);
                            }
                        }
                    }
                    return 1;
                }
                else if (rightExpression instanceof PyReferenceExpression referenceExpression) {
                    if (PyNames.DUNDER_DICT.equals(referenceExpression.getName())) {
                        return null;
                    }

                    List<QualifiedRatedResolveResult> resolveResults =
                        referenceExpression.multiFollowAssignmentsChain(resolveContext);
                    if (resolveResults.isEmpty()) {
                        return null;
                    }

                    PsiElement pyElement = PyUtil.filterTopPriorityResults(resolveResults).get(0).getElement();
                    if (pyElement == referenceExpression || !(pyElement instanceof PyExpression pyExpression)) {
                        return null;
                    }
                    if (pyElement instanceof PyDictLiteralExpression) {
                        return inspectDict(referenceExpression, problemTarget, true);
                    }
                    return inspectArguments(pyExpression, problemTarget);
                }
                else if (rightExpression instanceof PyCallExpression callExpression) {
                    PyExpression callee = callExpression.getCallee();
                    if (callee != null && "dict".equals(callee.getName())) {
                        return 1;
                    }
                    int result = inspectCallExpression(callExpression, resolveContext, myTypeEvalContext);
                    return result >= 0 ? result : null;
                }
                else if (rightExpression instanceof PyParenthesizedExpression parenthesizedExpression) {
                    PyExpression rhs = parenthesizedExpression.getContainedExpression();
                    if (rhs != null) {
                        return inspectArguments(rhs, problemTarget);
                    }
                }
                else if (rightExpression instanceof PyTupleExpression tupleExpression) {
                    if (PsiTreeUtil.isAncestor(problemTarget, tupleExpression, false)) {
                        PyExpression[] expressions = tupleExpression.getElements();
                        int i = 1;
                        for (PyExpression expression : expressions) {
                            String formatSpec = myFormatSpec.get(String.valueOf(i));
                            if (formatSpec != null) {
                                checkExpressionType(expression, formatSpec, expression);
                            }
                            ++i;
                        }
                        return expressions.length;
                    }
                    else {
                        if (!(myTypeEvalContext.getType(tupleExpression) instanceof PyTupleType tupleType)) {
                            return null;
                        }
                        matchEntireTupleTypes(problemTarget, tupleType);
                        return tupleType.getElementCount();
                    }
                }
                else if (rightExpression instanceof PyDictLiteralExpression) {
                    return inspectDict(rightExpression, problemTarget, false);
                }
                else if (PsiTreeUtil.instanceOf(rightExpression, PySequenceExpression.class, PyComprehensionElement.class)) {
                    if (s != null) {
                        checkTypeCompatible(
                            problemTarget, builtinCache.getStrType(),
                            PyLegacyDocstringTypeParser.getTypeByName(problemTarget, s, myTypeEvalContext)
                        );
                        return 1;
                    }
                }
                return null;
            }

            public void matchEntireTupleTypes(PsiElement rightExpression, @Nullable PyTupleType rightExpressionType) {
                List<@Nullable PyType> expectedElementTypes = new ArrayList<>();
                for (String name : myFormatSpec.values()) {
                    PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(rightExpression);
                    PyType expected = PyLegacyDocstringTypeParser.getTypeByName(rightExpression, name, myTypeEvalContext);
                    expectedElementTypes.add(expected == builtinCache.getStrType() ? PyAnyType.getUnknown() : expected);
                }
                PyTupleType expectedTupleType = PyTupleType.create(rightExpression, expectedElementTypes);
                checkTypeCompatible(rightExpression, rightExpressionType, expectedTupleType);
            }

            // inspects dict expressions. Finds key-value pairs from subscriptions if addSubscriptions is true.
            public int inspectDict(PyExpression rightExpression, PsiElement problemTarget, boolean addSubscriptions) {
                Map<PyExpression, PyExpression> additionalExpressions;
                PsiElement pyElement;
                if (addSubscriptions) {
                    additionalExpressions = addSubscriptions(
                        rightExpression.getContainingFile(),
                        rightExpression.getText()
                    );
                    PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);
                    pyElement = ((PyReferenceExpression) rightExpression).followAssignmentsChain(resolveContext).getElement();
                }
                else {
                    additionalExpressions = new HashMap<>();
                    pyElement = rightExpression;
                }
                if (pyElement == null) {
                    return 0;
                }
                PyKeyValueExpression[] expressions = ((PyDictLiteralExpression) pyElement).getElements();
                if (myUsedMappingKeys.isEmpty()) {
                    if (myExpectedArguments > 0) {
                        if (myExpectedArguments > 1 && myExpectedArguments == (expressions.length + additionalExpressions.size())) {
                            // probably "%s %s" % {'a':1, 'b':2}, with names forgotten in template
                            registerProblem(rightExpression, PyLocalize.inspFormatRequiresNoMapping().get());
                        }
                        else {
                            // "braces: %s" % {'foo':1} gives "braces: {'foo':1}", implicit str() kicks in
                            return 1;
                        }
                    }
                    else {
                        // "foo" % {whatever} is just "foo"
                        return 0;
                    }
                }
                int referenceKeyNumber = 0;
                for (PyKeyValueExpression expression : expressions) {
                    PyExpression key = expression.getKey();
                    PyExpression value = expression.getValue();
                    if (key instanceof PyStringLiteralExpression stringKey) {
                        resolveMappingKey(problemTarget, stringKey, value);
                    }
                    else if (key instanceof PyReferenceExpression) {
                        referenceKeyNumber++;
                    }
                }
                for (Map.Entry<PyExpression, PyExpression> expression : additionalExpressions.entrySet()) {
                    PyExpression key = expression.getKey();
                    PyExpression value = expression.getValue();
                    if (key instanceof PyStringLiteralExpression stringKey) {
                        resolveMappingKey(problemTarget, stringKey, value);
                    }
                    else if (key instanceof PyReferenceExpression) {
                        referenceKeyNumber++;
                    }
                }

                int unresolved = 0;
                for (String key : myUsedMappingKeys.keySet()) {
                    if (!myUsedMappingKeys.get(key)) {
                        unresolved++;
                        if (unresolved > referenceKeyNumber) {
                            registerProblem(problemTarget, PyInspectionMessages.problemMessage(
                                params -> PyLocalize.inspStrFormatKeyHasNoArgument(params[0]), key));
                            break;
                        }
                    }
                }
                return expressions.length + additionalExpressions.size();
            }

            public void resolveMappingKey(PsiElement problemTarget, PyStringLiteralExpression key, @Nullable PyExpression value) {
                String name = key.getStringValue();
                if (myUsedMappingKeys.get(name) != null) {
                    myUsedMappingKeys.put(name, true);
                    if (value != null) {
                        checkExpressionType(value, Objects.requireNonNull(myFormatSpec.get(name)), problemTarget);
                    }
                }
            }

            public void registerProblem(PsiElement problemTarget, String message, LocalQuickFix quickFix) {
                myProblem = true;
                myVisitor.registerProblem(problemTarget, message, quickFix);
            }

            public void registerProblem(PsiElement problemTarget, String message) {
                myProblem = true;
                myVisitor.registerProblem(problemTarget, message);
            }

            public void registerProblem(PsiElement problemTarget, ProblemMessage message) {
                myProblem = true;
                myVisitor.registerProblem(problemTarget, message);
            }

            public void checkExpressionType(PyExpression expression, String expectedTypeName, PsiElement problemTarget) {
                PyType actual = myTypeEvalContext.getType(expression);
                PyType expected = PyLegacyDocstringTypeParser.getTypeByName(problemTarget, expectedTypeName, myTypeEvalContext);
                if (actual != null) {
                    checkTypeCompatible(problemTarget, actual, expected);
                }
            }

            public void checkTypeCompatible(PsiElement problemTarget, @Nullable PyType actual, @Nullable PyType expected) {
                if (expected != null && PyNames.TYPE_STR.equals(expected.getName())) {
                    return;
                }
                if (actual != null && !match(expected, actual, myTypeEvalContext)) {
                    registerProblem(problemTarget, PyLocalize.inspStrFormatUnexpectedArgumentType(actual.getName()).get());
                }
            }

            public void inspectPercentFormat(PyStringLiteralExpression formatExpression) {
                String value = formatExpression.getText();
                List<PyStringFormatParser.SubstitutionChunk> chunks =
                    PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parsePercentFormat(value));

                myExpectedArguments = chunks.size();
                myUsedMappingKeys.clear();

                // if use mapping keys
                boolean mapping = !chunks.isEmpty() && chunks.get(0).getMappingKey() != null;
                for (int i = 0; i < chunks.size(); i++) {
                    if (chunks.get(i) instanceof PercentSubstitutionChunk chunk) {
                        // Mapping key
                        String mappingKey = String.valueOf(i + 1);
                        if (mapping) {
                            if (chunk.getMappingKey() == null || chunk.isUnclosedMapping()) {
                                registerProblem(formatExpression, PyLocalize.inspTooFewKeys().get());
                                break;
                            }
                            mappingKey = chunk.getMappingKey();
                            myUsedMappingKeys.put(mappingKey, false);
                        }

                        // Minimum field width
                        inspectWidth(formatExpression, chunk.getWidth());

                        // Precision
                        inspectWidth(formatExpression, chunk.getPrecision());

                        // Format specifier
                        char conversionType = chunk.getConversionType();
                        if (conversionType == 'b') {
                            LanguageLevel languageLevel = LanguageLevel.forElement(formatExpression);
                            if (languageLevel.isOlderThan(LanguageLevel.PYTHON35) || !isBytesLiteral(formatExpression, myTypeEvalContext)) {
                                registerProblem(formatExpression, PyLocalize.inspStrFormatUnsupportedFormatCharacterB().get());
                                return;
                            }
                        }
                        LanguageLevel languageLevel = LanguageLevel.forElement(formatExpression);
                        if (PERCENT_FORMAT_CONVERSIONS.containsKey(conversionType) && !(languageLevel.isPython2() && conversionType == 'a')) {
                            myFormatSpec.put(mappingKey, PERCENT_FORMAT_CONVERSIONS.get(conversionType));
                            continue;
                        }
                        registerProblem(
                            formatExpression,
                            PyLocalize.inspNoFormatSpecifierChar().get(),
                            new PyAddSpecifierToFormatQuickFix()
                        );
                        return;
                    }
                }
            }

            public void inspectWidth(PyStringLiteralExpression formatExpression, @Nullable String width) {
                if (!"*".equals(width)) {
                    return;
                }
                ++myExpectedArguments;
                if (!myUsedMappingKeys.isEmpty()) {
                    registerProblem(
                        formatExpression,
                        PyLocalize.inspStrFormatCanNotUseStarInFormatsWhenUsingMapping().get()
                    );
                }
            }

            public void inspectValues(@Nullable PyExpression rightExpression) {
                if (rightExpression == null) {
                    return;
                }
                if (rightExpression instanceof PyParenthesizedExpression parenthesizedExpression) {
                    inspectValues(parenthesizedExpression.getContainedExpression());
                }
                else {
                    PyType type = myTypeEvalContext.getType(rightExpression);
                    if (type instanceof PyClassType classType) {
                        if (!myUsedMappingKeys.isEmpty() && !PyABCUtil.isSubclass(classType.getPyClass(), PyNames.MAPPING, myTypeEvalContext)) {
                            registerProblem(rightExpression, PyLocalize.inspFormatRequiresMapping().get());
                            return;
                        }
                    }
                    inspectArgumentsNumber(rightExpression);
                }
            }

            public void inspectArgumentsNumber(PyExpression rightExpression) {
                Integer arguments = inspectArguments(rightExpression, rightExpression);
                if (myUsedMappingKeys.isEmpty() && arguments != null) {
                    if (myExpectedArguments < arguments) {
                        registerProblem(rightExpression, PyLocalize.inspTooManyArgsForFmtString().get());
                    }
                    else if (myExpectedArguments > arguments) {
                        registerProblem(rightExpression, PyLocalize.inspTooFewArgsForFmtString().get());
                    }
                }
            }

            private static Map<PyExpression, PyExpression> addSubscriptions(@Nullable PsiFile file, @Nullable String operand) {
                Map<PyExpression, PyExpression> additionalExpressions = new HashMap<>();
                Collection<PySubscriptionExpression> subscriptionExpressions =
                    PsiTreeUtil.findChildrenOfType(file, PySubscriptionExpression.class);
                for (PySubscriptionExpression expr : subscriptionExpressions) {
                    if (expr.getOperand().getText().equals(operand)) {
                        PsiElement parent = expr.getParent();
                        if (parent instanceof PyAssignmentStatement assignmentStatement) {
                            if (expr.equals(assignmentStatement.getLeftHandSideExpression())) {
                                PyExpression key = expr.getIndexExpression();
                                if (key != null) {
                                    additionalExpressions.put(key, Objects.requireNonNull(assignmentStatement.getAssignedValue()));
                                }
                            }
                        }
                    }
                }
                return additionalExpressions;
            }

            private static boolean isBytesLiteral(PyStringLiteralExpression expr, TypeEvalContext context) {
                PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(expr);
                PyClassType bytesType = builtinCache.getBytesType(LanguageLevel.forElement(expr));
                PyType actualType = context.getType(expr);
                return bytesType != null && actualType != null && match(bytesType, actualType, context);
            }
        }

        private static class NewStyleInspection {
            private final String myText;
            private final PyElement myProblemTarget;
            private final Visitor myVisitor;
            private final TypeEvalContext myTypeEvalContext;

            private boolean myProblem = false;

            private final Map<String, Set<String>> myFormatSpec = new HashMap<>();

            private NewStyleInspection(String text, PyElement problemTarget, Visitor visitor, TypeEvalContext typeEvalContext) {
                myText = text;
                myProblemTarget = problemTarget;
                myVisitor = visitor;
                myTypeEvalContext = typeEvalContext;
            }

            NewStyleInspection(PyStringLiteralExpression myFormatExpression, Visitor myVisitor, TypeEvalContext myTypeEvalContext) {
                this(myFormatExpression.getText(), myFormatExpression, myVisitor, myTypeEvalContext);
            }

            NewStyleInspection(PyFStringFragmentFormatPart myFormatExpression, Visitor myVisitor, TypeEvalContext myTypeEvalContext) {
                this(myFormatExpression.getText(), myFormatExpression, myVisitor, myTypeEvalContext);
            }

            public boolean isProblem() {
                return myProblem;
            }

            public void inspect() {
                PyStringFormatParser parser = new PyStringFormatParser(myText);
                List<NewStyleSubstitutionChunk> chunks = new ArrayList<>();
                for (PyStringFormatParser.FormatStringChunk chunk : parser.parseNewStyle()) {
                    if (chunk instanceof NewStyleSubstitutionChunk newStyleChunk) {
                        chunks.add(newStyleChunk);
                    }
                }
                for (int i = 0; i < chunks.size(); i++) {
                    NewStyleSubstitutionChunk chunk = chunks.get(i);
                    NewStyleSubstitutionChunk prevChunk = i > 0 ? chunks.get(i - 1) : null;

                    if (prevChunk != null) {
                        if (prevChunk.getManualPosition() != null && chunk.getAutoPosition() != null) {
                            registerProblem(myProblemTarget, PyLocalize.inspManualToAutoFieldNumbering().get());
                        }
                        else if (prevChunk.getAutoPosition() != null && chunk.getManualPosition() != null) {
                            registerProblem(myProblemTarget, PyLocalize.inspAutoToManualFieldNumbering().get());
                        }
                    }
                    String mappingKey = inspectNewStyleChunkAndGetMappingKey(chunk);
                    PyStringLiteralExpression anchor = myProblemTarget instanceof PyStringLiteralExpression literal ? literal : null;
                    if (!myProblem && anchor != null) {
                        inspectArguments(chunk, mappingKey, anchor);
                    }
                }
            }

            public String inspectNewStyleChunkAndGetMappingKey(NewStyleSubstitutionChunk chunk) {
                Set<String> supportedTypes = new HashSet<>();
                boolean hasTypeOptions = false;

                String mappingKey = chunk.getMappingKey() != null ? chunk.getMappingKey() : String.valueOf(chunk.getPosition());

                // inspect options available only for numeric types
                if (chunk.hasSignOption() || chunk.useAlternateForm() || chunk.hasZeroPadding() || chunk.hasThousandsSeparator()) {
                    // TODO: not using "KNOWN_COMPLEX_TYPES" because some of those won't be available at runtime and -> Any, need to check QName
                    specifyTypes(supportedTypes, Set.of("complex"));
                    hasTypeOptions = true;
                }

                if (chunk.getPrecision() != null) {
                    // TODO: actually availableTypes doesn't reject int, because int is compatible with float and complex, need to check QName
                    specifyTypes(supportedTypes, Set.of(PyNames.TYPE_STR, PyNames.TYPE_FLOAT, PyNames.TYPE_COMPLEX));
                    hasTypeOptions = true;
                }

                char conversionType = chunk.getConversionType();
                if (conversionType != Character.MIN_VALUE) {
                    Set<String> conversion = NEW_STYLE_FORMAT_CONVERSIONS.get(conversionType);
                    if (conversion != null) {
                        specifyTypes(supportedTypes, conversion);
                        hasTypeOptions = true;
                    }
                    else {
                        registerProblem(myProblemTarget, PyInspectionMessages.problemMessage(
                            params -> PyLocalize.inspUnsupportedFormatCharacter(params[0]), conversionType));
                    }
                }

                if (!supportedTypes.isEmpty()) {
                    myFormatSpec.put(mappingKey, supportedTypes);
                }
                else if (hasTypeOptions) {
                    registerProblem(myProblemTarget, PyLocalize.inspIncompatibleOptions(mappingKey).get());
                }
                return mappingKey;
            }

            public void inspectArguments(NewStyleSubstitutionChunk chunk, String mappingKey, PyStringLiteralExpression anchor) {
                PsiElement target = new PySubstitutionChunkReference(anchor, chunk).resolve();
                boolean hasElementIndex = chunk.getMappingKeyElementIndex() != null;
                if (target == null) {
                    String chunkMapping = chunk.getMappingKey();
                    if (chunkMapping != null) {
                        registerProblem(
                            myProblemTarget,
                            hasElementIndex
                                ? PyInspectionMessages.problemMessage(params -> PyLocalize.inspTooFewArgsForFmtString())
                                : PyInspectionMessages.problemMessage(
                                params -> PyLocalize.inspStrFormatKeyHasNoArgument(params[0]),
                                chunkMapping
                            )
                        );
                    }
                    else {
                        registerProblem(myProblemTarget, PyLocalize.inspTooFewArgsForFmtString().get());
                    }
                }
                else {
                    checkTypesCompatibleForCheckedTypesOnly(myProblemTarget, target, mappingKey);
                }
            }

            public void registerProblem(PsiElement problemTarget, String message) {
                myProblem = true;
                myVisitor.registerProblem(problemTarget, message);
            }

            public void registerProblem(PsiElement problemTarget, ProblemMessage message) {
                myProblem = true;
                myVisitor.registerProblem(problemTarget, message);
            }

            public void checkTypesCompatibleForCheckedTypesOnly(PyElement anchor, PsiElement target, String mappingKey) {
                if (!(target instanceof PyTypedElement typedElement)) {
                    return;
                }
                Set<String> formatSpec = myFormatSpec.get(mappingKey);
                if (formatSpec == null) {
                    return;
                }
                String type = String.join(" or ", formatSpec);
                PyType actual = PyUnionType.toNonWeakType(myTypeEvalContext.getType(typedElement));
                if (actual == null) {
                    return;
                }
                PyType expected = PyLegacyDocstringTypeParser.getTypeByName(anchor, type);
                if (expected == null) {
                    return;
                }
                if (PyTypeUtil.asUnionSequence(actual).stream().allMatch(it ->
                    KNOWN_FORMAT_MINI_LANGUAGE_TYPES.contains(it instanceof PyClassType classType ? classType.getClassQName() : null))
                    && !match(expected, actual, myTypeEvalContext)) {
                    registerProblem(typedElement, PyLocalize.inspStrFormatUnexpectedArgumentType(actual.getName()).get());
                }
            }

            private static void specifyTypes(Set<String> types, Set<String> supportedTypes) {
                if (types.isEmpty()) {
                    types.addAll(supportedTypes);
                }
                else {
                    types.retainAll(supportedTypes);
                }
            }
        }

        @Override
        public void visitPyBinaryExpression(PyBinaryExpression node) {
            if (node.getLeftExpression() instanceof PyStringLiteralExpression leftLiteral && node.isOperator("%")) {
                Inspection inspection = new Inspection(this, myTypeEvalContext);
                inspection.inspectPercentFormat(leftLiteral);
                if (inspection.isProblem()) {
                    return;
                }
                inspection.inspectValues(node.getRightExpression());
            }
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            PyExpression callee = node.getCallee();
            if (callee != null && callee.getName() != null && callee.getName().equals(PyNames.FORMAT)) {
                PyStringLiteralExpression literalExpression =
                    PsiTreeUtil.getChildOfType(callee, PyStringLiteralExpression.class);
                if (literalExpression != null) {
                    NewStyleInspection inspection = new NewStyleInspection(literalExpression, this, myTypeEvalContext);
                    inspection.inspect();
                }
            }
        }

        @Override
        public void visitPyFStringFragment(PyFStringFragment node) {
            PyFStringFragmentFormatPart formatPart = node.getFormatPart();
            if (formatPart == null) {
                return;
            }
            PyExpression expression = node.getExpression();
            if (expression == null) {
                return;
            }

            String formatSpecText = formatPart.getText();
            if (formatSpecText.isEmpty()) {
                return;
            }
            PyType actualType = expression.getType(myTypeEvalContext);

            if (node.getTypeConversion() == null) {
                PyType hasObjectFormat = null;
                boolean found = false;
                for (PyType it : PyTypeUtil.asUnionSequence(actualType)) {
                    boolean matches;
                    if (isAnyOrUnknown(it) || hasWellKnownFormatMethod(it, expression, myTypeEvalContext)) {
                        matches = false;
                    }
                    else if (it instanceof PyClassType classType) {
                        PyClass pyClass = classType.getPyClass();
                        // Type stubs (.pyi) frequently omit __str__, __repr__, and __format__ even when the runtime
                        // .py module defines them, so fall back to the implementation class to avoid false positives.
                        PyClass implementation = PyiUtil.getOriginalElementOrLeaveAsIs(pyClass, PyClass.class);
                        PyFunction implementationMethod = implementation.findMethodInImplementations(PyNames.DUNDER_FORMAT, myTypeEvalContext);
                        if (implementationMethod == null) {
                            matches = true;
                        }
                        else {
                            matches = Objects.equals(implementationMethod.getQualifiedName(), PyNames.FQN.OBJECT + "." + PyNames.DUNDER_FORMAT);
                        }
                    }
                    else {
                        matches = true;
                    }
                    if (matches) {
                        hasObjectFormat = it;
                        found = true;
                        break;
                    }
                }
                if (found) {
                    if (hasObjectFormat instanceof PyClassType objectFormatClassType) {
                        registerProblem(formatPart, PyInspectionMessages.problemMessage(
                                            params -> PyLocalize.inspStrFormatDefaultObjectFormat(params[0]),
                                            PyInspectionMessages.CodifiedParam.ofType(actualType, formatPart, myTypeEvalContext)),
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                        new PyAddDunderMethodQuickFix(objectFormatClassType.getPyClass(), PyNames.DUNDER_FORMAT));
                    }
                    else {
                        registerProblem(formatPart, PyInspectionMessages.problemMessage(
                            params -> PyLocalize.inspStrFormatDefaultObjectFormat(params[0]),
                            PyInspectionMessages.CodifiedParam.ofType(actualType, formatPart, myTypeEvalContext)));
                    }
                    return;
                }
            }

            if (PyTypeUtil.asUnionSequence(actualType).stream().noneMatch(it -> hasWellKnownFormatMethod(it, expression, myTypeEvalContext))) {
                return;
            }

            NewStyleInspection inspection = new NewStyleInspection(formatPart, this, myTypeEvalContext);
            inspection.inspect();

            // If the format spec embeds nested expressions like `{width}`, we cannot statically know
            // what its final form looks like; skip the type-char and "invalid spec" checks.
            if (PsiTreeUtil.findChildOfType(formatPart, PyFStringFragment.class) != null) {
                return;
            }

            String trimmedSpec = formatSpecText.stripTrailing();
            if (trimmedSpec.isEmpty()) {
                return;
            }
            char typeChar = trimmedSpec.charAt(trimmedSpec.length() - 1);

            Set<String> expectedTypeNames = NEW_STYLE_FORMAT_CONVERSIONS.get(typeChar);
            if (expectedTypeNames != null) {
                checkFStringComponentType(node, expression, formatPart, typeChar, trimmedSpec.length() - 1,
                                          allowedFormatOverrideQNames(typeChar));
                return;
            }

            if (!VALID_NON_TYPE_FORMAT_SPEC_ENDINGS.contains(typeChar)) {
                PsiElement typeCharElement = formatPart.findElementAt(trimmedSpec.length() - 1);
                if (typeCharElement == null) {
                    typeCharElement = formatPart;
                }
                registerProblem(typeCharElement, PyInspectionMessages.problemMessage(
                    params -> PyLocalize.inspStrFormatInvalidFormatSpec(params[0]), typeChar));
                return;
            }

            // No presentation type char. The only type-restricting parts left are the options that are valid
            // for numeric values only (sign, alternate form, zero padding, thousands separator).
            NumericOption option = firstNumericOnlyOption(trimmedSpec);
            if (option == null) {
                return;
            }
            checkFStringComponentType(node, expression, formatPart, option.optionChar(), option.optionOffset(),
                                      KNOWN_COMPLEX_TYPES);
        }

        /**
         * Reports {@code expression} when its type is incompatible with a format component, mirroring the logic used for both
         * presentation type chars and numeric-only options. {@code allowedQNames} are the fully qualified names of types whose
         * {@code __format__} override is trusted to accept the component (e.g. {@code decimal.Decimal}, numpy scalars).
         * The problem is highlighted at {@code reportOffset} within the format part and names {@code reportChar}.
         */
        private void checkFStringComponentType(
            PyFStringFragment node,
            PyExpression expression,
            PyFStringFragmentFormatPart formatPart,
            char reportChar,
            int reportOffset,
            Set<String> allowedQNames
        ) {
            PyType actual;
            if (node.getTypeConversion() != null) {
                actual = PyLegacyDocstringTypeParser.getTypeByName(expression, PyNames.TYPE_STR, myTypeEvalContext);
            }
            else {
                actual = PyUnionType.toNonWeakType(myTypeEvalContext.getType(expression));
            }
            if (actual == null) {
                return;
            }

            List<PyType> incompatibleTypes = new ArrayList<>();
            for (PyType member : PyTypeUtil.asUnionSequence(actual)) {
                if (member != null
                    && (hasWellKnownFormatMethod(member, expression, myTypeEvalContext)
                    || isNoneType(member))
                    && !isAllowedFormatOverride(member, allowedQNames, myTypeEvalContext)) {
                    incompatibleTypes.add(member);
                }
            }
            if (incompatibleTypes.isEmpty()) {
                return;
            }

            PsiElement reportElement = formatPart.findElementAt(reportOffset);
            if (reportElement == null) {
                reportElement = formatPart;
            }
            String incompatibleTypeName = incompatibleTypes.stream()
                .map(it -> PythonDocumentationProvider.getTypeName(PyLiteralType.upcastLiteralToClass(it), myTypeEvalContext))
                .collect(Collectors.joining(" | "));
            registerProblem(reportElement, PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspStrFormatCodeNotSupported(params[0], params[1]), reportChar, incompatibleTypeName));
        }

        /**
         * Returns true when this type's formatting is one we model and can validate format codes against. That is the
         * case when it is a type we explicitly know (a builtin such as {@code str}/{@code int}/{@code float}/{@code complex}, or a known numeric
         * library type listed in {@code KNOWN_FORMAT_MINI_LANGUAGE_TYPES} such as {@code decimal.Decimal}, {@code fractions.Fraction}, numpy scalars), or when
         * it is a subclass that inherits {@code __format__} from one of those well-known classes (e.g. {@code class MyInt(int)}).
         * <p>
         * It returns false for a type that supplies its own {@code __format__} not backed by a well-known class (a user class,
         * or a subclass that redefines {@code __format__}): we cannot statically know which format specs the override accepts,
         * so we trust it. It also returns false for a type that merely inherits {@code object.__format__}, which accepts only an
         * empty spec — a separate concern from format-code mismatches.
         */
        private static boolean hasWellKnownFormatMethod(@Nullable PyType type, PyExpression location, TypeEvalContext context) {
            if (type == null) {
                return false;
            }
            if (!(type instanceof PyClassType classType)) {
                return false;
            }
            if (KNOWN_FORMAT_MINI_LANGUAGE_TYPES.contains(classType.getPyClass().getQualifiedName())) {
                return true;
            }
            PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
            List<? extends RatedResolveResult> results =
                classType.resolveMember(PyNames.DUNDER_FORMAT, location, AccessDirection.READ, resolveContext);
            if (results == null) {
                return false;
            }
            for (RatedResolveResult result : results) {
                if (result.getElement() instanceof PyFunction function) {
                    PyClass containingClass = function.getContainingClass();
                    String qualifiedName = containingClass != null ? containingClass.getQualifiedName() : null;
                    if (qualifiedName != null && KNOWN_FORMAT_MINI_LANGUAGE_TYPES.contains(qualifiedName)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public static int inspectCallExpression(
            PyCallExpression callExpression,
            PyResolveContext resolveContext,
            TypeEvalContext evalContext
        ) {
            IntSummaryStatistics statistics = callExpression.multiResolveCallee(resolveContext)
                .stream()
                .map(callableType -> {
                    List<PyCallableArgument> arguments = new ArrayList<>();
                    for (PyExpression argument : callExpression.getArguments()) {
                        arguments.add(new PyCallableArgument(argument));
                    }
                    return callableType.getCallType(evalContext, callExpression, arguments);
                })
                .collect(Collectors.summarizingInt(callType -> {
                    if (isNoneType(callType)) {
                        return 1;
                    }
                    else if (callType instanceof PyClassType classType) {
                        return countElements(classType, evalContext);
                    }
                    else if (callType instanceof PyUnionType unionType) {
                        int maxNumber = 1;
                        boolean allForSure = true;
                        for (PyType member : unionType.getMembers()) {
                            if (member instanceof PyClassType classType) {
                                int elementsCount = countElements(classType, evalContext);
                                allForSure = allForSure && elementsCount != -1;
                                maxNumber = Math.max(maxNumber, elementsCount);
                            }
                            else if (!isNoneType(member)) {
                                allForSure = false;
                            }
                        }
                        return allForSure ? maxNumber : -1;
                    }
                    else {
                        return -1;
                    }
                }));

            return statistics.getMin() == statistics.getMax() ? statistics.getMin() : -1;
        }

        private static int countElements(PyClassType classType, TypeEvalContext evalContext) {
            if (!classType.getPyClass().isSubclass(PyNames.FQN.TUPLE, evalContext)) {
                return 1;
            }
            else if (classType instanceof PyTupleType tupleType) {
                return tupleType.getElementCount();
            }
            else {
                return -1;
            }
        }
    }

    private static final Set<Character> VALID_NON_TYPE_FORMAT_SPEC_ENDINGS = createValidNonTypeFormatSpecEndings();

    private static Set<Character> createValidNonTypeFormatSpecEndings() {
        Set<Character> result = new HashSet<>();
        for (char c = '0'; c <= '9'; c++) {
            result.add(c);
        }
        result.addAll(Set.of('<', '>', '^', '=', '+', '-', ' ', '_', ',', '#', 'z'));
        return result;
    }

    private static final Map<Character, Set<String>> NEW_STYLE_FORMAT_CONVERSIONS = Map.ofEntries(
        Map.entry('s', Set.of("str")),
        Map.entry('b', Set.of("int")),
        Map.entry('c', Set.of("int")),
        Map.entry('d', Set.of("int")),
        Map.entry('o', Set.of("int")),
        Map.entry('x', Set.of("int")),
        Map.entry('X', Set.of("int")),
        Map.entry('n', Set.of("int", "float", "complex")),
        Map.entry('e', Set.of("int", "float", "complex")),
        Map.entry('E', Set.of("int", "float", "complex")),
        Map.entry('f', Set.of("int", "float", "complex")),
        Map.entry('F', Set.of("int", "float", "complex")),
        Map.entry('g', Set.of("int", "float", "complex")),
        Map.entry('G', Set.of("int", "float", "complex")),
        Map.entry('%', Set.of("int", "float"))
    );

    /**
     * Fully qualified names of types whose {@code __format__} override is trusted to accept the given presentation {@code typeChar},
     * even though the type is not one of the resolvable builtins (e.g. {@code decimal.Decimal}, {@code fractions.Fraction}, numpy scalars).
     */
    private static Set<String> allowedFormatOverrideQNames(char typeChar) {
        return switch (typeChar) {
            case 'b', 'c', 'd', 'o', 'x', 'X' -> KNOWN_INT_TYPES;
            case 'e', 'E', 'f', 'F', 'g', 'G', 'n' -> KNOWN_COMPLEX_TYPES;
            case '%' -> KNOWN_DECIMAL_TYPES;
            case 's' -> Set.of(PyNames.FQN.STR);
            default -> Set.of();
        };
    }

    private static final String NEW_STYLE_ALIGN_SYMBOLS = "<>=^";
    private static final String NEW_STYLE_SIGN_SYMBOLS = "+- ";

    private record NumericOption(char optionChar, int optionOffset) {
    }

    /**
     * Locates the first option in a trimmed, non-nested f-string format part that is valid for numeric values only:
     * a sign ({@code +}, {@code -}, space), the alternate form {@code #}, zero padding {@code 0}, or the thousands separator {@code ,}.
     * {@code spec} is the format part text, which starts with the {@code :} separator. After skipping it, the spec is matched against
     * the standard mini-language order (fill/align, sign, alternate form, zero padding, width, grouping, precision, type),
     * so that, e.g., a leading {@code 0} is recognized as zero padding while {@code 0} inside a width is not, and a {@code +} used as a fill
     * character is ignored.
     * Returns the option character together with its offset within {@code spec}, or {@code null} if there is no such option.
     */
    private static @Nullable NumericOption firstNumericOnlyOption(String spec) {
        // Skip the leading ':' separator that the format part text always starts with.
        int i = spec.startsWith(":") ? 1 : 0;
        // [fill]align
        if (i + 1 < spec.length() && NEW_STYLE_ALIGN_SYMBOLS.indexOf(spec.charAt(i + 1)) >= 0) {
            i += 2;
        }
        else if (i < spec.length() && NEW_STYLE_ALIGN_SYMBOLS.indexOf(spec.charAt(i)) >= 0) {
            i += 1;
        }
        if (i < spec.length() && NEW_STYLE_SIGN_SYMBOLS.indexOf(spec.charAt(i)) >= 0) {
            return new NumericOption(spec.charAt(i), i);
        }
        if (i < spec.length() && spec.charAt(i) == '#') {
            return new NumericOption('#', i);
        }
        if (i < spec.length() && spec.charAt(i) == '0') {
            return new NumericOption('0', i);
        }
        while (i < spec.length() && Character.isDigit(spec.charAt(i))) {
            i++;
        }
        if (i < spec.length() && spec.charAt(i) == ',') {
            return new NumericOption(',', i);
        }
        return null;
    }
}
