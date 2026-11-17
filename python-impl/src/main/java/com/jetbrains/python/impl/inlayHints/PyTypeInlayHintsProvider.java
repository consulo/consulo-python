// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inlayHints;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.types.PyInferredVarianceJudgment;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeInferenceCspFactory;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.editor.inlay.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

@ExtensionImpl
public class PyTypeInlayHintsProvider implements DeclarativeInlayHintsProvider {
    public static final String REVEAL_TYPE_OPTION_ID = "python.type.inlays.reveal_type";
    public static final String FUNCTION_RETURN_TYPE_OPTION_ID = "python.type.inlays.function.return";
    public static final String VARIANCE_OPTION_ID = "python.type.inlays.variance";
    public static final String PARAMETER_TYPE_ANNOTATION = "python.type.inlays.parameter.annotation";
    public static final String SOLVED_CLASS_TYPE_PARAMETERS_OPTION_ID = "python.type.inlays.solved.type.parameters.class";
    public static final String SOLVED_FUNCTION_TYPE_PARAMETERS_OPTION_ID = "python.type.inlays.solved.type.parameters.function";

    // TODO consulo: PresentationTreeBuilderImpl.MAX_SEGMENT_TEXT_LENGTH is internal (ide-impl) in Consulo
    private static final int MAX_SEGMENT_TEXT_LENGTH = 30;

    @Override
    public DeclarativeInlayHintsCollector createCollector(PsiFile file, Editor editor) {
        return new Collector();
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    @Override
    public String getId() {
        return "python.type.inlays";
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.inlayTypeHints();
    }

    @Override
    public LocalizeValue getDescription() {
        return LocalizeValue.empty();
    }

    @Override
    public LocalizeValue getPreviewFileText() {
        // resources/inlayProviders/python.type.inlays/preview.py
        return LocalizeValue.of("""
            from typing import reveal_type

            def example(x: int, y: float)/*<# -> float #>*/:
                reveal_type(x + y)/*<# float #>*/
                return x + y

            reveal_type(example(1, 2.5))/*<# float #>*/

            class A[/*<# in #>*/T]:
                def f(self, t:T):
                    pass


            def f(a/*<#: int#>*/=1):
                pass


            # pytest fixtures
            def test_example(tmp_path/*<#: Path#>*/):
                pass""");
    }

    @Override
    public InlayGroup getGroup() {
        return InlayGroup.TYPES_GROUP;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public Set<DeclarativeInlayOptionInfo> getOptions() {
        return new LinkedHashSet<>(List.of(
            new DeclarativeInlayOptionInfo(REVEAL_TYPE_OPTION_ID, true, PyLocalize.inlayRevealType(), PyLocalize.inlayRevealType()),
            new DeclarativeInlayOptionInfo(FUNCTION_RETURN_TYPE_OPTION_ID, false, PyLocalize.inlayFunctionReturn(), PyLocalize.inlayFunctionReturn()),
            new DeclarativeInlayOptionInfo(VARIANCE_OPTION_ID, true, PyLocalize.inlayVariance(), PyLocalize.inlayVarianceDescription()),
            new DeclarativeInlayOptionInfo(PARAMETER_TYPE_ANNOTATION, true, PyLocalize.inlayParameterTypeAnnotations(),
                PyLocalize.inlayParameterTypeAnnotations()),
            new DeclarativeInlayOptionInfo(SOLVED_CLASS_TYPE_PARAMETERS_OPTION_ID, true, PyLocalize.inlaySolvedTypeParametersClass(),
                PyLocalize.inlaySolvedTypeParametersClassDescription()),
            new DeclarativeInlayOptionInfo(SOLVED_FUNCTION_TYPE_PARAMETERS_OPTION_ID, false, PyLocalize.inlaySolvedTypeParametersFunction(),
                PyLocalize.inlaySolvedTypeParametersFunctionDescription())
        ));
    }

    @FunctionalInterface
    private interface TypeArgumentsSolver {
        @Nullable List<@Nullable PyType> solve(PyCallExpression call, PyReferenceExpression callee, PyResolveContext resolveContext);
    }

    private static class Collector implements DeclarativeInlayHintsCollector.SharedBypassCollector {
        final HintFormat returnTypeHintFormat = HintFormat.DEFAULT.withFontSize(HintFontSize.ABitSmallerThanInEditor);
        final HintFormat revealTypeHintFormat = returnTypeHintFormat.withHorizontalMargin(HintMarginPadding.MarginAndSmallerPadding);
        final HintFormat varianceHintFormat = returnTypeHintFormat.withHorizontalMargin(HintMarginPadding.MarginAndSmallerPadding);
        final HintFormat typeArgumentHintFormat = HintFormat.DEFAULT;

        @Override
        public void collectFromElement(PsiElement element, DeclarativeInlayTreeSink sink) {
            TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile());
            PyResolveContext resolveContext = PyResolveContext.defaultContext(typeEvalContext);

            sink.whenOptionEnabled(REVEAL_TYPE_OPTION_ID, () -> getInlaysForRevealType(element, sink, resolveContext));

            sink.whenOptionEnabled(FUNCTION_RETURN_TYPE_OPTION_ID, () -> getInlaysForReturnType(element, sink, resolveContext));

            sink.whenOptionEnabled(VARIANCE_OPTION_ID, () -> {
                getInlaysForTypeVariableVariance(element, sink, resolveContext);
                getInlaysForTypeParameterVariance(element, sink, resolveContext);
            });

            sink.whenOptionEnabled(PARAMETER_TYPE_ANNOTATION, () -> getInlaysForParameterAnnotations(element, sink, resolveContext));

            sink.whenOptionEnabled(SOLVED_CLASS_TYPE_PARAMETERS_OPTION_ID,
                () -> getInlaysForSolvedTypeParameters(element, sink, resolveContext, Collector::solveClassTypeArguments));

            sink.whenOptionEnabled(SOLVED_FUNCTION_TYPE_PARAMETERS_OPTION_ID,
                () -> getInlaysForSolvedTypeParameters(element, sink, resolveContext, Collector::solveFunctionTypeArguments));
        }

        private void getInlaysForRevealType(PsiElement element, DeclarativeInlayTreeSink sink, PyResolveContext resolveContext) {
            if (!(element instanceof PyCallExpression callExpression)) {
                return;
            }
            List<PyCallable> callables = callExpression.multiResolveCalleeFunction(resolveContext);
            PyCallable callable = callables.size() == 1 ? callables.get(0) : null;
            TypeEvalContext typeEvalContext = resolveContext.getTypeEvalContext();

            if (callable instanceof PyFunction function &&
                Arrays.asList(PyTypingTypeProvider.REVEAL_TYPE, PyTypingTypeProvider.REVEAL_TYPE_EXT).contains(function.getQualifiedName())) {
                PyExpression[] args = callExpression.getArguments();

                if (args.length != 1) {
                    return;
                }
                PyType type = typeEvalContext.getType(args[0]);

                Document document = element.getContainingFile().getViewProvider().getDocument();
                if (document == null) {
                    return;
                }
                int lineNumber = document.getLineNumber(element.getTextRange().getEndOffset());
                sink.addPresentation(new DeclarativeInlayPosition.EndOfLinePosition(lineNumber), revealTypeHintFormat, builder -> {
                    // use geTypeName here, because reveal_type should show the same as "Type Info" action
                    builder.text(PythonDocumentationProvider.getTypeName(type, typeEvalContext));
                });
            }
        }

        private void getInlaysForReturnType(PsiElement element, DeclarativeInlayTreeSink sink, PyResolveContext resolveContext) {
            TypeEvalContext typeEvalContext = resolveContext.getTypeEvalContext();
            if (!(element.getParent() instanceof PyFunction function)) {
                return;
            }
            if (element == function.getNameIdentifier() &&
                function.getAnnotationValue() == null &&
                function.getTypeCommentAnnotation() == null) {
                PyType returnType = typeEvalContext.getReturnType(function);
                PyType type;
                if (function.isAsync()) {
                    if (returnType instanceof PyClassType classType && classType.getTypeArguments().size() > 2) {
                        type = classType.getTypeArguments().get(2);
                    }
                    else {
                        type = null;
                    }
                }
                else {
                    type = returnType;
                }

                sink.addPresentation(
                    new DeclarativeInlayPosition.InlineInlayPosition(function.getParameterList().getTextRange().getEndOffset(), true),
                    returnTypeHintFormat,
                    builder -> {
                        builder.text("-> ");
                        PyTypeHintDeclarativeRendererKt.printPyTypeHint(builder, type, typeEvalContext);
                    }
                );
            }
        }

        private void getInlaysForTypeVariableVariance(PsiElement element, DeclarativeInlayTreeSink sink, PyResolveContext resolveContext) {
            if (!(element instanceof PyReferenceExpression refExpr)) {
                return;
            }
            PyTupleExpression tupleExpr = refExpr.getParent() instanceof PyTupleExpression tuple ? tuple : null;
            PySubscriptionExpression subscriptionExpr;
            if (refExpr.getParent() instanceof PySubscriptionExpression subscription) {
                subscriptionExpr = subscription;
            }
            else if (tupleExpr != null && tupleExpr.getParent() instanceof PySubscriptionExpression subscription) {
                subscriptionExpr = subscription;
            }
            else {
                return;
            }
            if (subscriptionExpr.getIndexExpression() != refExpr && subscriptionExpr.getIndexExpression() != tupleExpr) {
                return;
            }
            if (!(subscriptionExpr.getQualifier() instanceof PyReferenceExpression qualifier)) {
                return;
            }
            if (!(resolveContext.getTypeEvalContext().getType(qualifier) instanceof PyClassLikeType qualifierType)) {
                return;
            }
            if (!PyTypingTypeProvider.GENERIC.equals(qualifierType.getClassQName())) {
                return;
            }
            Ref<PyType> typeRef = PyTypingTypeProvider.getType(refExpr, resolveContext.getTypeEvalContext());
            if (typeRef == null || !(typeRef.get() instanceof PyTypeParameterType typeVarType)) {
                return;
            }
            if (typeVarType.getVariance() == PyVariance.INVARIANT) {
                return;
            }

            PyVariance inferredVariance =
                PyInferredVarianceJudgment.getDeclaredOrInferredVariance(typeVarType, resolveContext.getTypeEvalContext());
            addPresentation(sink, inferredVariance, element);
        }

        private void getInlaysForTypeParameterVariance(PsiElement element, DeclarativeInlayTreeSink sink, PyResolveContext resolveContext) {
            if (!(element instanceof PyTypeParameter typeParameter)) {
                return;
            }

            PyVariance inferredVariance =
                PyInferredVarianceJudgment.getDeclaredOrInferredVariance(typeParameter, resolveContext.getTypeEvalContext());
            addPresentation(sink, inferredVariance, element);
        }

        void addPresentation(DeclarativeInlayTreeSink sink, @Nullable PyVariance inferredVariance, PsiElement element) {
            DeclarativeInlayPosition position = new DeclarativeInlayPosition.InlineInlayPosition(element.getTextRange().getStartOffset(), false);
            if (inferredVariance == PyVariance.COVARIANT) {
                sink.addPresentation(position, null,
                    "covariant: <code>" + element.getText() + "</code> only appears in output positions",
                    varianceHintFormat, builder -> builder.text("out"));
            }
            if (inferredVariance == PyVariance.CONTRAVARIANT) {
                sink.addPresentation(position, null,
                    "contravariant: <code>" + element.getText() + "</code> only appears in input positions",
                    varianceHintFormat, builder -> builder.text("in"));
            }
        }

        private void getInlaysForParameterAnnotations(PsiElement element, DeclarativeInlayTreeSink sink, PyResolveContext resolveContext) {
            if (!(element instanceof PyNamedParameter parameter)) {
                return;
            }

            if (parameter.isSelf()) {
                return;
            }

            if (parameter.getAnnotationValue() != null || parameter.getTypeCommentAnnotation() != null) {
                return;
            }

            TypeEvalContext typeEvalContext = resolveContext.getTypeEvalContext();

            PyType rawParameterType = typeEvalContext.getType(parameter);

            // Unwrap the type of *args and **kwargs parameters to show the element type
            PyType parameterType;
            if (parameter.isPositionalContainer()) {
                parameterType = rawParameterType instanceof PyClassType classType ? classType.getIteratedItemType() : null;
            }
            else if (parameter.isKeywordContainer()) {
                parameterType = rawParameterType instanceof PyClassType classType && classType.getTypeArguments().size() > 1
                    ? classType.getTypeArguments().get(1)
                    : null;
            }
            else {
                parameterType = rawParameterType;
            }
            if (parameterType == null || isUnknown(parameterType)) {
                return;
            }

            PsiElement nameIdentifier = parameter.getNameIdentifier();
            int offset = nameIdentifier != null ? nameIdentifier.getTextRange().getEndOffset() : parameter.getTextRange().getEndOffset();

            sink.addPresentation(
                new DeclarativeInlayPosition.InlineInlayPosition(offset, true),
                HintFormat.DEFAULT,
                builder -> {
                    builder.text(": ");
                    PyTypeHintDeclarativeRendererKt.printPyTypeHint(builder, parameterType, typeEvalContext);
                }
            );
        }

        private void getInlaysForSolvedTypeParameters(PsiElement element,
                                                      DeclarativeInlayTreeSink sink,
                                                      PyResolveContext resolveContext,
                                                      TypeArgumentsSolver solve) {
            if (!(element instanceof PyCallExpression call)) {
                return;
            }
            // Skip explicitly parameterized calls like `A[int](1)`, where the callee is a subscription expression.
            if (!(call.getCallee() instanceof PyReferenceExpression callee)) {
                return;
            }

            List<@Nullable PyType> typeArguments = solve.solve(call, callee, resolveContext);
            if (typeArguments == null) {
                return;
            }
            if (typeArguments.stream().allMatch(it -> isUnknown(it))) {
                return;
            }

            TypeEvalContext typeEvalContext = resolveContext.getTypeEvalContext();
            sink.addPresentation(
                new DeclarativeInlayPosition.InlineInlayPosition(callee.getTextRange().getEndOffset(), true),
                typeArgumentHintFormat,
                builder -> {
                    builder.text("[");
                    for (int index = 0; index < typeArguments.size(); index++) {
                        if (index > 0) {
                            builder.text(", ");
                        }
                        // Use getTypeName (the same renderer as the "Type Info" tooltip), not getTypeHint: the PEP-484
                        // annotation format cannot express a ParamSpec's keyword-only/named parameters, so a solved
                        // `**P` would degrade from `[*, a: int]` to a misleading `[Any, int]`.
                        appendTypeHint(builder, PythonDocumentationProvider.getTypeName(typeArguments.get(index), typeEvalContext));
                    }
                    builder.text("]");
                }
            );
        }

        private static @Nullable List<@Nullable PyType> solveClassTypeArguments(PyCallExpression call,
                                                                                PyReferenceExpression callee,
                                                                                PyResolveContext resolveContext) {
            TypeEvalContext typeEvalContext = resolveContext.getTypeEvalContext();
            PyType calleeType = typeEvalContext.getType(callee);
            if (!(calleeType instanceof PyClassType calleeClassType) || !calleeClassType.isDefinition()) {
                return null;
            }
            PyType callType = typeEvalContext.getType(call);
            if (!(callType instanceof PyClassType callClassType) || !callClassType.isParameterized()) {
                return null;
            }
            return callClassType.getTypeArguments();
        }

        // Call of a generic function declaring its own type parameters, e.g. `f(1)` for `def f[T](t: T) -> T`.
        private static @Nullable List<@Nullable PyType> solveFunctionTypeArguments(PyCallExpression call,
                                                                                  PyReferenceExpression callee,
                                                                                  PyResolveContext resolveContext) {
            TypeEvalContext typeEvalContext = resolveContext.getTypeEvalContext();
            // Generic class instantiations are handled by solveClassTypeArguments.
            PyType calleeType = typeEvalContext.getType(callee);
            if (calleeType instanceof PyClassType calleeClassType && calleeClassType.isDefinition()) {
                return null;
            }

            List<PyCallExpression.PyArgumentsMapping> mappings = PyCallExpressionHelper.mapArguments(call, resolveContext);
            if (mappings.size() != 1) {
                return null;
            }
            PyCallExpression.PyArgumentsMapping mapping = mappings.get(0);
            PyCallableType callableType = mapping.getCallableType();
            if (callableType == null) {
                return null;
            }
            List<PyTypeParameterType> typeParameters = callableType.getTypeParameters(typeEvalContext);
            if (typeParameters == null || typeParameters.isEmpty()) {
                return null;
            }

            Map<PyCallableArgument, PyCallableParameter> mappedParameters = new LinkedHashMap<>();
            for (Map.Entry<PyExpression, PyCallableParameter> entry : mapping.getMappedParameters().entrySet()) {
                mappedParameters.put(new PyCallableArgument(entry.getKey()), entry.getValue());
            }
            PyTypeChecker.GenericSubstitutions substitutions =
                PyTypeInferenceCspFactory.unifyGenericCall(call, callableType, mappedParameters, typeEvalContext);
            if (substitutions == null) {
                return null;
            }
            List<@Nullable PyType> result = new ArrayList<>(typeParameters.size());
            for (PyTypeParameterType it : typeParameters) {
                result.add(PyTypeChecker.substitute(it, substitutions, typeEvalContext));
            }
            return result;
        }

        private static void appendTypeHint(DeclarativePresentationTreeBuilder builder, String typeHint) {
            // A single text node longer than MAX_SEGMENT_TEXT_LENGTH is truncated by the platform with an ellipsis.
            // Split into at most two segments so a moderately long type stays readable without producing an
            // excessively long inlay.
            if (typeHint.length() > MAX_SEGMENT_TEXT_LENGTH) {
                builder.text(typeHint.substring(0, MAX_SEGMENT_TEXT_LENGTH));
                builder.text(typeHint.substring(MAX_SEGMENT_TEXT_LENGTH));
            }
            else {
                builder.text(typeHint);
            }
        }
    }
}
