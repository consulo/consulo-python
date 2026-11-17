// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.stdlib.PyStdlibTypeProvider;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyDisjointBaseUtil;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.psi.types.PyIntersectionType;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyNoneTypeKt;
import com.jetbrains.python.impl.psi.types.PyStructuralType;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.util.registry.Registry;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.Stack;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class PyTypeAssertionEvaluator extends PyRecursiveElementVisitor {
    private final Stack<Assertion> myStack = new Stack<>();
    private boolean myPositive;

    public PyTypeAssertionEvaluator(boolean positive) {
        myPositive = positive;
    }

    public List<Assertion> getDefinitions() {
        return myStack;
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
        if (node.isCalleeText(PyNames.ISINSTANCE, PyNames.ASSERT_IS_INSTANCE)) {
            PyExpression[] args = node.getArguments();
            if (args.length == 2) {
                PyExpression typeElement = args[1];

                pushAssertion(args[0], myPositive, context -> {
                    if (myPositive || isSafeForNegativeAssertion(typeElement, context)) {
                        return transformTypeFromAssertion(
                            context.getType(typeElement),
                            false,
                            context,
                            typeElement
                        );
                    }
                    else {
                        return null;
                    }
                });
            }
        }
        else if (node.isCalleeText(PyNames.ISSUBCLASS)) {
            PyExpression[] args = node.getArguments();
            if (args.length == 2) {
                PyExpression typeElement = args[1];

                pushAssertion(args[0], myPositive, context -> {
                    if (myPositive || isSafeForNegativeAssertion(typeElement, context)) {
                        return transformTypeFromAssertion(
                            context.getType(typeElement),
                            true,
                            context,
                            typeElement
                        );
                    }
                    else {
                        return null;
                    }
                });
            }
        }
    }

    private void visitExpressionInCondition(PyExpression node) {
        if (myPositive && isReferenceInTruthyCondition(node)) {
            // TODO: we can actually check if the class defines __bool__ or __len__, and use it to exclude the type
            // we could not suggest `None` because it could be a reference to an empty collection
            // so we could push only non-`None` assertions
            pushAssertion(node, !myPositive, context -> PyBuiltinCache.getInstance(node).getNoneType());
        }
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
        visitExpressionInCondition(node);
        super.visitPyReferenceExpression(node);
    }

    @Override
    public void visitPyAssignmentExpression(PyAssignmentExpression node) {
        visitExpressionInCondition(node);
        super.visitPyAssignmentExpression(node);
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
        PyExpression lhs = PyPsiUtils.flattenParens(node.getLeftExpression());
        PyExpression rhs = PyPsiUtils.flattenParens(node.getRightExpression());
        if (lhs == null || rhs == null) {
            return;
        }

        PyElementType operator = node.getOperator();
        boolean isOrEqualsOperator = node.isOperator(PyNames.IS) || PyTokenTypes.EQEQ == operator;
        if (isOrEqualsOperator || node.isOperator("isnot") || PyTokenTypes.NE == operator || PyTokenTypes.NE_OLD == operator) {
            setPositive(isOrEqualsOperator, () -> processIsOrEquals(lhs, rhs));
        }

        if (PyTokenTypes.IN_KEYWORD == operator || node.isOperator("notin")) {
            setPositive(PyTokenTypes.IN_KEYWORD == operator, () -> processIn(lhs, rhs));
        }
    }

    private void processIsOrEquals(PyExpression lhs, PyExpression rhs) {
        Boolean leftBoolean = PyEvaluator.evaluateNoResolve(lhs, boolean.class);
        if (leftBoolean != null) {
            setPositive(leftBoolean, () -> rhs.accept(this));
            return;
        }

        Boolean rightBoolean = PyEvaluator.evaluateNoResolve(rhs, boolean.class);
        if (rightBoolean != null) {
            setPositive(rightBoolean, () -> lhs.accept(this));
            return;
        }

        if (PyLiteralType.isNone(lhs)) {
            pushAssertion(rhs, myPositive, context -> PyBuiltinCache.getInstance(rhs).getNoneType());
            return;
        }

        if (PyLiteralType.isNone(rhs)) {
            pushAssertion(lhs, myPositive, context -> PyBuiltinCache.getInstance(lhs).getNoneType());
            return;
        }

        // narrow `x` itself on `type(x) is C` / `x.__class__ is C` (and the symmetric operand order).
        PyExpression lhsClassSubject = getRuntimeClassSubject(lhs);
        if (lhsClassSubject != null) {
            pushRuntimeClassAssertion(lhsClassSubject, rhs);
            return;
        }
        PyExpression rhsClassSubject = getRuntimeClassSubject(rhs);
        if (rhsClassSubject != null) {
            pushRuntimeClassAssertion(rhsClassSubject, lhs);
            return;
        }

        boolean positive = myPositive;
        pushAssertion(lhs, myPositive, context -> {
            PyType type = getLiteralType(rhs, context);
            return positive || type instanceof PyLiteralType ? type : null;
        });
    }

    /**
     * If {@code expr} denotes the runtime class of a value — {@code type(x)} or {@code x.__class__} — returns that value ({@code x}).
     * Comparing such an expression against a class object lets us narrow the value itself (PY-87917).
     */
    private static @Nullable PyExpression getRuntimeClassSubject(PyExpression expr) {
        if (expr instanceof PyCallExpression callExpression && callExpression.isCalleeText(PyNames.TYPE)) {
            PyExpression[] args = callExpression.getArguments();
            if (args.length == 1) {
                return args[0];
            }
        }
        if (expr instanceof PyReferenceExpression referenceExpression &&
            referenceExpression.isQualified() &&
            PyNames.__CLASS__.equals(referenceExpression.getReferencedName())) {
            return referenceExpression.getQualifier();
        }
        return null;
    }

    private void pushRuntimeClassAssertion(PyExpression subject, PyExpression classExpr) {
        boolean positive = myPositive;
        pushAssertion(subject, myPositive, context -> {
            // Only the positive edge is sound: `type(x) is not C` still permits subclasses of `C`,
            // so nothing useful can be excluded from `x` on the negative edge.
            return positive ? classObjectToInstanceType(context.getType(classExpr)) : null;
        });
    }

    private void processIn(PyExpression lhs, PyExpression rhs) {
        if (rhs instanceof PyTupleExpression || rhs instanceof PyListLiteralExpression || rhs instanceof PySetLiteralExpression) {
            boolean positive = myPositive;
            pushAssertion(lhs, myPositive, context -> {
                PyExpression[] elements = ((PySequenceExpression) rhs).getElements();
                List<@Nullable PyType> types = new ArrayList<>(elements.length);
                PyType noneType = PyBuiltinCache.getInstance(rhs).getNoneType();
                for (PyExpression element : elements) {
                    PyType type = PyLiteralType.isNone(element) ? noneType : getLiteralType(element, context);
                    if (type != null && (positive || type == noneType || type instanceof PyLiteralType)) {
                        types.add(type);
                    }
                }
                return types.isEmpty() ? null : PyUnionType.unionOrUnknown(types);
            });
        }
    }

    private void setPositive(boolean positive, Runnable runnable) {
        boolean oldPositive = myPositive;
        if (!positive) {
            myPositive = !myPositive;
        }
        try {
            runnable.run();
        }
        finally {
            myPositive = oldPositive;
        }
    }

    @Override
    public void visitPyCaseClause(PyCaseClause node) {
        PyPattern pattern = node.getPattern();
        if (pattern == null) {
            return;
        }
        if (node.getParent() instanceof PyMatchStatement matchStatement) {
            pushAssertion(matchStatement.getSubject(), myPositive, context -> context.getType(pattern));
        }
    }

    /**
     * Negative type assertion for when all cases fail
     */
    @Override
    public void visitPyMatchStatement(PyMatchStatement matchStatement) {
        assert !myPositive; // for match statement as a whole, only negative assertion can be made
        PyExpression subject = matchStatement.getSubject();
        if (subject == null) {
            return;
        }
        // allowAnyExpr is here because we need negative edges with Never even when subject is not reference expression
        pushAssertion(subject, true, true, true, context -> {
            List<PyCaseClause> clauses = matchStatement.getCaseClauses();
            if (!clauses.isEmpty()) {
                return clauses.get(clauses.size() - 1).getSubjectTypeAfter(context);
            }
            else {
                return null;
            }
        });
    }

    private void pushAssertion(@Nullable PyExpression expr,
                               boolean positive,
                               Function<TypeEvalContext, @Nullable PyType> suggestedType) {
        pushAssertion(expr, positive, false, isStrictNarrowingAllowed(), suggestedType);
    }

    private void pushAssertion(@Nullable PyExpression expression,
                               boolean positive,
                               boolean allowAnyExpr,
                               boolean forceStrictNarrow,
                               Function<TypeEvalContext, @Nullable PyType> suggestedType) {
        PyExpression expr = PyPsiUtils.flattenParens(expression);
        if (expr instanceof PySequenceExpression sequenceExpression) {
            PyExpression[] elements = sequenceExpression.getElements();
            for (int i = 0; i < elements.length; i++) {
                pushAssertion(elements[i], positive, allowAnyExpr, forceStrictNarrow, getIteratedType(suggestedType, i));
            }
        }
        else if (expr instanceof PyAssignmentExpression assignmentExpression) {
            pushAssertion(assignmentExpression.getTarget(), positive, allowAnyExpr, forceStrictNarrow, suggestedType);
        }
        else if (expr != null) {
            PyExpression target = expr;
            InstructionTypeCallback typeCallback = context -> createAssertionType(
                context.getType(target),
                suggestedType.apply(context),
                positive,
                forceStrictNarrow,
                context
            );

            if (expr instanceof PyReferenceExpression || expr instanceof PyTargetExpression) {
                myStack.push(new Assertion((PyQualifiedExpression) target, typeCallback));
            }
            else if (allowAnyExpr) {
                myStack.push(new Assertion(null, typeCallback));
            }
        }
    }

    public static class Assertion {
        public final @Nullable PyQualifiedExpression element;
        private final InstructionTypeCallback myTypeEvalFunction;

        public Assertion(@Nullable PyQualifiedExpression element, InstructionTypeCallback typeEvalFunction) {
            this.element = element;
            myTypeEvalFunction = typeEvalFunction;
        }

        public @Nullable PyQualifiedExpression getElement() {
            return element;
        }

        public InstructionTypeCallback getTypeEvalFunction() {
            return myTypeEvalFunction;
        }
    }

    private static boolean isSafeForNegativeAssertion(PyExpression expression, TypeEvalContext context) {
        List<PyExpression> elements = expandClassInfoExpressions(expression);
        return !elements.isEmpty() && elements.stream().allMatch(it -> isSafeClassInfoReference(it, context));
    }

    public static List<PyExpression> expandClassInfoExpressions(PyExpression expression) {
        PyExpression flattened = PyPsiUtils.flattenParens(expression);
        if (flattened == null) {
            return new ArrayList<>();
        }
        if (flattened instanceof PyTupleExpression tupleExpression) {
            List<PyExpression> result = new ArrayList<>();
            for (PyExpression element : tupleExpression.getElements()) {
                result.addAll(expandClassInfoExpressions(element));
            }
            return result;
        }
        // Keep in mind that `isinstance` will only accept `A | B` expression if all operands are classinfo, so no parameterized generics
        if (flattened instanceof PyBinaryExpression binaryExpression && binaryExpression.getOperator() == PyTokenTypes.OR) {
            PyExpression left = binaryExpression.getLeftExpression();
            PyExpression right = binaryExpression.getRightExpression();
            List<PyExpression> result = new ArrayList<>(expandClassInfoExpressions(left));
            if (right != null) {
                result.addAll(expandClassInfoExpressions(right));
            }
            return result;
        }
        List<PyExpression> result = new ArrayList<>();
        result.add(flattened);
        return result;
    }

    private static boolean isSafeClassInfoReference(PyExpression expression, TypeEvalContext context) {
        if (expression instanceof PyReferenceExpression referenceExpression) {
            // Here we check that the reference resolves to a class, not a target in assignment or function parameter.
            // This is done to avoid cases like Py3TypeTest.testIsInstanceNegativeNarrowing
            List<PsiElement> resolvedElements = PyUtil.multiResolveTopPriority(referenceExpression, PyResolveContext.defaultContext(context));
            return resolvedElements.size() == 1 && resolvedElements.get(0) instanceof PyClass;
        }
        return false;
    }

    /**
     * Converts a class-object type ({@code type} of some class) into the corresponding instance type, mapping over unions.
     * Returns {@code null} if {@code type} is not a class object (e.g. an instance type such as {@code None}),
     * so that comparisons like {@code x.__class__ is None} do not narrow {@code x}.
     */
    private static @Nullable PyType classObjectToInstanceType(@Nullable PyType type) {
        if (type instanceof PyUnionType unionType) {
            List<@Nullable PyType> members = new ArrayList<>();
            for (PyType member : unionType.getMembers()) {
                members.add(classObjectToInstanceType(member));
            }
            return members.stream().anyMatch(it -> it == null) ? null : PyUnionType.unionOrUnknown(members);
        }
        if (type instanceof PyInstantiableType<?> instantiableType) {
            return instantiableType.isDefinition() ? instantiableType.toInstance() : null;
        }
        return null;
    }

    private static @Nullable PyType getLiteralType(PyExpression element, TypeEvalContext context) {
        PyType type = PyLiteralType.getLiteralType(element, context);
        if (type == null) {
            type = context.getType(element);
        }
        return PyTypeUtil.toStream(type).allMatch(it -> it instanceof PyLiteralType) ? type : null;
    }

    public static @Nullable Ref<PyType> createAssertionType(@Nullable PyType initial,
                                                           @Nullable PyType suggested,
                                                           boolean positive,
                                                           boolean forceStrictNarrow,
                                                           TypeEvalContext context) {
        if (suggested == null) {
            return null;
        }
        if (positive) {
            // Find all initial type members that are subtypes of the suggested (more specific than the narrowing "suggested" type).
            // Imagine having `list[int] | int` narrowed by `list[Any]`.
            List<@Nullable PyType> initialSubtypes = PyTypeUtil.toStream(initial)
                .filter(initialSubtype -> match(suggested, initialSubtype, context))
                .toList();

            // Find all suggested subtype members that are subtypes of the initial (more specific than the initial type)
            // AND are not subtypes of those more specific initial types.
            // This is needed to support generics of `Any`, because `list[Any]` is both a subtype, and a supertype of `list[str]`.
            List<@Nullable PyType> suggestedSubtypes = PyTypeUtil.toStream(suggested)
                .filter(suggestedSubtype -> match(initial, suggestedSubtype, context))
                .filter(suggestedSubtype -> initialSubtypes.stream()
                    .noneMatch(initialSubtype -> match(initialSubtype, suggestedSubtype, context)))
                .toList();

            List<@Nullable PyType> types = new ArrayList<>(initialSubtypes);
            types.addAll(suggestedSubtypes);
            return new Ref<>(types.isEmpty() ? intersect(initial, suggested, context) : PyUnionType.unionOrUnknown(types));
        }
        else {
            if (initial instanceof PyUnionType unionType) {
                return new Ref<>(excludeFromUnion(unionType, suggested, context, forceStrictNarrow));
            }
            if (match(suggested, initial, context)) {
                return forceStrictNarrow || isStrictNarrowingAllowed() ? new Ref<>(PyNeverType.NEVER) : null;
            }
            Ref<PyType> diff = trySubtract(initial, suggested, context);
            return diff != null ? diff : new Ref<>(initial);
        }
    }

    private static @Nullable PyType excludeFromUnion(PyUnionType unionType,
                                                     @Nullable PyType type,
                                                     TypeEvalContext context,
                                                     boolean forceStrictNarrow) {
        List<@Nullable PyType> members = new ArrayList<>();
        for (PyType m : unionType.getMembers()) {
            Ref<PyType> diff = trySubtract(m, type, context);
            if (diff != null) {
                members.add(diff.get());
            }
            else if (!match(type, m, context)) {
                members.add(m);
            }
        }
        if ((forceStrictNarrow || isStrictNarrowingAllowed()) && members.isEmpty()) {
            return PyNeverType.NEVER;
        }
        return PyUnionType.unionOrUnknown(members);
    }

    public static boolean isStrictNarrowingAllowed() {
        return Registry.is("python.strict.type.narrow", true);
    }

    private static @Nullable Ref<PyType> trySubtract(@Nullable PyType type1,
                                                    @Nullable PyType type2,
                                                    TypeEvalContext context) {
        assert !(type1 instanceof PyUnionType);

        if (!(type1 instanceof PyLiteralType) &&
            type1 instanceof PyClassType classType &&
            !classType.isDefinition() &&
            PyStdlibTypeProvider.isCustomEnum(classType.getPyClass(), context)) {
            if (classType.getPyClass().getAncestorClasses(context)
                .stream()
                .anyMatch(cls -> PyNames.TYPE_ENUM_FLAG.equals(cls.getQualifiedName()))) {
                // Do not expand enum classes that derive from enum.Flag
                return null;
            }
            List<PyType> enumMembers = new ArrayList<>(PyStdlibTypeProvider.getEnumMembers(classType.getPyClass(), context).toList());
            List<@Nullable PyType> filteredEnumMembers = new ArrayList<>();
            for (PyType m : enumMembers) {
                if (!PyTypeChecker.match(type2, m, context)) {
                    filteredEnumMembers.add(m);
                }
            }
            if (filteredEnumMembers.isEmpty()) {
                return new Ref<>(PyNeverType.NEVER);
            }
            PyType type = enumMembers.size() == filteredEnumMembers.size() ? type1 : PyUnionType.unionOrUnknown(filteredEnumMembers);
            return new Ref<>(type);
        }
        return null;
    }


    private static boolean isFinal(PyClassType classType, TypeEvalContext context) {
        return PyKnownDecoratorUtil.knownDecoratorsOf(classType.getPyClass(), context)
            .containsAny(PyKnownDecorator.TYPING_FINAL, PyKnownDecorator.TYPING_FINAL_EXT);
    }

    private static boolean isLiteralOrNoneType(@Nullable PyType type) {
        return type instanceof PyLiteralType || PyNoneTypeKt.isNoneType(type);
    }

    private static @Nullable PyType intersect(@Nullable PyType initial,
                                              @Nullable PyType suggested,
                                              TypeEvalContext context) {
        if (isLiteralOrNoneType(initial) && PyTypeChecker.match(suggested, initial, context)) {
            return initial;
        }
        if (isLiteralOrNoneType(suggested) && PyTypeChecker.match(initial, suggested, context)) {
            return suggested;
        }

        if (initial instanceof PyClassType initialClassType && suggested instanceof PyClassType suggestedClassType) {
            if (PyDisjointBaseUtil.areDisjoint(initialClassType, suggestedClassType, context)) {
                return PyNeverType.NEVER;
            }
            if (isFinal(initialClassType, context) || isFinal(suggestedClassType, context)) {
                if (!PyTypeChecker.match(initial, suggested, context) && !PyTypeChecker.match(suggested, initial, context)) {
                    return PyNeverType.NEVER;
                }
            }
            return PyIntersectionType.intersectionOrTop(initial, suggested);
        }

        if (initial instanceof PyUnionType initialUnion && initialUnion.getMembers().size() <= 5) {
            return initialUnion.map(member -> intersect(member, suggested, context));
        }
        if (suggested instanceof PyUnionType suggestedUnion && suggestedUnion.getMembers().size() <= 5) {
            return suggestedUnion.map(member -> intersect(initial, member, context));
        }
        if (isLiteralOrNoneType(initial) || isLiteralOrNoneType(suggested)) {
            return PyNeverType.NEVER;
        }
        return PyIntersectionType.intersectionOrTop(initial, suggested);
    }

    private static boolean match(@Nullable PyType expected, @Nullable PyType actual, TypeEvalContext context) {
        return !(actual instanceof PyStructuralType) && !PyTypeChecker.isUnknown(actual, context) && !PyTypeUtil.inheritsAny(actual, context) &&
            PyTypeChecker.match(expected, actual, context);
    }

    /**
     * @param transformToDefinition if true the result type will be Type[T], not T itself.
     */
    private static @Nullable PyType transformTypeFromAssertion(@Nullable PyType type,
                                                               boolean transformToDefinition,
                                                               TypeEvalContext context,
                                                               @Nullable PyExpression typeElement) {
        /*
         * We need to distinguish:
         *   if isinstance(x, (int, str)):
         * And:
         *   if isinstance(x, (1, "")):
         */
        PyExpression typeElementNoParens = PyPsiUtils.flattenParens(typeElement);
        if (type instanceof PyTupleType tupleType) {
            List<@Nullable PyType> members = new ArrayList<>();
            int count = tupleType.getElementCount();

            if (typeElementNoParens instanceof PyTupleExpression tupleExpression && tupleExpression.getElements().length == count) {
                PyExpression[] elements = tupleExpression.getElements();
                for (int i = 0; i < count; i++) {
                    members.add(transformTypeFromAssertion(tupleType.getElementType(i), transformToDefinition, context, elements[i]));
                }
            }
            else {
                for (int i = 0; i < count; i++) {
                    members.add(transformTypeFromAssertion(tupleType.getElementType(i), transformToDefinition, context, null));
                }
            }

            return PyUnionType.unionOrUnknown(members);
        }
        else if (typeElementNoParens instanceof PyBinaryExpression binaryExpression && binaryExpression.getOperator() == PyTokenTypes.OR) {
            Ref<PyType> typeFromTypingProvider = PyTypingTypeProvider.getType(binaryExpression, context);
            if (typeFromTypingProvider != null) {
                return transformTypeFromAssertion(typeFromTypingProvider.get(), transformToDefinition, context, null);
            }
        }
        else if (type instanceof PyUnionType unionType) {
            return unionType.map(member -> transformTypeFromAssertion(member, transformToDefinition, context, null));
        }
        else if (type instanceof PyInstantiableType<?> instantiableType) {
            return transformToDefinition ? instantiableType.toClass() : instantiableType.toInstance();
        }
        return type;
    }

    private static Function<TypeEvalContext, @Nullable PyType> getIteratedType(Function<TypeEvalContext, @Nullable PyType> sequenceType,
                                                                               int index) {
        return context -> {
            PyType computedSuggestedType = sequenceType.apply(context);
            if (computedSuggestedType instanceof PyNeverType) {
                return computedSuggestedType;
            }
            if (computedSuggestedType instanceof PyTupleType tupleType) {
                return tupleType.getElementType(index);
            }
            return null;
        };
    }

    private static @Nullable PsiElement skipNotAndParens(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }
        PsiElement e = element.getParent();
        while (e != null) {
            if (!(e instanceof PyParenthesizedExpression) &&
                !(e instanceof PyPrefixExpression prefixExpression && prefixExpression.getOperator() == PyTokenTypes.NOT_KEYWORD)) {
                return e;
            }
            e = e.getParent();
        }
        return null;
    }

    private static boolean isReferenceInTruthyCondition(PyExpression node) {
        PsiElement parent = skipNotAndParens(node);
        if (parent instanceof PyConditionalStatementPart) {
            return true;
        }
        if (parent instanceof PyConditionalExpression conditionalExpression &&
            PsiTreeUtil.isAncestor(conditionalExpression.getCondition(), node, false)) {
            return true;
        }
        if (parent instanceof PyBinaryExpression binaryExpression &&
            (binaryExpression.isOperator(PyNames.AND) || binaryExpression.isOperator(PyNames.OR))) {
            return true;
        }
        if (parent instanceof PyAssertStatement) {
            return true;
        }
        if (parent instanceof PyGeneratorExpression generatorExpression &&
            generatorExpression.getIfComponents().stream().anyMatch(it -> PsiTreeUtil.isAncestor(it.getTest(), node, false))) {
            return true;
        }
        return false;
    }
}
