// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation;

import com.jetbrains.python.PyLanguageFacadeKt;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.PyAstSingleStarParameter;
import com.jetbrains.python.ast.PyAstSlashParameter;
import com.jetbrains.python.ast.PyAstTypeParameter;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.types.PyConcatenateType;
import com.jetbrains.python.impl.psi.types.PyInferredVarianceJudgment;
import com.jetbrains.python.impl.psi.types.PyIntersectionType;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.impl.psi.types.PyNamedTupleType;
import com.jetbrains.python.impl.psi.types.PyNarrowedType;
import com.jetbrains.python.impl.psi.types.PyOverloadType;
import com.jetbrains.python.impl.psi.types.PyParamSpecType;
import com.jetbrains.python.impl.psi.types.PySelfType;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.impl.psi.types.PyTypeFormType;
import com.jetbrains.python.impl.psi.types.PyTypeVisitorExt;
import com.jetbrains.python.impl.psi.types.PyTypedDictType;
import com.jetbrains.python.impl.psi.types.PyTypingNewType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.impl.psi.types.PyUnsafeUnionType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterListOwner;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyTopType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeRendererFeature;
import com.jetbrains.python.psi.types.PyTypeVarTupleType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;
import com.jetbrains.python.psi.types.PyVariance;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.util.HtmlBuilder;
import consulo.application.util.HtmlChunk;
import consulo.colorScheme.TextAttributesKey;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import static com.jetbrains.python.impl.documentation.PyDocSignaturesHighlighterKt.highlightExpressionText;
import static com.jetbrains.python.impl.documentation.PyDocSignaturesHighlighterKt.styledSpan;
import static com.jetbrains.python.impl.psi.types.PyInferredVarianceJudgment.isEffectivelyInvariant;
import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAnyOrUnknown;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

// TODO visitPyConcatenateType
public abstract class PyTypeRenderer extends PyTypeVisitorExt<HtmlChunk> {
  private static final int MAX_DEPTH = 6;

  /**
   * Java counterpart of {@code kotlin.jvm.functions.Function4} used by JetBrains for type renderer callbacks.
   */
  @FunctionalInterface
  interface Function4<P1, P2, P3, P4, R> {
    R invoke(P1 p1, P2 p2, P3 p3, P4 p4);
  }

  protected int myDepth = 0;
  protected final TypeEvalContext myTypeEvalContext;
  protected final EnumSet<PyTypeRendererFeature> myRenderingFeatures;

  protected final boolean isRenderingFqn() {
    return myRenderingFeatures.contains(PyTypeRendererFeature.USE_FQN);
  }

  protected final boolean isRenderingTypeVarBounds() {
    return myRenderingFeatures.contains(PyTypeRendererFeature.TYPE_VAR_BOUNDS);
  }

  protected final boolean isRenderingBareLiteral() {
    return myRenderingFeatures.contains(PyTypeRendererFeature.BARE_LITERAL);
  }


  private PyTypeRenderer(TypeEvalContext typeEvalContext, EnumSet<PyTypeRendererFeature> features) {
    myTypeEvalContext = typeEvalContext;
    myRenderingFeatures = features;
  }

  static abstract class HtmlRenderer extends PyTypeRenderer {
    private final PsiElement myAnchor;

    private HtmlRenderer(TypeEvalContext typeEvalContext, PsiElement anchor, EnumSet<PyTypeRendererFeature> features) {
      super(typeEvalContext, features);
      myAnchor = anchor;
    }

    protected final PsiElement getAnchor() {
      return myAnchor;
    }

    @Override
    protected HtmlChunk styled(@Nullable String text, TextAttributesKey style) {
      return styledSpan(text, style);
    }

    @Override
    protected HtmlChunk styled(HtmlChunk chunk, TextAttributesKey style) {
      return styledSpan(chunk, style);
    }

    @Override
    protected HtmlChunk escaped(@Nullable String text) {
      return HtmlChunk.text(text);
    }

    @Override
    protected HtmlChunk className(@Nullable String name) {
      return PyDocumentationLink.toPossibleClass(name, myAnchor, myTypeEvalContext);
    }

    @Override
    protected HtmlChunk styledExpression(PyExpression expression) {
      return highlightExpressionText(expression.getText(), expression);
    }
  }

  static final class RichDocumentation extends HtmlRenderer {
    RichDocumentation(TypeEvalContext typeEvalContext, PsiElement anchor) {
      super(typeEvalContext, anchor, EnumSet.noneOf(PyTypeRendererFeature.class));
    }
  }

  /**
   * Renders types as HTML the same way as {@link RichDocumentation}, but emits class links in the
   * {@code #element/<fqn>} format navigable from editor tooltips (see
   * {@code com.intellij.codeInsight.hint.ElementLinkHandler}) instead of the {@code psi_element://}
   * protocol used by Quick Documentation. Intended for rich inspection tooltips.
   */
  static final class TooltipDocumentation extends HtmlRenderer {
    TooltipDocumentation(TypeEvalContext typeEvalContext, PsiElement anchor, EnumSet<PyTypeRendererFeature> features) {
      super(typeEvalContext, anchor, features);
    }

    @Override
    protected HtmlChunk className(@Nullable String name) {
      return PyDocumentationLink.toPossibleClassTooltipLink(name, getAnchor(), myTypeEvalContext);
    }

    @Override
    protected HtmlChunk classLink(PyClassLikeType type, @Nullable String name) {
      return PyDocumentationLink.toPossibleClassTooltipLink(type, name, getAnchor(), myTypeEvalContext);
    }

    @Override
    protected HtmlChunk qualifiedNameLink(String displayText, String qualifiedName) {
      return PyDocumentationLink.toQualifiedNameTooltipLink(qualifiedName, displayText);
    }
  }

  /**
   * Renders types as HTML with the same highlighting as {@link TooltipDocumentation}, but without links.
   */
  static final class HighlightedDocumentation extends HtmlRenderer {
    HighlightedDocumentation(TypeEvalContext typeEvalContext, PsiElement anchor) {
      super(typeEvalContext, anchor, EnumSet.noneOf(PyTypeRendererFeature.class));
    }

    @Override
    protected HtmlChunk className(@Nullable String name) {
      return PyDocumentationLink.toPossibleClassText(name, getAnchor(), myTypeEvalContext);
    }

    @Override
    protected HtmlChunk classLink(PyClassLikeType type, @Nullable String name) {
      return PyDocumentationLink.toPossibleClassText(type, name, getAnchor(), myTypeEvalContext);
    }
  }

  static final class Documentation extends PyTypeRenderer {
    Documentation(TypeEvalContext typeEvalContext, EnumSet<PyTypeRendererFeature> features) {
      super(typeEvalContext, features);
    }
  }

  public static final class TypeHint extends PyTypeRenderer {
    private static final EnumSet<PyTypeRendererFeature> SUPPORTED_FEATURES = EnumSet.of(PyTypeRendererFeature.USE_FQN);

    public TypeHint(TypeEvalContext typeEvalContext, EnumSet<PyTypeRendererFeature> features) {
      super(typeEvalContext, validateFeatures(features));
    }

    private static EnumSet<PyTypeRendererFeature> validateFeatures(EnumSet<PyTypeRendererFeature> features) {
      EnumSet<PyTypeRendererFeature> unsupported = EnumSet.copyOf(features);
      unsupported.removeAll(SUPPORTED_FEATURES);
      if (!unsupported.isEmpty()) {
        throw new IllegalArgumentException("Unsupported features for type hint rendering " + unsupported);
      }
      return features;
    }

    @Override
    protected boolean maxDepthExceeded() {
      return false;
    }

    @Override
    public HtmlChunk visitPyCallableType(PyCallableType callableType) {
      List<PyCallableParameter> parameters = getParameters(callableType);
      HtmlChunk parameterList = parameters != null
                                ? inSquareBrackets(ContainerUtil.map(parameters, this::visitPyCallableParameter))
                                : styled("...", PyHighlighter.PY_DOT);
      return PyTypeLayout.concat(HtmlChunk.raw(isRenderingFqn() ? "typing.Callable" : "Callable"), //NON-NLS
                                 inSquareBrackets(List.of(parameterList, render(callableType.getReturnType(myTypeEvalContext)))));
    }

    // Returns `callableType`'s parameter list (excluding terminating '/') if it can be expressed using typing.Callable, `null` otherwise.
    // Parameters specified using typing.Callable are assumed to be positional-only (a parameter list must be terminated with '/'). There
    // is no way to specify keyword-only or variadic parameters.
    private @Nullable List<PyCallableParameter> getParameters(PyCallableType callableType) {
      List<PyCallableParameter> parameters = callableType.getParameters(myTypeEvalContext);
      if (parameters == null) return null;
      if (parameters.isEmpty()) return List.of();

      for (int i = 0; i < parameters.size(); i++) {
        PyCallableParameter parameter = parameters.get(i);
        if (parameter.isPositionalContainer()) {
          return null;
        }
        if (parameter.isKeywordOnlySeparator()) {
          return null;
        }
        if (parameter.isPositionOnlySeparator()) {
          if (i == parameters.size() - 1) {
            return parameters.subList(0, i);
          }
          return null;
        }
      }

      // TODO If CallableType is inferred from a 'Callable[]' type hint, there is no terminating '/' parameter.
      // Check whether all parameters have no name then.
      if (ContainerUtil.all(parameters, parameter -> parameter.getName() == null)) {
        return parameters;
      }
      return null;
    }

    @Override
    protected HtmlChunk visitPyCallableParameter(PyCallableParameter param) {
      return render(param.getType(myTypeEvalContext));
    }

    @Override
    public HtmlChunk visitPyUnsafeUnionType(PyUnsafeUnionType unsafeUnionType) {
      // There is no way to represent weak unions through type hints
      return visitUnknownType();
    }

    @Override
    public HtmlChunk visitPyIntersectionType(PyIntersectionType intersectionType) {
      // There is no way to represent intersections through the standard type hints at the moment
      return visitUnknownType();
    }

    @Override
    public HtmlChunk visitUnknownType() {
      // `Unknown` is not denotable Python: render it as `Any` in generated annotations
      return visitAnyType();
    }

    @Override
    public HtmlChunk visitPySelfType(PySelfType selfType) {
      HtmlChunk selfTypeRender = className(isRenderingFqn() ? "typing.Self" : "Self"); //NON-NLS
      return selfType.isDefinition() ? wrapInTypingType(selfTypeRender) : selfTypeRender;
    }

    @Override
    public HtmlChunk visitPyOverloadType(PyOverloadType overloadType) {
      return escaped("Callable[..., object]"); //NON-NLS
    }
  }

  protected boolean maxDepthExceeded() {
    return myDepth > MAX_DEPTH;
  }

  protected HtmlChunk render(@Nullable PyType type) {
    if (maxDepthExceeded()) {
      return styled("...", PyHighlighter.PY_DOT);
    }
    myDepth++;
    try {
      return visit(type, this);
    }
    finally {
      myDepth--;
    }
  }

  protected HtmlChunk styled(@Nullable String text, TextAttributesKey style) {
    return HtmlChunk.raw(StringUtil.notNullize(text));
  }

  protected HtmlChunk styled(HtmlChunk chunk, TextAttributesKey style) {
    return chunk;
  }

  protected HtmlChunk escaped(@Nullable String text) {
    return HtmlChunk.raw(StringUtil.notNullize(text));
  }

  protected HtmlChunk className(@Nullable String name) {
    return escaped(name);
  }

  protected HtmlChunk classLink(PyClassLikeType type, @Nullable String name) {
    return className(name);
  }

  /**
   * Renders a name that is shown as [displayText] and, in HTML tooltips, linked to the symbol with the given
   * [qualifiedName]. The default renders plain text without a link. Used for things that carry a known FQN but
   * are not [PyClass]es: {@code typing} special forms ({@code Any}, {@code Literal}) and modules.
   */
  protected HtmlChunk qualifiedNameLink(String displayText, String qualifiedName) {
    return HtmlChunk.raw(displayText);
  }

  protected HtmlChunk styledExpression(PyExpression expression) {
    return HtmlChunk.raw(expression.getText());
  }

  private HtmlChunk commaSeparator() {
    return PyTypeLayout.separator(styled(", ", PyHighlighter.PY_COMMA), styled(",", PyHighlighter.PY_COMMA), HtmlChunk.empty());
  }

  private HtmlChunk bracketed(HtmlChunk open, List<HtmlChunk> items, HtmlChunk close) {
    return PyTypeLayout.bracketed(open, items, commaSeparator(), styled(",", PyHighlighter.PY_COMMA), close);
  }

  protected final HtmlChunk inSquareBrackets(List<HtmlChunk> items) {
    return bracketed(styled("[", PyHighlighter.PY_BRACKETS), items, styled("]", PyHighlighter.PY_BRACKETS));
  }

  private HtmlChunk joinWithOperator(List<HtmlChunk> operands, String operator) {
    return PyTypeLayout.fill(operands, PyTypeLayout.separator(styled(" " + operator + " ", PyHighlighter.PY_OPERATION_SIGN),
                                                              HtmlChunk.empty(),
                                                              styled(operator + " ", PyHighlighter.PY_OPERATION_SIGN)));
  }

  protected final boolean isBitwiseOrUnionAvailable() {
    return PyTypingTypeProvider.isBitwiseOrUnionAvailable(myTypeEvalContext);
  }

  protected final boolean isGenericBuiltinsAvailable() {
    PsiFile origin = myTypeEvalContext.getOrigin();
    return origin == null || PyLanguageFacadeKt.getEffectiveLanguageLevel(origin).isAtLeast(LanguageLevel.PYTHON39);
  }

  @Override
  public HtmlChunk visitPyClassType(PyClassType classType) {
    if (!classType.isParameterized()) {
      return visitPyClassLikeType(classType);
    }
    HtmlChunk genericTypeRender = renderGenericType(classType);
    return classType.isDefinition() ? wrapInTypingType(genericTypeRender) : genericTypeRender;
  }

  @Override
  public HtmlChunk visitPyTypedDictType(PyTypedDictType typedDictType) {
    return renderNamedType(typedDictType);
  }

  @Override
  public HtmlChunk visitPyNamedTupleType(PyNamedTupleType namedTupleType) {
    // A named tuple is rendered by its name and the type arguments of its class, not by its field types.
    return renderNamedType(namedTupleType);
  }

  /**
   * Renders a type that has a name of its own, rather than the name of its class, together with its type arguments.
   */
  private HtmlChunk renderNamedType(PyClassType type) {
    List<PyType> typeArguments = type.getTypeArguments();
    if (typeArguments.isEmpty()) {
      return visitPyClassLikeType(type);
    }

    HtmlChunk parameterizedRender = PyTypeLayout.concat(classLink(type, getTypeName(type)),
                                                        inSquareBrackets(ContainerUtil.map(typeArguments, this::render)));
    return type.isDefinition() ? wrapInTypingType(parameterizedRender) : parameterizedRender;
  }

  @Override
  public HtmlChunk visitPyTypingNewType(PyTypingNewType newType) {
    // A NewType is rendered by its own name, not by the type arguments of the type it wraps (which it exposes
    // through delegation). Before PY-79063 it reached the non-parameterized visitPyClassType branch.
    return visitPyClassLikeType(newType);
  }

  private HtmlChunk renderGenericType(PyClassType genericType) {
    List<HtmlChunk> result = new ArrayList<>();
    boolean renderTypeArgumentList = genericType.isParameterized();
    String className = genericType.getName();
    if (renderTypeArgumentList &&
        !isGenericBuiltinsAvailable() &&
        PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.containsKey(className)) {
      className = PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.get(className);
      if (isRenderingFqn()) {
        className = PyTypingTypeProvider.TYPING + "." + className;
      }
      result.add(className(className));
    }
    else {
      result.add(classLink(genericType, isRenderingFqn() ? genericType.getClassQName() : className));
    }
    if (renderTypeArgumentList) {
      result.add(inSquareBrackets(ContainerUtil.map(genericType.getTypeArguments(), this::render)));
    }
    return PyTypeLayout.concat(result);
  }

  protected HtmlChunk wrapInTypingType(HtmlChunk instanceTypeRender) {
    return PyTypeLayout.concat(
      isGenericBuiltinsAvailable() ? styled(isRenderingFqn() ? PyNames.FQN.TYPE : PyNames.TYPE, PyHighlighter.PY_BUILTIN_NAME) : //NON-NLS
      escaped(isRenderingFqn() ? "typing.Type" : "Type"), //NON-NLS
      inSquareBrackets(List.of(instanceTypeRender))
    );
  }

  @Override
  public HtmlChunk visitPyClassLikeType(PyClassLikeType classLikeType) {
    HtmlChunk classTypeRender = classLink(classLikeType, getTypeName(classLikeType));
    return classLikeType.isDefinition() ? wrapInTypingType(classTypeRender) : classTypeRender;
  }

  @Override
  public HtmlChunk visitPyNarrowedType(PyNarrowedType narrowedType) {
    HtmlChunk name = narrowedType.getTypeIs()
                     ? styled(isRenderingFqn() ? "typing.TypeIs" : "TypeIs", PyHighlighter.PY_CLASS_DEFINITION)
                     : styled(isRenderingFqn() ? "typing.TypeGuard" : "TypeGuard", PyHighlighter.PY_CLASS_DEFINITION);
    return PyTypeLayout.concat(name, inSquareBrackets(List.of(render(narrowedType.getNarrowedType()))));
  }

  @Override
  public HtmlChunk visitPyTypeFormType(PyTypeFormType typeFormType) {
    return PyTypeLayout.concat(styled(isRenderingFqn() ? "typing.TypeForm" : "TypeForm", PyHighlighter.PY_CLASS_DEFINITION),
                               inSquareBrackets(List.of(render(typeFormType.getRepresentedType()))));
  }

  @Override
  public HtmlChunk visitPyNeverType(PyNeverType neverType) {
    return className(neverType.getName());
  }

  @Override
  public HtmlChunk visitPyTopType(PyTopType topType) {
    return className(isRenderingFqn() ? PyNames.FQN.OBJECT : topType.getName());
  }

  @Override
  public HtmlChunk visitPyUnionType(PyUnionType unionType) {
    if (isOptional(unionType)) {
      return renderOptional(unionType);
    }
    // In bare mode literals aren't grouped under a single `Literal[...]`; each renders as its bare value and joins
    // the union with the normal separator (`1 | 2` rather than `Literal[1, 2]`).
    Pair<List<PyLiteralType>, List<PyType>> literalsAndOthers = isRenderingBareLiteral() ? null : extractLiterals(unionType);
    if (literalsAndOthers != null) {
      if (literalsAndOthers.second.isEmpty()) {
        return renderUnionOfLiterals(literalsAndOthers.first);
      }
      return renderUnion(ContainerUtil.prepend(
        ContainerUtil.map(literalsAndOthers.second, this::renderUnionMember),
        renderUnionOfLiterals(literalsAndOthers.first)
      ));
    }
    if (ContainerUtil.all(unionType.getMembers(), t -> t instanceof PyClassType ct && ct.isDefinition())) {
      return wrapInTypingType(render(unionType.map(type -> type != null ? ((PyClassType)type).toInstance() : null)));
    }
    if (unionType.getMembers().stream().anyMatch(it -> isUnknown(it))) {
      // Always put Unknown at the end of the union
      return renderUnion(List.of(render(PyUnionType.unionOrUnknown(ContainerUtil.filter(unionType.getMembers(), it -> !isUnknown(it)))),
                                 visitUnknownType()));
    }
    return renderUnion(ContainerUtil.map(unionType.getMembers(), this::renderUnionMember));
  }

  private HtmlChunk renderUnionMember(@Nullable PyType member) {
    HtmlChunk rendered = render(member);
    // Add parentheses around intersections in unions when using | & syntax for clarity: (A & B) | C instead of A & B | C
    // Not needed for Union[...] syntax
    if (member instanceof PyIntersectionType && isBitwiseOrUnionAvailable()) {
      return PyTypeLayout.concat(styled("(", PyHighlighter.PY_PARENTHS), rendered, styled(")", PyHighlighter.PY_PARENTHS));
    }
    return rendered;
  }

  @Override
  public HtmlChunk visitPyUnsafeUnionType(PyUnsafeUnionType unsafeUnionType) {
    return PyTypeLayout.concat(escaped("UnsafeUnion"), //NON-NLS
                               inSquareBrackets(ContainerUtil.map(unsafeUnionType.getMembers(), this::render)));
  }

  private HtmlChunk renderUnionOfLiterals(List<PyLiteralType> literals) {
    return PyTypeLayout.concat(
      qualifiedNameLink(isRenderingFqn() ? PyTypingTypeProvider.LITERAL : "Literal", PyTypingTypeProvider.LITERAL), //NON-NLS
      PyTypeLayout.bracketed(styled("[", PyHighlighter.PY_BRACKETS),
                             ContainerUtil.map(ContainerUtil.map(literals, PyLiteralType::getExpressionText), HtmlChunk::raw),
                             commaSeparator(),
                             styled(",", PyHighlighter.PY_COMMA),
                             styled("]", PyHighlighter.PY_BRACKETS),
                             true)
    );
  }

  private HtmlChunk renderUnion(List<HtmlChunk> renderedUnionMembers) {
    if (isBitwiseOrUnionAvailable()) {
      return joinWithOperator(renderedUnionMembers, "|");
    }
    return PyTypeLayout.concat(escaped(isRenderingFqn() ? "typing.Union" : "Union"), //NON-NLS
                               inSquareBrackets(renderedUnionMembers));
  }

  // TODO get rid of dedicated rendering for Optional
  private HtmlChunk renderOptional(PyUnionType type) {
    HtmlChunk renderedType = render(ContainerUtil.find(type.getMembers(), t -> !isNoneType(t)));
    if (isBitwiseOrUnionAvailable()) {
      return joinWithOperator(List.of(renderedType, render(ContainerUtil.find(type.getMembers(), t -> isNoneType(t)))), "|");
    }
    return PyTypeLayout.concat(escaped(isRenderingFqn() ? "typing.Optional" : "Optional"), //NON-NLS
                               inSquareBrackets(List.of(renderedType)));
  }

  @Override
  public HtmlChunk visitPyIntersectionType(PyIntersectionType intersectionType) {
    return joinWithOperator(ContainerUtil.map(intersectionType.getMembers(), member -> {
      HtmlChunk rendered = render(member);
      // Add parentheses around unions in intersections when using | & syntax for clarity: (A | B) & C instead of A | B & C

      if (member instanceof PyUnionType && isBitwiseOrUnionAvailable()) {
        return PyTypeLayout.concat(styled("(", PyHighlighter.PY_PARENTHS), rendered, styled(")", PyHighlighter.PY_PARENTHS));
      }
      return rendered;
    }), "&");
  }

  private static @Nullable Pair<List<PyLiteralType>, List<PyType>> extractLiterals(PyUnionType type) {
    final Collection<PyType> members = type.getMembers();

    final List<PyLiteralType> literalTypes = ContainerUtil.filterIsInstance(members, PyLiteralType.class);
    if (literalTypes.size() < 2) return null;

    final List<PyType> otherTypes = ContainerUtil.filter(members, m -> !(m instanceof PyLiteralType));

    return Pair.create(literalTypes, otherTypes);
  }

  private static boolean isOptional(PyUnionType type) {
    return type.getMembers().size() == 2 && ContainerUtil.find(type.getMembers(), m -> isNoneType(m)) != null;
  }

  @Override
  public HtmlChunk visitPyTupleType(PyTupleType tupleType) {
    HtmlChunk name = isGenericBuiltinsAvailable()
                     ? styled(isRenderingFqn() ? PyNames.FQN.TUPLE : PyNames.TUPLE, PyHighlighter.PY_BUILTIN_NAME) //NON-NLS
                     : escaped(isRenderingFqn() ? "typing.Tuple" : "Tuple"); //NON-NLS
    if (tupleType.getElementTypes().isEmpty()) {
      return PyTypeLayout.concat(name, inSquareBrackets(List.of(styled("()", PyHighlighter.PY_PARENTHS))));
    }
    List<HtmlChunk> elements = new ArrayList<>(ContainerUtil.map(tupleType.getElementTypes(), this::render));
    if (tupleType.isHomogeneous()) {
      elements.add(styled("...", PyHighlighter.PY_DOT));
    }
    return PyTypeLayout.concat(name, inSquareBrackets(elements));
  }

  @Override
  public HtmlChunk visitAnyType() {
    return qualifiedNameLink(isRenderingFqn() ? PyTypingTypeProvider.ANY : PyNames.ANY_TYPE, PyTypingTypeProvider.ANY);
  }

  @Override
  public HtmlChunk visitUnknownType() {
    if (PyAnyType.isEnabled()) {
      return HtmlChunk.raw(PyNames.UNKNOWN_TYPE);
    }
    return visitAnyType();
  }

  @Override
  public HtmlChunk visitPyType(PyType type) {
    return escaped(type.getName());
  }

  @Override
  public HtmlChunk visitPyModuleType(PyModuleType moduleType) {
    // A module's name is its own (importable) qualified name, so it doubles as a navigable link target.
    // Don't go through getTypeName(): under FQN rendering that resolves to the module's declaration element,
    // which is the `types.ModuleType` class rather than the module itself.
    String name = moduleType.getName();
    return StringUtil.isEmpty(name) ? escaped(name) : qualifiedNameLink(name, name);
  }

  HtmlChunk describeTypeParameterList(
    PyTypeParameterListOwner typeParameterListOwner,
    boolean showVariance,
    Function4<@Nullable PyType, @Nullable PyTypedElement, PsiElement, TypeEvalContext, HtmlChunk> renderer,
    TypeEvalContext context
  ) {
    var typeParameterList = typeParameterListOwner.getTypeParameterList();
    if (typeParameterList == null) return HtmlChunk.empty();
    var typeParameters = typeParameterList.getTypeParameters();
    if (typeParameters.isEmpty()) return HtmlChunk.empty();

    var result = new HtmlBuilder();
    result.append(styled("[", PyHighlighter.PY_BRACKETS));
    boolean firstTypeParam = true;
    for (PyTypeParameter typeParam : typeParameters) {
      if (!firstTypeParam) {
        result.append(styled(", ", PyHighlighter.PY_COMMA));
      }
      result.append(describeTypeParameter(typeParam, typeParam, showVariance, false, renderer, context));
      firstTypeParam = false;
    }
    result.append(styled("]", PyHighlighter.PY_BRACKETS));
    return result.toFragment();
  }

  HtmlChunk describeTypeParameterList(List<? extends PyTypeParameterType> typeParameters) {
    if (typeParameters.isEmpty()) return HtmlChunk.empty();

    var result = new HtmlBuilder();
    result.append(styled("[", PyHighlighter.PY_BRACKETS));
    boolean first = true;
    for (PyTypeParameterType typeParam : typeParameters) {
      if (!first) {
        result.append(styled(", ", PyHighlighter.PY_COMMA));
      }
      result.append(describeTypeParameter(typeParam));
      first = false;
    }
    result.append(styled("]", PyHighlighter.PY_BRACKETS));
    return result.toFragment();
  }

  static @Nullable PyTypedElement findReferenceOrTypeParameter(@Nullable PsiElement originalElement) {
    if (originalElement == null) return null;
    if (originalElement instanceof PyTypeParameter typeParam) return typeParam;
    if (originalElement instanceof PyReferenceExpression refExpr) return refExpr;
    if (originalElement.getParent() instanceof PyTypeParameter typeParam) return typeParam;
    if (originalElement.getParent() instanceof PyReferenceExpression refExpr) return refExpr;
    PsiElement prevElement = PsiTreeUtil.prevLeaf(originalElement, true);
    if (prevElement == null) return null;
    if (prevElement.getParent() instanceof PyTypeParameter typeParam) return typeParam;
    if (prevElement instanceof PyReferenceExpression refExpr) return refExpr;
    if (prevElement.getParent() instanceof PyReferenceExpression refExpr) return refExpr;
    return null;
  }

  HtmlChunk describeTypeParameter(PyTypeParameter typeParameter,
                                  @Nullable PsiElement originalElement,
                                  boolean showVariance,
                                  boolean showKind,
                                  Function4<@Nullable PyType, @Nullable PyTypedElement, PsiElement, TypeEvalContext, HtmlChunk> renderer,
                                  TypeEvalContext context) {
    PyVariance variance = null;
    if (showVariance) {
      PyTypedElement refExpr = findReferenceOrTypeParameter(originalElement);
      boolean effectivelyInvariant = isEffectivelyInvariant(refExpr, context);
      variance = effectivelyInvariant
                 ? PyVariance.INVARIANT
                 : PyInferredVarianceJudgment.getDeclaredOrInferredVariance(refExpr, context);
    }
    PyExpression boundExpression = typeParameter.getBoundExpression();
    Ref<PyType> boundExprTR = boundExpression == null ? null : PyTypingTypeProvider.getType(boundExpression, context);
    HtmlChunk bound = boundExprTR != null && boundExprTR.get() != null
                      ? renderer.invoke(boundExprTR.get(), boundExpression, boundExpression, context)
                      : null;
    PyExpression defaultExpr = typeParameter.getDefaultExpression();
    Ref<PyType> defaultExprTR = defaultExpr == null ? null : PyTypingTypeProvider.getType(defaultExpr, context);
    HtmlChunk defaultValue = defaultExprTR != null && defaultExprTR.get() != null
                             ? renderer.invoke(defaultExprTR.get(), defaultExpr, defaultExpr, context)
                             : null;
    return describeTypeParameter(variance, StringUtil.notNullize(typeParameter.getName()), bound, defaultValue, typeParameter.getKind(),
                                 showKind);
  }

  HtmlChunk describeTypeParameter(PyTypeParameterType typeParameter) {
    var bound = typeParameter instanceof PyTypeVarType typeVar ? typeVar.getBound() : PyAnyType.getUnknown();
    var defaultTypeRef = typeParameter.getDefaultType();
    var defaultType = defaultTypeRef != null ? defaultTypeRef.get() : null;
    // PyTypeParameterTypes have the stars in the name but PyTypeParameters don't
    String name = typeParameter.getName().replaceFirst("\\*+", "");
    HtmlChunk renderedBound = !isAnyOrUnknown(bound) ? render(bound) : null;
    HtmlChunk renderedType = defaultType == null ? null
                             : typeParameter instanceof PyParamSpecType && isUnknown(defaultType) ? styled("...", PyHighlighter.PY_DOT)
                             : render(defaultType);
    PyAstTypeParameter.Kind parameterKind = switch (typeParameter) {
      case PyTypeVarType ignored -> PyTypeParameter.Kind.TypeVar;
      case PyTypeVarTupleType ignored -> PyTypeParameter.Kind.TypeVarTuple;
      case PyParamSpecType ignored -> PyTypeParameter.Kind.ParamSpec;
      default -> null;
    };
    return describeTypeParameter(null, name, renderedBound, renderedType, parameterKind, false);
  }

  private HtmlChunk describeTypeParameter(
    @Nullable PyVariance variance,
    String name,
    @Nullable HtmlChunk bound,
    @Nullable HtmlChunk defaultValue,
    PyTypeParameter.@Nullable Kind kind,
    boolean showKind
  ) {
    var result = new HtmlBuilder();
    if (variance != null) {
      String varianceStr = switch (variance) {
        case INFER_VARIANCE -> null;
        case COVARIANT -> "out "; //NON-NLS
        case CONTRAVARIANT -> "in "; //NON-NLS
        case INVARIANT -> "invariant "; //NON-NLS
        case BIVARIANT -> "bivariant "; //NON-NLS
      };
      if (varianceStr != null) {
        result.append(styled(varianceStr, PyHighlighter.PY_KEYWORD));
      }
    }
    var prefix = switch (kind) {
      case null -> null;
      case TypeVar -> null;
      case TypeVarTuple -> "*";
      case ParamSpec -> "**";
    };
    if (prefix != null) {
      result.append(prefix);
    }
    result.append(styled(name, PyHighlighter.PY_TYPE_PARAMETER));
    if (bound != null) {
      result.append(styled(": ", PyHighlighter.PY_OPERATION_SIGN));
      result.append(styled(bound, PyHighlighter.PY_ANNOTATION));
    }
    if (defaultValue != null) {
      result.append(styled(" = ", PyHighlighter.PY_OPERATION_SIGN));
      result.append(styled(defaultValue, PyHighlighter.PY_ANNOTATION));
    }
    if (showKind && kind != null) {
      result
        .append(", ")
        .append(PyLocalize.qdocTypeParameterKind())
        .append(" ")
        .append(styled(kind.name(), PyHighlighter.PY_ANNOTATION)); //NON-NLS
    }
    return result.toFragment();
  }

  @Override
  public HtmlChunk visitPyCallableType(PyCallableType callableType) {
    List<HtmlChunk> result = new ArrayList<>();
    var typeParameters = callableType.getTypeParameters(myTypeEvalContext);
    if (typeParameters != null) {
      result.add(describeTypeParameterList(typeParameters));
    }
    List<PyCallableParameter> parameters = callableType.getParameters(myTypeEvalContext);
    List<HtmlChunk> renderedParameters = parameters != null
                                         ? ContainerUtil.map(parameters, this::visitPyCallableParameter)
                                         : List.of(styled("...", PyHighlighter.PY_DOT));
    result.add(bracketed(styled("(", PyHighlighter.PY_PARENTHS), renderedParameters, styled(")", PyHighlighter.PY_PARENTHS)));
    result.add(escaped(" -> "));
    result.add(render(callableType.getReturnType(myTypeEvalContext)));
    return PyTypeLayout.concat(result);
  }

  protected HtmlChunk visitPyCallableParameter(PyCallableParameter param) {
    List<HtmlChunk> result = new ArrayList<>();
    if (param.isPositionOnlySeparator()) {
      result.add(escaped(PyAstSlashParameter.TEXT));
    }
    else if (param.isKeywordOnlySeparator()) {
      result.add(escaped(PyAstSingleStarParameter.TEXT));
    }
    else if (param.isPositionalContainer() || param.isKeywordContainer()) {
      PyType type = param.getArgumentType(myTypeEvalContext);
      if (param.getName() != null) {
        result.add(escaped(param.isPositionalContainer() ? "*" : "**"));
        result.add(styled(param.getName(), PyHighlighter.PY_PARAMETER));
        if (!isAnyOrUnknown(type)) {
          result.add(styled(": ", PyHighlighter.PY_OPERATION_SIGN));
          result.add(render(type));
        }
      }
      else {
        result.add(render(type));
      }
    }
    else {
      PyType type = param.getType(myTypeEvalContext);
      // TODO remove that
      if (!(type instanceof PyParamSpecType) && !(type instanceof PyConcatenateType)) {
        if (param.getName() != null) {
          result.add(styled(param.getName(), PyHighlighter.PY_PARAMETER));
          result.add(styled(": ", PyHighlighter.PY_OPERATION_SIGN));
        }
      }
      result.add(render(type));
    }
    return PyTypeLayout.concat(result);
  }

  @Override
  public HtmlChunk visitPyTypeParameterType(PyTypeParameterType typeParameterType) {
    List<HtmlChunk> result = new ArrayList<>();
    result.add(escaped(getTypeName(typeParameterType)));
    if (isRenderingTypeVarBounds()) {
      var effectiveBound = typeParameterType.getEffectiveBound();
      if (!isUnknown(effectiveBound)) {
        result.add(escaped(" ≤: "));
        result.add(render(effectiveBound));
      }
    }
    if (typeParameterType instanceof PyTypeVarType typeVarType && typeVarType.isDefinition()) {
      return wrapInTypingType(PyTypeLayout.concat(result));
    }
    return PyTypeLayout.concat(result);
  }

  @Override
  public HtmlChunk visitPyUnpackedTupleType(PyUnpackedTupleType unpackedTupleType) {
    List<HtmlChunk> elements = unpackedTupleType.isUnbound()
                               ? List.of(render(unpackedTupleType.getElementTypes().getFirst()), styled("...", PyHighlighter.PY_DOT))
                               : ContainerUtil.map(unpackedTupleType.getElementTypes(), this::render);
    return PyTypeLayout.concat(HtmlChunk.text("*"),
                               styled(isRenderingFqn() ? "builtins.tuple" : "tuple", PyHighlighter.PY_BUILTIN_NAME), //NON-NLS
                               inSquareBrackets(elements));
  }

  @Override
  public HtmlChunk visitPySelfType(PySelfType selfType) {
    // Don't render Self as a type parameter
    HtmlChunk selfTypeRender = PyTypeLayout.concat(className(isRenderingFqn() ? "typing.Self" : "Self"), //NON-NLS
                                                   HtmlChunk.text("@"),
                                                   render(selfType.getScopeClassType().toInstance()));
    return selfType.isDefinition() ? wrapInTypingType(selfTypeRender) : selfTypeRender;
  }

  @Override
  public HtmlChunk visitPyCallableParameterListType(PyCallableParameterListType callableParameterListType) {
    return bracketed(HtmlChunk.text("["),
                     ContainerUtil.map(callableParameterListType.getParameters(), this::visitPyCallableParameter),
                     HtmlChunk.text("]"));
  }

  @Override
  public HtmlChunk visitPyLiteralType(PyLiteralType literalType) {
    HtmlBuilder result = new HtmlBuilder();
    // Bare mode drops the redundant `Literal[...]` wrapper and shows just the value (`1`, `'a'`, `Color.RED`).
    boolean bare = isRenderingBareLiteral();
    if (!bare) {
      result.append(qualifiedNameLink(isRenderingFqn() ? PyTypingTypeProvider.LITERAL : "Literal", PyTypingTypeProvider.LITERAL)); //NON-NLS
      result.append("[");
    }
    @Nullable String classQName = literalType.getClassQName();
    if (isRenderingFqn() && classQName != null && literalType.getEnumMemberName() != null) {
      result.append(classQName);
      result.append(".");
      result.append(literalType.getEnumMemberName());
    }
    else {
      result.appendRaw(literalType.getExpressionText()); // append raw since the literal can include quotes: Literal["foo"]
    }
    if (!bare) {
      result.append("]");
    }
    return literalType.isDefinition() ? wrapInTypingType(result.toFragment()) : result.toFragment();
  }

  @Override
  public HtmlChunk visitPyOverloadType(PyOverloadType overloadType) {
    return PyTypeLayout.concat(HtmlChunk.text(overloadType.getName()),
                               bracketed(HtmlChunk.text("["), ContainerUtil.map(overloadType.getItems(), this::render), HtmlChunk.text("]")));
  }

  protected final @Nullable String getTypeName(PyType type) {
    if (isNoneType(type)) {
      return PyNames.NONE;
    }
    if (isRenderingFqn()) {
      PyQualifiedNameOwner declarationElement = type.getDeclarationElement();
      if (declarationElement != null) {
        return declarationElement.getQualifiedName();
      }
      if (type instanceof PyClassLikeType classLikeType) {
        return classLikeType.getClassQName();
      }
      return null;
    }
    return type.getName();
  }
}
