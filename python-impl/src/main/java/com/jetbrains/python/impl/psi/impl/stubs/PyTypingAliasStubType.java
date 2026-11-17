// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStubType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.impl.psi.ASTDelegatePsiElement;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.ast.TokenSet;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub.InitializerType;
import com.jetbrains.python.impl.psi.stubs.PyTypingAliasStub;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl(id = "typingAliasStub", order = "after typedDictStub")
public final class PyTypingAliasStubType extends CustomTargetExpressionStubType<PyTypingAliasStub> {
    private static final int STRING_LITERAL_LENGTH_THRESHOLD = 100;
    private static final Key<CachedValue<PyExpression>> ASSIGNED_VALUE_FROM_STUB = Key.create("PyTypingAliasStubType.assignedValueFromStub");
    private static final Key<CachedValue<PyExpression>> ASSIGNED_VALUE_FROM_AST = Key.create("PyTypingAliasStubType.assignedValueFromAst");

    public static final Pattern RE_TYPE_HINT_LIKE_STRING = Pattern.compile(
        """
            (?x)
            \\s*
            \\S+(\\[.*])?   # initial type like: "list[int]"
            (\\s*[|&]\\s*   # union or intersection operators: " | " or " & "
                \\S+(\\[.*])? # operand types
            )*              # repeating
            \\s*
            """,
        Pattern.DOTALL
    );

    private static final TokenSet VALID_TYPE_ANNOTATION_ELEMENTS = TokenSet.create(PyElementTypes.REFERENCE_EXPRESSION,
                                                                                   PyElementTypes.SUBSCRIPTION_EXPRESSION,
                                                                                   PyElementTypes.TUPLE_EXPRESSION,
                                                                                   // List of types is allowed only inside Callable[...]
                                                                                   PyElementTypes.LIST_LITERAL_EXPRESSION,
                                                                                   PyElementTypes.STRING_LITERAL_EXPRESSION,
                                                                                   PyElementTypes.NONE_LITERAL_EXPRESSION,
                                                                                   PyElementTypes.ELLIPSIS_LITERAL_EXPRESSION,
                                                                                   PyElementTypes.STAR_EXPRESSION);

    @Override
    public @Nullable PyTypingAliasStub createStub(PyTargetExpression psi) {
        PyExpression value = getAssignedValueIfTypeAliasLike(psi, true);
        return value != null ? new PyTypingTypeAliasStubImpl(value.getText()) : null;
    }

    /**
     * Whether the reference that is assigned to the target is the value of a type alias.
     * {@link PyTargetExpressionStub#isTypeAliasLikeReference()} keeps this result,
     * so that {@link #getAssignedValueStubLike} applies the same rules with the stub and with the AST.
     */
    static boolean isTypeAliasLikeReference(PyTargetExpression target) {
        return getAssignedValueIfTypeAliasLike(target, false) instanceof PyReferenceExpression;
    }

    private static @Nullable PyExpression getAssignedValueIfTypeAliasLike(PyTargetExpression target, boolean forStubCreation) {
        if (!(PyUtil.isTopLevel(target) || ScopeUtil.getScopeOwner(target) instanceof PyClass) || !looksLikeTypeAliasTarget(target)) {
            return null;
        }
        PyExpression value = target.findAssignedValue();
        PyExpression unwrappedValue = unwrapParentheses(value);

        // Plain reference expressions are handled by PyTargetExpressionStub.getInitializer()
        // when initializer type is ReferenceExpression. We don't want to override these stubs,
        // so as not to break existing resolve functionality (see PyTargetExpression.getAssignedQName()).
        if (unwrappedValue == null || forStubCreation && value instanceof PyReferenceExpression) {
            return null;
        }

        if (isExplicitTypeAlias(target)) {
            return value;
        }
        // Typing specification doesn't allow fully quoted values of implicit type aliases
        // because they ambiguous with ordinary top-level string variables.
        // For instance:
        //
        // FOO = "int | str"
        //
        // is not considered a valid type alias, even though the string content looks like a type hint.
        // See "BadTypeAlias14" in aliases_implicit.py of the conformance test suite.
        if (target.getAnnotation() == null && !(unwrappedValue instanceof PyStringLiteralExpression) && looksLikeTypeHint(unwrappedValue)) {
            return value;
        }
        return null;
    }

    /**
     * Unwraps a value such as {@code (int | str)}. A parenthesized tuple stays as it is, because a tuple is not a type expression.
     * The stub keeps the text with the parentheses, because the inner text can span lines and hold comments.
     */
    private static @Nullable PyExpression unwrapParentheses(@Nullable PyExpression value) {
        if (!(value instanceof PyParenthesizedExpression)) {
            return value;
        }
        PyExpression contained = PyPsiUtils.flattenParens(value);
        return contained instanceof PyTupleExpression ? value : contained;
    }

    private static boolean looksLikeTypeAliasTarget(PyTargetExpression target) {
        if (target.isQualified()) {
            return false;
        }
        String name = target.getName();
        if (name == null || PyUtilCore.isSpecialName(name)) {
            return false;
        }
        PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(target, PyAssignmentStatement.class);
        if (assignment == null) {
            return false;
        }
        PyExpression[] targets = assignment.getRawTargets();
        return targets.length == 1 && targets[0] == target;
    }

    private static boolean isExplicitTypeAlias(PyTargetExpression target) {
        PyAnnotation annotation = target.getAnnotation();
        if (annotation != null) {
            PyExpression value = annotation.getValue();
            if (value instanceof PyReferenceExpression referenceExpression) {
                return PyResolveUtil.resolveImportedElementQNameLocally(referenceExpression).stream()
                    .map(QualifiedName::toString)
                    .anyMatch(name -> name.equals(PyTypingTypeProvider.TYPE_ALIAS) || name.equals(PyTypingTypeProvider.TYPE_ALIAS_EXT));
            }
        }
        else {
            String typeHintText = StringUtil.notNullize(target.getTypeCommentAnnotation());
            return typeHintText.equals("TypeAlias") || typeHintText.endsWith(".TypeAlias");
        }
        return false;
    }

    public static boolean looksLikeTypeHint(PyExpression expression) {
        PyCallExpression call = as(expression, PyCallExpression.class);
        if (call != null) {
            PyReferenceExpression callee = as(call.getCallee(), PyReferenceExpression.class);
            return callee != null &&
                   ("TypeVar".equals(callee.getReferencedName()) || "TypeVarTuple".equals(callee.getReferencedName()) ||
                    "ParamSpec".equals(callee.getReferencedName()) || "TypeAliasType".equals(callee.getReferencedName()));
        }
        return isSyntacticallyValidAnnotation(expression);
    }

    private static boolean isSyntacticallyValidAnnotation(PyExpression expression) {
        boolean[] illegal = {false};
        expression.accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPySubscriptionExpression(PySubscriptionExpression node) {
                if (node.getOperand() instanceof PyReferenceExpression refExpr &&
                    "Annotated".equals(refExpr.getName()) &&
                    node.getIndexExpression() instanceof PyTupleExpression tupleExpr) {
                    refExpr.accept(this);
                    tupleExpr.getElements()[0].accept(this);
                }
                else if (node.getOperand() instanceof PyReferenceExpression refExpr &&
                         "Literal".equals(refExpr.getName())) {
                    refExpr.accept(this);
                }
                else {
                    super.visitPySubscriptionExpression(node);
                }
            }

            @Override
            public void visitPyBinaryExpression(PyBinaryExpression node) {
                if (!(node.getOperator() == PyTokenTypes.OR || node.getOperator() == PyTokenTypes.AND)) {
                    illegal[0] = true;
                    return;
                }
                node.getLeftExpression().accept(this);
                PyExpression rightExpression = node.getRightExpression();
                if (rightExpression != null) {
                    rightExpression.accept(this);
                }
            }

            @Override
            public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
                boolean nonTrivial = (node.isInterpolated()
                                      || node.getStringNodes().size() != 1
                                      || node.getTextLength() > STRING_LITERAL_LENGTH_THRESHOLD
                                      || !node.getStringElements().getFirst().getPrefix().isEmpty()
                                      || !RE_TYPE_HINT_LIKE_STRING.matcher(node.getStringValue()).matches());
                if (nonTrivial) {
                    illegal[0] = true;
                }
            }

            @Override
            public void visitPyReferenceExpression(PyReferenceExpression node) {
                if (node.asQualifiedName() == null) {
                    illegal[0] = true;
                }
            }

            @Override
            public void visitPyListLiteralExpression(PyListLiteralExpression node) {
                if (node == expression) {
                    illegal[0] = true;
                    return;
                }
                super.visitElement(node);
            }

            @Override
            public void visitElement(PsiElement element) {
                if (element instanceof ASTDelegatePsiElement) {
                    if (!VALID_TYPE_ANNOTATION_ELEMENTS.contains(element.getNode().getElementType())) {
                        illegal[0] = true;
                        return;
                    }
                    super.visitElement(element);
                }
            }
        });
        return !illegal[0];
    }

    @Override
    public @Nullable PyTypingAliasStub deserializeStub(StubInputStream stream) throws IOException {
        String ref = stream.readNameString();
        return ref != null ? new PyTypingTypeAliasStubImpl(ref) : null;
    }

    /**
     * If the target expression's stub is present and the right hand value of the assignment is treated as
     * a valid type alias, retrieve its text from the stub, create {@link PyExpressionCodeFragment} from it,
     * and return the contained expression. Otherwise, get the assigned value from AST directly
     * but process it in the same way as if it was going to be saved in the stub.
     *
     * @see PyTypingAliasStub
     */
    public static @Nullable PyExpression getAssignedValueStubLike(PyTargetExpression target) {
        // The stub and the AST give different expressions, so each one has its own cached value.
        // A value from the stub must not stay in use after the AST is loaded.
        Key<CachedValue<PyExpression>> key = target.getStub() != null ? ASSIGNED_VALUE_FROM_STUB : ASSIGNED_VALUE_FROM_AST;
        return LanguageCachedValueUtil.getCachedValue(target, key, () -> CachedValueProvider.Result.create(
            computeAssignedValueStubLike(target), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static @Nullable PyExpression computeAssignedValueStubLike(PyTargetExpression target) {
        PyTargetExpressionStub stub = target.getStub();
        if (stub != null) {
            PyTypingAliasStub aliasStub = stub.getCustomStub(PyTypingAliasStub.class);
            String aliasText = null;
            if (aliasStub != null) {
                aliasText = aliasStub.getText();
            }
            else if (stub.getInitializerType() == InitializerType.ReferenceExpression && stub.isTypeAliasLikeReference()) {
                aliasText = Objects.toString(stub.getInitializer(), null);
            }
            return aliasText != null ? unwrapParentheses(PyUtil.createExpressionFromFragment(aliasText, target)) : null;
        }
        // Use PSI to get the assigned value but only if the same expression would be saved in stubs
        return unwrapParentheses(getAssignedValueIfTypeAliasLike(target, false));
    }
}
