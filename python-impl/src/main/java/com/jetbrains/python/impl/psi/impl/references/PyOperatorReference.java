// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.references;

import java.util.stream.Collectors;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyQualifiedElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

// TODO consulo: ContainerUtil.packNullables missing
import static consulo.util.collection.ContainerUtil.createMaybeSingletonList;
import static com.jetbrains.python.ast.PyAstBinaryExpression.getChainedComparisonAwareLeftExpression;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAnyOrUnknown;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

public class PyOperatorReference extends PyReferenceBase {
    public PyOperatorReference(PyQualifiedElement element, PyResolveContext context) {
        super(element, context);
    }

    @Override
    protected List<RatedResolveResult> resolveInner() {
        // TODO consulo: ContainerUtil.flatMap missing
        return resolveOperator().stream().flatMap(it -> it.resolveResults().stream()).collect(Collectors.toList());
    }

    public record OperatorResolveResult(PyType selfType,
                                        List<PyExpression> arguments,
                                        OperatorSide side,
                                        List<? extends RatedResolveResult> resolveResults) { }

    public enum OperatorSide {
        INPLACE,
        LEFT,
        RIGHT,
    }

    public List<OperatorResolveResult> resolveOperator() {
        if (myElement instanceof PyAugAssignmentStatement stmt) {
            return resolveInlineAndLeftAndRightOperators(stmt, stmt.getReferencedName());
        }
        else if (myElement instanceof PyBinaryExpression expr) {
            String name = expr.getReferencedName();
            if (PyNames.STANDALONE_RIGHT_OPERATORS.contains(name)) {
                var leftOperand = (PyExpression)getChainedComparisonAwareLeftExpression(expr);
                return resolveMember(expr.getRightExpression(), createMaybeSingletonList(leftOperand), OperatorSide.RIGHT, name);
            }
            else {
                return resolveLeftAndRightOperators(expr, name);
            }
        }
        else if (myElement instanceof PySubscriptionExpression expr) {
            return resolveMember(expr.getOperand(), expr.getArguments(), OperatorSide.LEFT, expr.getReferencedName());
        }
        else if (myElement instanceof PyPrefixExpression expr) {
            return resolveMember(expr.getOperand(), List.of(), OperatorSide.LEFT, expr.getReferencedName());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
        if (element instanceof PyParameter || element instanceof PyTargetExpression) {
            return false;
        }
        return super.isReferenceTo(element);
    }

    @Override
    public String toString() {
        return "PyOperatorReference(" + myElement + "," + myContext + ")";
    }

    public String getReadableOperatorName() {
        String name = myElement.getReferencedName();
        if (name != null && PyNames.SUBSCRIPTION_OPERATORS.contains(name)) {
            return "[]";
        }
        else {
            return getRangeInElement().substring(myElement.getText());
        }
    }

    public @Nullable PyExpression getReceiver(@Nullable PyCallable resolvedCallee) {
        return switch (myElement) {
            case PyCallExpression call -> call.getCallee() instanceof PyQualifiedExpression callee ? callee.getQualifier() : null;
            case PyPrefixExpression prefixExpr -> prefixExpr.getOperand();
            case PySubscriptionExpression subscription -> subscription.getOperand();
            case PyBinaryExpression binaryExpr -> binaryExpr.isRightOperator(resolvedCallee)
                                                 ? binaryExpr.getRightExpression()
                                                 : (PyExpression)getChainedComparisonAwareLeftExpression(binaryExpr);
            default -> null;
        };
    }

    private List<OperatorResolveResult> resolveLeftAndRightOperators(PyBinaryExpression expr, @Nullable String name) {
        List<OperatorResolveResult> result = new ArrayList<>();
        PyExpression leftOperand = (PyExpression)getChainedComparisonAwareLeftExpression(expr);

        TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
        typeEvalContext.traceWithIndent("Trying to resolve left operator", () -> {
            result.addAll(resolveMember(leftOperand, createMaybeSingletonList(expr.getRightExpression()), OperatorSide.LEFT, name));
        });

        // A user-defined metaclass override of the operator never lets Python fall back to the
        // reflected operator on the right; skipping it here keeps inherited typeshed signatures
        // out of the inferred type. Instance-receiver paths still need both candidates (e.g. for
        // stub-defined numpy operators).
        if (isMetaclassDispatch(leftOperand) &&
            hasUserDefinedResolution(result.stream().flatMap(it -> it.resolveResults().stream()))) {
            return result;
        }

        typeEvalContext.traceWithIndent("Trying to resolve right operator", () -> {
            result.addAll(resolveMember(expr.getRightExpression(), createMaybeSingletonList(leftOperand), OperatorSide.RIGHT,
                                         PyNames.leftToRightOperatorName(name)));
        });
        return result;
    }

    private List<OperatorResolveResult> resolveInlineAndLeftAndRightOperators(PyAugAssignmentStatement stmt,
                                                                                       @Nullable String name) {
        List<OperatorResolveResult> result = new ArrayList<>();

        TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
        typeEvalContext.traceWithIndent("Trying to resolve inplace operator", () -> {
            result.addAll(resolveMember(stmt.getTarget(), createMaybeSingletonList(stmt.getValue()), OperatorSide.INPLACE, name));
        });
        typeEvalContext.traceWithIndent("Trying to resolve left operator", () -> {
            result.addAll(resolveMember(stmt.getTarget(), createMaybeSingletonList(stmt.getValue()), OperatorSide.LEFT,
                                         PyNames.inplaceToLeftOperatorName(name)));
        });
        typeEvalContext.traceWithIndent("Trying to resolve right operator", () -> {
            result.addAll(resolveMember(stmt.getValue(), createMaybeSingletonList(stmt.getTarget()), OperatorSide.RIGHT,
                                         PyNames.inplaceToRightOperatorName(name)));
        });
        return result;
    }

    private static boolean hasUserDefinedResolution(Stream<? extends RatedResolveResult> results) {
        return results
            .map(res -> res.getElement())
            .filter(Objects::nonNull)
            .anyMatch(element -> !PyBuiltinCache.getInstance(element).isBuiltin(element));
    }

    private boolean isMetaclassDispatch(@Nullable PyExpression receiver) {
        if (receiver == null) {
            return false;
        }
        PyType type = myContext.getTypeEvalContext().getType(receiver);
        return type instanceof PyClassLikeType classLikeType && classLikeType.isDefinition();
    }

    private List<OperatorResolveResult> resolveMember(@Nullable PyExpression object,
                                                               List<PyExpression> arguments,
                                                               OperatorSide side,
                                                               @Nullable String name) {
        if (object != null && name != null) {
            TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
            PyType type = typeEvalContext.getType(object);
            typeEvalContext.trace("Side text is %s, type is %s", object.getText(), type);
            if (!isAnyOrUnknown(type)) {
                List<OperatorResolveResult> res = new ArrayList<>();
                for (PyType it : PyTypeUtil.toStream(type)) {
                    if (isUnknown(it)) {
                        continue;
                    }
                    var resolved = it instanceof PyClassLikeType && ((PyClassLikeType)it).isDefinition()
                                   ? resolveDefinitionMember((PyClassLikeType)it, object, name)
                                   : it.resolveMember(name, object, AccessDirection.of(myElement), myContext);

                    if (!ContainerUtil.isEmpty(resolved)) {
                        res.add(new OperatorResolveResult(it, arguments, side, resolved));
                    }
                }

                if (!res.isEmpty()) {
                    return res;
                }
                else if (typeEvalContext.tracing()) {
                    VirtualFile vFile = null;
                    if (type instanceof PyClassType) {
                        PyClass pyClass = ((PyClassType)type).getPyClass();
                        vFile = pyClass.getContainingFile().getVirtualFile();
                    }
                    type.resolveMember(name, object, AccessDirection.of(myElement), myContext);
                    typeEvalContext.trace("Could not resolve member %s in type %s from file %s", name, type, vFile);
                }
            }
        }
        return Collections.emptyList();
    }

    private @Nullable List<? extends RatedResolveResult> resolveDefinitionMember(PyClassLikeType classLikeType,
                                                                                 PyExpression object,
                                                                                 String name) {
        PyClassLikeType metaClassType = classLikeType.getMetaClassType(myContext.getTypeEvalContext(), true);
        if (metaClassType != null) {
            List<? extends RatedResolveResult> results =
                metaClassType.resolveMember(name, object, AccessDirection.of(myElement), myContext);

            if (!ContainerUtil.isEmpty(results)) {
                return results;
            }
        }

        return name.equals(PyNames.GETITEM) && LanguageLevel.forElement(object).isAtLeast(LanguageLevel.PYTHON37)
               ? classLikeType.resolveMember(PyNames.CLASS_GETITEM, object, AccessDirection.of(myElement), myContext)
               : null;
    }
}
