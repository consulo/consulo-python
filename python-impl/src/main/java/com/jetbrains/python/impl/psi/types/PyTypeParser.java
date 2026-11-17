// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.google.common.collect.ImmutableMap;
import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.QualifiedName;
import java.util.function.Function;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.impl.psi.types.functionalParser.ForwardDeclaration;
import com.jetbrains.python.impl.psi.types.functionalParser.FunctionalParser;
import com.jetbrains.python.impl.psi.types.functionalParser.ParserException;
import com.jetbrains.python.impl.psi.types.functionalParser.Token;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.TypeEvalContext;

import com.jetbrains.python.psi.types.PyTypeTokenTypes;

import static com.jetbrains.python.psi.types.PyTypeTokenTypes.IDENTIFIER;
import static com.jetbrains.python.psi.types.PyTypeTokenTypes.PARAMETER;
import static com.jetbrains.python.impl.psi.types.functionalParser.FunctionalParserBase.many;
import static com.jetbrains.python.impl.psi.types.functionalParser.FunctionalParserBase.maybe;
import static com.jetbrains.python.impl.psi.types.functionalParser.FunctionalParserBase.token;

/**
 * Parser for the legacy PEP-484-incompatible type hint format described in
 * <a href="https://github.com/JetBrains/python-skeletons/blob/master/README-obsolete.md">the repository of python-skeletons</a>.
 * It doesn't support many features of the modern Python type hints, e.g. intersections types, complex generic types, TypeIs/TypeGuard, etc.
 * and uses different rules for name resolution (in short, importing names is not required).
 * For most cases one should switch to {@link com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider#getStringBasedType},
 * but if you still need to support the legacy format for some reason, consider using the API alias {@link PyLegacyDocstringTypeParser}
 * to make the intention more explicit.
 *
 * @see com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider#getStringBasedType(String, PsiElement, TypeEvalContext)
 * @deprecated Use {@link PyLegacyDocstringTypeParser} instead.
 */
@Deprecated
public final class PyTypeParser {
    private static final ParseResult EMPTY_RESULT = new ParseResult(null, Collections.emptyMap(), Collections.emptyMap(),
                                                                    Collections.emptyMap());

    public static class ParseResult {
        private final @Nullable PyType myType;
        private final Map<TextRange, ? extends PyType> myTypes;
        private final Map<? extends PyType, TextRange> myFullRanges;
        private final Map<? extends PyType, PyImportElement> myImports;

        ParseResult(@Nullable PyType type, Map<TextRange, ? extends PyType> types,
                    Map<? extends PyType, TextRange> fullRanges,
                    Map<? extends PyType, PyImportElement> imports) {
            myType = type;
            myTypes = types;
            myFullRanges = fullRanges;
            myImports = imports;
        }

        ParseResult(PyType type, TextRange range) {
            this(type, ImmutableMap.of(range, type), ImmutableMap.of(type, range), ImmutableMap.of());
        }

        public @Nullable PyType getType() {
            if (myType == null) {  // Includes 'this == EMPTY_RESULT'
                return PyAnyType.getUnknown();
            }
            return myType;
        }

        public Map<TextRange, ? extends PyType> getTypes() {
            return myTypes;
        }

        public Map<? extends PyType, TextRange> getFullRanges() {
            return myFullRanges;
        }

        public Map<? extends PyType, PyImportElement> getImports() {
            return myImports;
        }

        private ParseResult merge(ParseResult result) {
            Map<TextRange, PyType> types = new HashMap<>();
            Map<PyType, TextRange> fullRanges = new HashMap<>();
            Map<PyType, PyImportElement> imports = new HashMap<>();
            types.putAll(myTypes);
            types.putAll(result.getTypes());
            fullRanges.putAll(myFullRanges);
            fullRanges.putAll(result.getFullRanges());
            imports.putAll(myImports);
            imports.putAll(result.getImports());
            return new ParseResult(myType, types, fullRanges, imports);
        }

        private ParseResult withType(@Nullable PyType type) {
            return new ParseResult(type, myTypes, myFullRanges, myImports);
        }
    }

    /**
     * @param anchor should never be null or null will be returned
     * @return null either if there was an error during parsing or if extracted type is equivalent to <tt>Any</tt> or <tt>undefined</tt>
     */
    public static @Nullable PyType getTypeByName(@Nullable PsiElement anchor, String type) {
        if (anchor == null) {
            return EMPTY_RESULT.getType();
        }
        return parse(anchor, type).getType();
    }

    /**
     * @param anchor  should never be null or null will be returned
     * @param context type evaluation context
     * @return null either if there was an error during parsing or if extracted type is equivalent to <tt>Any</tt> or <tt>undefined</tt>
     */
    public static @Nullable PyType getTypeByName(@Nullable PsiElement anchor, String type, TypeEvalContext context) {
        if (anchor == null) {
            return EMPTY_RESULT.getType();
        }
        return parse(anchor, type, context).getType();
    }

    /**
     * @param anchor  should never be null or null will be returned
     * @param context type evaluation context
     * @param fqnOnly if true, resolves names using only fully-qualified lookup (ignores local scope/imported aliases)
     * @return null either if there was an error during parsing or if extracted type is equivalent to <tt>Any</tt> or <tt>undefined</tt>
     */
    public static @Nullable PyType getTypeByName(@Nullable PsiElement anchor,
                                                 String type,
                                                 TypeEvalContext context,
                                                 boolean fqnOnly) {
        if (anchor == null) {
            return EMPTY_RESULT.getType();
        }
        return parse(anchor, type, context, fqnOnly).getType();
    }

    /**
     * @param anchor should never be null or {@link PyTypeParser#EMPTY_RESULT} will be returned
     * @param type   representation of the type to parse
     */
    public static ParseResult parse(PsiElement anchor, String type) {
        return parse(anchor, type, TypeEvalContext.codeInsightFallback(anchor.getProject()));
    }

    /**
     * @param anchor  should never be null or {@link PyTypeParser#EMPTY_RESULT} will be returned
     * @param type    representation of the type to parse
     * @param context type evaluation context
     */
    public static ParseResult parse(PsiElement anchor, String type, TypeEvalContext context) {
        return parse(anchor, type, context, false);
    }

    /**
     * @param anchor  should never be null or {@link PyTypeParser#EMPTY_RESULT} will be returned
     * @param type    representation of the type to parse
     * @param context type evaluation context
     * @param fqnOnly if true, resolves names using only fully-qualified lookup (ignores local scope/imported aliases)
     */
    public static ParseResult parse(PsiElement anchor,
                                             String type,
                                             TypeEvalContext context,
                                             boolean fqnOnly) {
        PyPsiUtils.assertValid(anchor);

        ForwardDeclaration<ParseResult, PyElementType> typeExpr = ForwardDeclaration.create();

        FunctionalParser<ParseResult, PyElementType> classType =
            token(IDENTIFIER).then(many(op(".").skipThen(token(IDENTIFIER))))
                .map(new MakeSimpleType(anchor, context, fqnOnly))
                .cached()
                .named("class-type");

        FunctionalParser<ParseResult, PyElementType> tupleType =
            op("(").skipThen(typeExpr).then(many(op(",").skipThen(typeExpr))).thenSkip(op(")"))
                .map(value -> {
                    ParseResult result = value.getFirst();
                    List<ParseResult> rest = value.getSecond();
                    if (rest.isEmpty()) {
                        return result;
                    }
                    List<PyType> types = new ArrayList<>();
                    types.add(result.getType());
                    for (ParseResult r : rest) {
                        result = result.merge(r);
                        types.add(r.getType());
                    }
                    return result.withType(PyTupleType.create(anchor, types));
                })
                .named("tuple-type");

        FunctionalParser<ParseResult, PyElementType> typeParameter =
            token(PARAMETER).then(maybe(op("<=").skipThen(typeExpr)))
                .map(value -> {
                    Token<PyElementType> token = value.getFirst();
                    String name = token.getText().toString();
                    TextRange range = token.getRange();
                    ParseResult boundResult = value.getSecond();
                    if (boundResult != null) {
                        PyTypeVarType type1 = new PyTypeVarTypeImpl(name, boundResult.getType());
                        ParseResult result = new ParseResult(type1, range);
                        return result.merge(boundResult).withType(type1);
                    }
                    return new ParseResult(new PyTypeVarTypeImpl(name, PyAnyType.getUnknown()), range);
                })
                .named("type-parameter");

        FunctionalParser<ParseResult, PyElementType> simpleExpr =
            classType
                .or(tupleType)
                .or(typeParameter)
                .named("simple-expr");

        FunctionalParser<ParseResult, PyElementType> paramExpr =
            classType.thenSkip(op("[")).then(typeExpr).then(many(op(",").skipThen(typeExpr))).thenSkip(op("]"))
                .map(value -> {
                    Pair<ParseResult, ParseResult> firstPair = value.getFirst();
                    ParseResult first = firstPair.getFirst();
                    ParseResult second = firstPair.getSecond();
                    List<ParseResult> third = value.getSecond();
                    PyType firstType = first.getType();
                    List<PyType> typesInBrackets = new ArrayList<>();
                    typesInBrackets.add(second.getType());
                    ParseResult result = first;
                    result = result.merge(second);
                    for (ParseResult r : third) {
                        typesInBrackets.add(r.getType());
                        result = result.merge(r);
                    }
                    List<PyType> elementTypes = third.isEmpty() ? Collections.singletonList(second.getType()) : typesInBrackets;
                    if (firstType instanceof PyClassType) {
                        PyType type1 = new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false, elementTypes);
                        return result.withType(type1);
                    }
                    return EMPTY_RESULT;
                })
                .or(classType.thenSkip(op("of")).then(simpleExpr)
                      .map(value -> {
                        ParseResult firstResult = value.getFirst();
                        ParseResult secondResult = value.getSecond();
                        ParseResult result = firstResult.merge(secondResult);
                        PyType firstType = firstResult.getType();
                        PyType secondType = secondResult.getType();
                        if (firstType != null) {
                          if (firstType instanceof PyClassType && secondType != null) {
                            return result.withType(new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false,
                                                                            Collections.singletonList(secondType)));
                          }
                          return result.withType(firstType);
                        }
                        return EMPTY_RESULT;
                      }))
                .or(classType.thenSkip(op("from")).then(simpleExpr).thenSkip(op("to")).then(simpleExpr)
                      .map(value -> {
                        Pair<ParseResult, ParseResult> firstPair = value.getFirst();
                        ParseResult first = firstPair.getFirst();
                        ParseResult second = firstPair.getSecond();
                        ParseResult third = value.getSecond();
                        PyType firstType = first.getType();
                        if (firstType instanceof PyClassType) {
                          List<PyType> elementTypes = Arrays.asList(second.getType(), third.getType());
                          PyClassTypeImpl type1 = new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false,
                                                                                 elementTypes);
                          return first.merge(second).merge(third).withType(type1);
                        }
                        return EMPTY_RESULT;
                      }))
                .named("param-expr");

        FunctionalParser<ParseResult, PyElementType> callableExpr =
            op("(").skipThen(maybe(typeExpr.then(many(op(",").skipThen(typeExpr))))).thenSkip(op(")")).thenSkip(op("->")).then(typeExpr)
                .map(
                    value -> {
                        List<PyCallableParameter> parameters = new ArrayList<>();
                        ParseResult returnResult = value.getSecond();
                        ParseResult result;
                        Pair<ParseResult, List<ParseResult>> firstPair = value.getFirst();
                        if (firstPair != null) {
                            ParseResult first = firstPair.getFirst();
                            List<ParseResult> second = firstPair.getSecond();
                            result = first;
                            parameters.add(PyCallableParameterImpl.nonPsi(first.getType()));
                            for (ParseResult r : second) {
                                result = result.merge(r);
                                parameters.add(PyCallableParameterImpl.nonPsi(r.getType()));
                            }
                            result = result.merge(returnResult);
                        }
                        else {
                            result = returnResult;
                        }
                        return result.withType(new PyCallableTypeImpl(parameters, returnResult.getType()));
                    })
                .named("callable-expr");

        FunctionalParser<ParseResult, PyElementType> singleExpr =
            paramExpr
                .or(callableExpr)
                .or(simpleExpr)
                .named("single-expr");

        FunctionalParser<ParseResult, PyElementType> unionExpr =
            singleExpr.then(many(op("or").or(op("|")).skipThen(singleExpr)))
                .map(value -> {
                    ParseResult first = value.getFirst();
                    List<ParseResult> rest = value.getSecond();
                    if (rest.isEmpty()) {
                        return first;
                    }
                    List<PyType> types = new ArrayList<>();
                    types.add(first.getType());
                    ParseResult result = first;
                    for (ParseResult r : rest) {
                        types.add(r.getType());
                        result = result.merge(r);
                    }
                    return result.withType(PyUnionType.unionOrUnknown(types));
                })
                .named("union-expr");

        typeExpr
            .define(unionExpr)
            .named("type-expr");

        FunctionalParser<ParseResult, PyElementType> typeFile =
            typeExpr
                .endOfInput()
                .named("type-file");

        try {
            return typeFile.parse(tokenize(type));
        }
        catch (ParserException e) {
            return EMPTY_RESULT;
        }
    }

    private static class MakeSimpleType implements Function<Pair<Token<PyElementType>, List<Token<PyElementType>>>, ParseResult> {
        private final PsiElement myAnchor;
        private final TypeEvalContext myContext;
        private final boolean myFqnOnly;

        MakeSimpleType(PsiElement anchor, TypeEvalContext context) {
            this(anchor, context, false);
        }

        MakeSimpleType(PsiElement anchor, TypeEvalContext context, boolean fqnOnly) {
            myAnchor = anchor;
            myContext = context;
            myFqnOnly = fqnOnly;
        }

        @Override
        public @Nullable ParseResult apply(Pair<Token<PyElementType>, List<Token<PyElementType>>> value) {
            Token<PyElementType> first = value.getFirst();
            List<Token<PyElementType>> rest = value.getSecond();
            TextRange firstRange = first.getRange();
            boolean unqualified = rest.isEmpty();

            if (unqualified) {
                ParseResult result = parseBuiltinType(first);
                if (result != null) {
                    return result;
                }
            }

            PsiFile file = myAnchor.getContainingFile();
            List<Token<PyElementType>> tokens = new ArrayList<>();
            tokens.add(first);
            tokens.addAll(rest);

            if (file instanceof PyFile pyFile) {
                Map<TextRange, PyType> types = new HashMap<>();
                Map<PyType, TextRange> fullRanges = new HashMap<>();
                Map<PyType, PyImportElement> imports = new HashMap<>();

                PyType type = resolveQualifierType(tokens, pyFile, myContext, types, fullRanges, imports);

                if (type != null) {
                    PyResolveContext resolveContext = PyResolveContext.defaultContext(myContext);
                    PyExpression expression = myAnchor instanceof PyExpression ? (PyExpression)myAnchor : null;

                    for (Token<PyElementType> token : tokens) {
                        PyType qualifierType = type;
                        type = null;
                        List<? extends RatedResolveResult> results = qualifierType.resolveMember(token.getText().toString(), expression,
                                                                                                       AccessDirection.READ, resolveContext);
                        if (results != null && !results.isEmpty()) {
                            PsiElement resolved = results.get(0).getElement();
                            if (resolved instanceof PyTypedElement) {
                                type = myContext.getType((PyTypedElement)resolved);
                                if (type != null && !allowResolveToType(type)) {
                                    type = null;
                                    break;
                                }
                                if (type instanceof PyClassLikeType) {
                                    type = ((PyClassLikeType)type).toInstance();
                                }
                            }
                        }
                        if (type == null) {
                            break;
                        }
                        types.put(token.getRange(), type);
                        fullRanges.put(type, TextRange.create(firstRange.getStartOffset(), token.getRange().getEndOffset()));
                    }
                    if (type != null) {
                        return new ParseResult(type, types, fullRanges, imports);
                    }
                }
            }

            return EMPTY_RESULT;
        }

        private @Nullable PyType resolveQualifierType(List<Token<PyElementType>> tokens,
                                                      PyFile file,
                                                      TypeEvalContext context,
                                                      Map<TextRange, PyType> types,
                                                      Map<PyType, TextRange> fullRanges,
                                                      Map<PyType, PyImportElement> imports) {
            if (tokens.isEmpty()) {
                return null;
            }
            Token<PyElementType> firstToken = tokens.get(0);
            String firstText = firstToken.getText().toString();
            TextRange firstRange = firstToken.getRange();

            List<RatedResolveResult> resolveResults;
            if (myFqnOnly) {
                // First, try to resolve from "typing" for unqualified names (e.g., Literal, Any)
                var qNameContext = PyResolveImportUtil.fromFoothold(myAnchor);
                PsiElement typingMember =
                    PyResolveImportUtil.resolveTopLevelMember(QualifiedName.fromDottedString("typing." + firstText), qNameContext);
                if (typingMember != null) {
                    resolveResults = Collections.singletonList(new RatedResolveResult(RatedResolveResult.RATE_NORMAL, typingMember));
                }
                else {
                    // Fall back to fully qualified name search (handled below using getImplicitlyResolvedType)
                    resolveResults = Collections.emptyList();
                }
            }
            else {
                resolveResults = file.multiResolveName(firstText);
            }

            if (resolveResults.isEmpty()) {
                return getImplicitlyResolvedType(tokens, context, types, fullRanges, firstRange);
            }
            List<PyType> members = new ArrayList<>();
            for (RatedResolveResult result : resolveResults) {
                PsiElement resolved = result.getElement();
                PyType type = null;
                if (resolved instanceof PyTypedElement) {
                    type = context.getType((PyTypedElement)resolved);
                }
                if (type != null) {
                    if (!allowResolveToType(type)) {
                        continue;
                    }
                    if (type instanceof PyClassLikeType) {
                        type = ((PyClassLikeType)type).toInstance();
                    }
                    types.put(firstRange, type);
                    fullRanges.put(type, firstRange);
                    for (PyFromImportStatement fromImportStatement : file.getFromImports()) {
                        for (PyImportElement importElement : fromImportStatement.getImportElements()) {
                            if (firstText.equals(importElement.getVisibleName())) {
                                imports.put(type, importElement);
                            }
                        }
                    }
                    for (PyImportElement importElement : file.getImportTargets()) {
                        if (firstText.equals(importElement.getVisibleName())) {
                            imports.put(type, importElement);
                        }
                    }
                }
                members.add(type);
            }
            if (!members.isEmpty()) {
                tokens.remove(0);
            }
            return PyUnionType.unionOrUnknown(members);
        }

        private @Nullable PyType getImplicitlyResolvedType(List<Token<PyElementType>> tokens,
                                                           TypeEvalContext context,
                                                           Map<TextRange, PyType> types,
                                                           Map<PyType, TextRange> fullRanges,
                                                           TextRange firstRange) {
            PyType type = null;
            QualifiedName qName = null;
            while (!tokens.isEmpty()) {
                Token<PyElementType> token = tokens.get(0);
                String name = token.getText().toString();
                qName = qName != null ? qName.append(name) : QualifiedName.fromComponents(name);
                List<PsiElement> modules = PyResolveImportUtil.resolveQualifiedName(qName, PyResolveImportUtil.fromFoothold(myAnchor));
                PsiElement module = !modules.isEmpty() ? modules.get(0) : null;
                if (module == null) {
                    break;
                }
                if (module instanceof PsiDirectory) {
                    module = PyUtil.getPackageElement((PsiDirectory)module, myAnchor);
                }
                if (module instanceof PyTypedElement) {
                    PyType moduleType = context.getType((PyTypedElement)module);
                    if (moduleType != null) {
                        type = moduleType;
                        types.put(token.getRange(), type);
                        fullRanges.put(type, TextRange.create(firstRange.getStartOffset(), token.getRange().getEndOffset()));
                    }
                }
                tokens.remove(0);
            }
            return type;
        }

        private static boolean allowResolveToType(PyType type) {
            return type instanceof PyClassLikeType || type instanceof PyModuleType || type instanceof PyImportedModuleType ||
                   type instanceof PyTypeVarType;
        }

        private @Nullable ParseResult parseBuiltinType(Token<PyElementType> token) {
            PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(myAnchor);
            String name = token.getText().toString();
            TextRange range = token.getRange();

            if (PyNames.ANY_TYPE.equals(name)) {
                return EMPTY_RESULT;
            }
            else if (PyNames.NONE.equals(name)) {
                var type = builtinCache.getNoneType();
                return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
            }
            else if ("integer".equals(name) || PyNames.TYPE_LONG.equals(name)) {
                PyClassType type = builtinCache.getIntType();
                return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
            }
            else if ("string".equals(name)) {
                PyType type = builtinCache.getStringType(LanguageLevel.forElement(myAnchor));
                return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
            }
            else if ("bytestring".equals(name)) {
                PyType type = builtinCache.getByteStringType(LanguageLevel.forElement(myAnchor));
                return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
            }
            else if ("bytes".equals(name)) {
                PyClassType type = builtinCache.getBytesType(LanguageLevel.forElement(myAnchor));
                return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
            }
            else if (PyNames.TYPE_UNICODE.equals(name)) {
                PyClassType type = builtinCache.getUnicodeType(LanguageLevel.forElement(myAnchor));
                return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
            }
            else if ("boolean".equals(name)) {
                PyClassType type = builtinCache.getBoolType();
                return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
            }
            else if (PyNames.TYPE_FLOAT.equals(name)) {
                PyClassType type = builtinCache.getFloatType();
                PyType enriched = type != null ? PyNumericTowerUtil.enrich(type) : null;
                return enriched != null ? new ParseResult(enriched, range) : EMPTY_RESULT;
            }
            else if (PyNames.TYPE_COMPLEX.equals(name)) {
                PyClassType type = builtinCache.getComplexType();
                PyType enriched = type != null ? PyNumericTowerUtil.enrich(type) : null;
                return enriched != null ? new ParseResult(enriched, range) : EMPTY_RESULT;
            }
            else if ("dictionary".equals(name)) {
                PyClassType type = builtinCache.getDictType();
                return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
            }
            else if (PyNames.BASESTRING.equals(name)) {
                PyType type = builtinCache.getStrOrUnicodeType();
                return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
            }

            PyType builtinType = builtinCache.getObjectType(name);
            if (builtinType != null) {
                return new ParseResult(builtinType, range);
            }

            return null;
        }
    }

    private static FunctionalParser<Token<PyElementType>, PyElementType> op(@Nullable String text) {
        return token(PyTypeTokenTypes.OP, text);
    }

    private static List<Token<PyElementType>> tokenize(String s) {
        List<Token<PyElementType>> tokens = new ArrayList<>();
        _PyTypeLexer lexer = new _PyTypeLexer(new StringReader(s));
        lexer.reset(s, 0, s.length(), lexer.yystate());
        try {
            PyElementType type;
            while ((type = lexer.advance()) != null) {
                if (type == PyTypeTokenTypes.SPACE || type == PyTypeTokenTypes.MARKUP) {
                    continue;
                }
                TextRange range = TextRange.create(lexer.getTokenStart(), lexer.getTokenEnd());
                Token<PyElementType> token = new Token<>(type, lexer.yytext(), range);
                tokens.add(token);
            }
        }
        catch (IOException | Error e) {
            return Collections.emptyList();
        }
        return tokens;
    }
}
