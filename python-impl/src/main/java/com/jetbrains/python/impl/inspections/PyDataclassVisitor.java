// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.PyDataclassFieldParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames.Dataclasses;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.types.PyCollectionType;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.python.impl.localize.PyLocalize;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared base for the `PyDataclassInspection` visitors. It holds only the framework-agnostic *helpers* reused by the
 * per-framework visitors — it intentionally defines no `visitXxx` checks, so a framework visitor inherits nothing that
 * would double-run when the dispatcher fans a callback out to both the common pass and a framework pass.
 * <p>
 * The actual checks live in dedicated visitors: {@link PyCommonDataclassVisitor} for the framework-agnostic ones and one
 * visitor per framework (e.g. {@code PyStdlibDataclassVisitor}, or an extension-point framework's visitor). `PyDataclassInspection` wires
 * them together: for every callback it first runs the common pass and then, depending on the framework, the matching
 * framework visitor.
 */
public class PyDataclassVisitor extends PyInspectionVisitor {

    public PyDataclassVisitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
        super(holder, context);
    }

    private record UselessArgument(@Nullable PyExpression argument, String argumentName, String methodName) {
    }

    /**
     * Reports the parameter/method conflicts shared by stdlib and `dataclass_transform` dataclasses (`eq`/`order`
     * consistency, `init`/`repr`/`eq`/`order`/`frozen`/`unsafe_hash` arguments made useless by hand-written methods, and
     * frozen-inheritance consistency across the hierarchy). Invoked by the stdlib and transform visitors.
     */
    protected void processDataclassParameters(PyClass cls, PyDataclassParameters dataclassParameters) {
        if (!dataclassParameters.getEq() && dataclassParameters.getOrder()) {
            registerProblem(dataclassParameters.getEqArgument(), PyLocalize.inspDataclassesEqMustBeTrueIfOrderTrue().get(),
                            ProblemHighlightType.GENERIC_ERROR);
        }

        boolean initMethodExists = false;
        boolean reprMethodExists = false;
        boolean eqMethodExists = false;
        boolean orderMethodsExist = false;
        boolean mutatingMethodsExist = false;
        boolean hashMethodExists = false;

        for (PyFunction it : cls.getMethods()) {
            String name = it.getName();
            if (PyNames.INIT.equals(name)) {
                initMethodExists = true;
            }
            else if ("__repr__".equals(name)) {
                reprMethodExists = true;
            }
            else if ("__eq__".equals(name)) {
                eqMethodExists = true;
            }
            else if (name != null && PyDataclassVisitorKt.ORDER_OPERATORS.contains(name)) {
                orderMethodsExist = true;
            }
            else if ("__setattr__".equals(name) || "__delattr__".equals(name)) {
                mutatingMethodsExist = true;
            }
            else if (PyNames.HASH.equals(name)) {
                hashMethodExists = true;
            }
        }

        hashMethodExists = hashMethodExists || cls.findClassAttribute(PyNames.HASH, false, myTypeEvalContext) != null;

        // argument to register problem, argument name and method name
        List<UselessArgument> useless = new ArrayList<>();

        if (dataclassParameters.getInit() && initMethodExists) {
            useless.add(new UselessArgument(dataclassParameters.getInitArgument(), "init", PyNames.INIT));
        }

        if (dataclassParameters.getRepr() && reprMethodExists) {
            useless.add(new UselessArgument(dataclassParameters.getReprArgument(), "repr", "__repr__"));
        }

        if (dataclassParameters.getEq() && eqMethodExists) {
            useless.add(new UselessArgument(dataclassParameters.getEqArgument(), "eq", "__eq__"));
        }

        for (UselessArgument it : useless) {
            registerProblem(it.argument(),
                            PyInspectionMessages.problemMessage(a -> PyLocalize.inspDataclassesArgumentIgnoredIfClassAlreadyDefinesMethod(a[0], a[1]),
                                                                it.argumentName(), it.methodName()),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }

        if (dataclassParameters.getOrder() && orderMethodsExist) {
            registerProblem(dataclassParameters.getOrderArgument(),
                            PyLocalize.inspDataclassesOrderArgumentShouldBeFalseIfClassDefinesOneOfOrderMethods().get(),
                            ProblemHighlightType.GENERIC_ERROR);
        }

        if (Boolean.TRUE.equals(dataclassParameters.getFrozen()) && mutatingMethodsExist) {
            registerProblem(dataclassParameters.getFrozenArgument(),
                            PyLocalize.inspDataclassesFrozenAttributeShouldBeFalseIfClassDefinesSetattrOrDelattr().get(),
                            ProblemHighlightType.GENERIC_ERROR);
        }

        if (dataclassParameters.getUnsafeHash() && hashMethodExists) {
            registerProblem(dataclassParameters.getUnsafeHashArgument(),
                            PyLocalize.inspDataclassesUnsafeHashAttributeShouldBeFalseIfClassDefinesHash().get(),
                            ProblemHighlightType.GENERIC_ERROR);
        }

        processFrozenInheritance(cls, dataclassParameters);
    }

    /**
     * Reports a class whose explicit `frozen` state differs from one declared elsewhere in its dataclass hierarchy.
     */
    protected void processFrozenInheritance(PyClass cls, PyDataclassParameters dataclassParameters) {
        Boolean frozenInHierarchy = null;
        for (PyClass current : StreamEx.of(cls).append(cls.getAncestorClasses(myTypeEvalContext))) {
            PyDataclassParameters currentParameters = PyDataclassesKt.parseStdOrDataclassTransformDataclassParameters(current, myTypeEvalContext);
            Boolean currentFrozen = currentParameters != null ? currentParameters.getFrozen() : null;
            if (currentFrozen == null) {
                continue;
            }

            if (frozenInHierarchy == null) {
                frozenInHierarchy = currentFrozen;
            }
            else if (!frozenInHierarchy.equals(currentFrozen)) {
                PyExpression frozenArgument = dataclassParameters.getFrozenArgument();
                registerProblem(frozenArgument != null ? frozenArgument : cls.getNameIdentifier(),
                                PyLocalize.inspDataclassesFrozenDataclassesCanNotInheritNonFrozenOne().get(),
                                ProblemHighlightType.GENERIC_ERROR);
            }
        }
    }

    /**
     * Reports conflicting `field(...)` arguments (a `default_factory` on a `ClassVar`/`InitVar`, or both `default` and
     * `default_factory`). Shared by the stdlib and transform visitors.
     */
    protected void processFieldFunctionCall(PyClass dataclass, PyDataclassParameters dataclassParameters, PyTargetExpression field) {
        PyDataclassFieldParameters fieldStub =
            PyDataclassesKt.resolveDataclassFieldParameters(dataclass, dataclassParameters, field, myTypeEvalContext);
        if (fieldStub == null) {
            return;
        }
        if (!(field.findAssignedValue() instanceof PyCallExpression call)) {
            return;
        }

        if (PyTypingTypeProvider.isClassVar(field, myTypeEvalContext) || getInitVarType(field) != null) {
            if (fieldStub.getHasDefaultFactory()) {
                registerProblem(call.getKeywordArgument("default_factory"),
                                PyLocalize.inspDataclassesFieldCannotHaveDefaultFactory().get(),
                                ProblemHighlightType.GENERIC_ERROR);
            }
        }
        else if (fieldStub.getHasDefault() && fieldStub.getHasDefaultFactory()) {
            registerProblem(call.getArgumentList(), PyLocalize.inspDataclassesCannotSpecifyBothDefaultAndDefaultFactory().get(),
                            ProblemHighlightType.GENERIC_ERROR);
        }
    }

    /**
     * The `dataclasses.InitVar[...]` element type of {@code field}, or {@code null} when it is not an InitVar.
     */
    protected @Nullable PyType getInitVarType(PyTargetExpression field) {
        PyType fieldType = myTypeEvalContext.getType(field);
        if (fieldType instanceof PyCollectionType collectionType && Dataclasses.DATACLASSES_INITVAR.equals(collectionType.getClassQName())) {
            List<PyType> elementTypes = collectionType.getElementTypes();
            return elementTypes.size() == 1 ? elementTypes.get(0) : null;
        }
        return null;
    }

    /**
     * The {@link PyClass} of the instance {@code element} refers to, or {@code null} when {@code element} is a type/definition or not a class.
     */
    public @Nullable PyClass getInstancePyClass(@Nullable PyTypedElement element) {
        PyType type = element != null ? myTypeEvalContext.getType(element) : null;
        return type instanceof PyClassType classType && !classType.isDefinition() ? classType.getPyClass() : null;
    }
}
