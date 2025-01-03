/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.psi.types;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameResolverImpl;
import com.jetbrains.python.impl.psi.types.functionalParser.ForwardDeclaration;
import com.jetbrains.python.impl.psi.types.functionalParser.FunctionalParser;
import com.jetbrains.python.impl.psi.types.functionalParser.ParserException;
import com.jetbrains.python.impl.psi.types.functionalParser.Token;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.function.Function;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.impl.psi.types.PyTypeTokenTypes.IDENTIFIER;
import static com.jetbrains.python.impl.psi.types.PyTypeTokenTypes.PARAMETER;
import static com.jetbrains.python.impl.psi.types.functionalParser.FunctionalParserBase.*;

/**
 * @author vlan
 */
public class PyTypeParser {
  private static final ParseResult EMPTY_RESULT =
    new ParseResult(null, null, Collections.<TextRange, PyType>emptyMap(), Collections.<PyType, TextRange>emptyMap(), Collections.<PyType,
      PyImportElement>emptyMap());

  public static class ParseResult {
    @Nullable
    private final PsiElement myElement;
    @Nullable
    private final PyType myType;
    @Nonnull
    private final Map<TextRange, ? extends PyType> myTypes;
    @Nonnull
    private final Map<? extends PyType, TextRange> myFullRanges;
    @Nonnull
    private final Map<? extends PyType, PyImportElement> myImports;

    ParseResult(@Nullable PsiElement element,
                @Nullable PyType type,
                @Nonnull Map<TextRange, ? extends PyType> types,
                @Nonnull Map<? extends PyType, TextRange> fullRanges,
                @Nonnull Map<? extends PyType, PyImportElement> imports) {
      myElement = element;
      myType = type;
      myTypes = types;
      myFullRanges = fullRanges;
      myImports = imports;
    }

    ParseResult(@Nullable PsiElement element, @Nonnull PyType type, @Nonnull TextRange range) {
      this(element, type, ImmutableMap.of(range, type), ImmutableMap.of(type, range), ImmutableMap.<PyType, PyImportElement>of());
    }

    @Nullable
    private PsiElement getElement() {
      return myElement;
    }

    @Nullable
    public PyType getType() {
      return myType;
    }

    @Nonnull
    public Map<TextRange, ? extends PyType> getTypes() {
      return myTypes;
    }

    @Nonnull
    public Map<? extends PyType, TextRange> getFullRanges() {
      return myFullRanges;
    }

    @Nonnull
    public Map<? extends PyType, PyImportElement> getImports() {
      return myImports;
    }

    private ParseResult merge(@Nonnull ParseResult result) {
      final Map<TextRange, PyType> types = new HashMap<>();
      final Map<PyType, TextRange> fullRanges = new HashMap<>();
      final Map<PyType, PyImportElement> imports = new HashMap<>();
      types.putAll(myTypes);
      types.putAll(result.getTypes());
      fullRanges.putAll(myFullRanges);
      fullRanges.putAll(result.getFullRanges());
      imports.putAll(myImports);
      imports.putAll(result.getImports());
      return new ParseResult(myElement, myType, types, fullRanges, imports);
    }

    private ParseResult withType(@Nullable PyType type) {
      return new ParseResult(myElement, type, myTypes, myFullRanges, myImports);
    }
  }

  /**
   * @param anchor should never be null or null will be returned
   * @return null either if there was an error during parsing or if extracted type is equivalent to <tt>Any</tt> or <tt>undefined</tt>
   */
  @Nullable
  public static PyType getTypeByName(@Nullable final PsiElement anchor, @Nonnull String type) {
    return parse(anchor, type).getType();
  }

  /**
   * @param anchor should never be null or null will be returned
   */
  @Nonnull
  public static ParseResult parse(@Nullable final PsiElement anchor, @Nonnull String type) {
    PyPsiUtils.assertValid(anchor);
    if (anchor == null) {
      return EMPTY_RESULT;
    }

    final ForwardDeclaration<ParseResult, PyElementType> typeExpr = ForwardDeclaration.create();

    final FunctionalParser<ParseResult, PyElementType> classType =
      token(IDENTIFIER).then(many(op(".").skipThen(token(IDENTIFIER)))).map(new MakeSimpleType(anchor)).cached().named("class-type");

    final FunctionalParser<ParseResult, PyElementType> tupleType =
      op("(").skipThen(typeExpr).then(many(op(",").skipThen(typeExpr))).thenSkip(op(")")).map(value -> {
        ParseResult result = value.getFirst();
        final List<ParseResult> rest = value.getSecond();
        if (rest.isEmpty()) {
          return result;
        }
        final List<PyType> types = new ArrayList<>();
        types.add(result.getType());
        for (ParseResult r : rest) {
          result = result.merge(r);
          types.add(r.getType());
        }
        return result.withType(PyTupleType.create(anchor, types));
      }).named("tuple-type");

    final FunctionalParser<ParseResult, PyElementType> typeParameter =
      token(PARAMETER).then(maybe(op("<=").skipThen(typeExpr))).map(value -> {
        final Token<PyElementType> token = value.getFirst();
        final String name = token.getText().toString();
        final TextRange range = token.getRange();
        final ParseResult boundResult = value.getSecond();
        if (boundResult != null) {
          final PyGenericType type1 = new PyGenericType(name, boundResult.getType());
          final ParseResult result = new ParseResult(null, type1, range);
          return result.merge(boundResult).withType(type1);
        }
        return new ParseResult(null, new PyGenericType(name, null), range);
      }).named("type-parameter");

    final FunctionalParser<ParseResult, PyElementType> simpleExpr = classType.or(tupleType).or(typeParameter).named("simple-expr");

    final FunctionalParser<ParseResult, PyElementType> paramExpr =
      classType.thenSkip(op("[")).then(typeExpr).then(many(op(",").skipThen(typeExpr))).thenSkip(op("]")).map(value -> {
        final Pair<ParseResult, ParseResult> firstPair = value.getFirst();
        final ParseResult first = firstPair.getFirst();
        final ParseResult second = firstPair.getSecond();
        final List<ParseResult> third = value.getSecond();
        final PyType firstType = first.getType();
        final List<PyType> typesInBrackets = new ArrayList<>();
        typesInBrackets.add(second.getType());
        ParseResult result = first;
        result = result.merge(second);
        for (ParseResult r : third) {
          typesInBrackets.add(r.getType());
          result = result.merge(r);
        }
        final List<PyType> elementTypes = third.isEmpty() ? Collections.singletonList(second.getType()) : typesInBrackets;
        final PsiElement resolved = first.getElement();
        if (resolved != null) {
          final PyType typingType = PyTypingTypeProvider.getType(resolved, elementTypes);
          if (typingType != null) {
            return result.withType(typingType);
          }
        }
        if (firstType instanceof PyClassType) {
          final PyType type1 = new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false, elementTypes);
          return result.withType(type1);
        }
        return EMPTY_RESULT;
      }).or(classType.thenSkip(op("of")).then(simpleExpr).map(value -> {
        final ParseResult firstResult = value.getFirst();
        final ParseResult secondResult = value.getSecond();
        final ParseResult result = firstResult.merge(secondResult);
        final PyType firstType = firstResult.getType();
        final PyType secondType = secondResult.getType();
        if (firstType != null) {
          if (firstType instanceof PyClassType && secondType != null) {
            return result.withType(new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(),
                                                            false,
                                                            Collections.singletonList(secondType)));
          }
          return result.withType(firstType);
        }
        return EMPTY_RESULT;
      })).or(classType.thenSkip(op("from")).then(simpleExpr).thenSkip(op("to")).then(simpleExpr).map(value -> {
        final Pair<ParseResult, ParseResult> firstPair = value.getFirst();
        final ParseResult first = firstPair.getFirst();
        final ParseResult second = firstPair.getSecond();
        final ParseResult third = value.getSecond();
        final PyType firstType = first.getType();
        if (firstType instanceof PyClassType) {
          final List<PyType> elementTypes = Arrays.asList(second.getType(), third.getType());
          final PyCollectionTypeImpl type1 = new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false, elementTypes);
          return first.merge(second).merge(third).withType(type1);
        }
        return EMPTY_RESULT;
      })).named("param-expr");

    final FunctionalParser<ParseResult, PyElementType> callableExpr =
      op("(").skipThen(maybe(typeExpr.then(many(op(",").skipThen(typeExpr))))).thenSkip(op(")")).thenSkip(op("->")).then(typeExpr)
             .map(value -> {
               final List<PyCallableParameter> parameters = new ArrayList<>();
               final ParseResult returnResult = value.getSecond();
               ParseResult result;
               final Pair<ParseResult, List<ParseResult>> firstPair = value.getFirst();
               if (firstPair != null) {
                 final ParseResult first = firstPair.getFirst();
                 final List<ParseResult> second = firstPair.getSecond();
                 result = first;
                 parameters.add(new PyCallableParameterImpl(null, first.getType()));
                 for (ParseResult r : second) {
                   result = result.merge(r);
                   parameters.add(new PyCallableParameterImpl(null, r.getType()));
                 }
                 result = result.merge(returnResult);
               }
               else {
                 result = returnResult;
               }
               return result.withType(new PyCallableTypeImpl(parameters, returnResult.getType()));
             }).named("callable-expr");

    final FunctionalParser<ParseResult, PyElementType> singleExpr = paramExpr.or(callableExpr).or(simpleExpr).named("single-expr");

    final FunctionalParser<ParseResult, PyElementType> unionExpr =
      singleExpr.then(many(op("or").or(op("|")).skipThen(singleExpr))).map(value -> {
        final ParseResult first = value.getFirst();
        final List<ParseResult> rest = value.getSecond();
        if (rest.isEmpty()) {
          return first;
        }
        final List<PyType> types = new ArrayList<>();
        types.add(first.getType());
        ParseResult result = first;
        for (ParseResult r : rest) {
          types.add(r.getType());
          result = result.merge(r);
        }
        return result.withType(PyUnionType.union(types));
      }).named("union-expr");

    typeExpr.define(unionExpr).named("type-expr");

    final FunctionalParser<ParseResult, PyElementType> typeFile = typeExpr.endOfInput().named("type-file");

    try {
      return typeFile.parse(tokenize(type));
    }
    catch (ParserException e) {
      return EMPTY_RESULT;
    }
  }

  @Nonnull
  public static ParseResult parsePep484FunctionTypeComment(@Nonnull PsiElement anchor, @Nonnull String text) {
    final ForwardDeclaration<ParseResult, PyElementType> typeExpr = ForwardDeclaration.create();

    final Function<Pair<ParseResult, List<ParseResult>>, ParseResult> toParamTypeList = pair -> {
      if (pair != null) {
        final ParseResult first = pair.getFirst();
        final List<ParseResult> second = pair.getSecond();
        final List<PyType> itemTypes = new ArrayList<>();
        ParseResult result = first;
        itemTypes.add(result.getType());
        for (ParseResult r : second) {
          result = result.merge(r);
          itemTypes.add(r.getType());
        }
        return result.withType(ParameterListType.fromParameterTypes(itemTypes));
      }
      return EMPTY_RESULT.withType(new ParameterListType(Collections.emptyList()));
    };

    final FunctionalParser<ParseResult, PyElementType> ellipsis =
      op("...").map(token -> EMPTY_RESULT.withType(EllipsisType.INSTANCE)).named("ellipsis");

    final FunctionalParser<ParseResult, PyElementType> classType =
      token(IDENTIFIER).then(many(op(".").skipThen(token(IDENTIFIER)))).map(new MakeSimpleType(anchor)).cached().named("class-type");

    final FunctionalParser<ParseResult, PyElementType> typeParamList =
      op("[").skipThen(maybe(typeExpr.then(many(op(",").skipThen(typeExpr))))).thenSkip(op("]")).map(toParamTypeList).named
        ("type-param-list");

    final FunctionalParser<ParseResult, PyElementType> typeParam = typeExpr.or(typeParamList).or(ellipsis).named("type-param");

    final FunctionalParser<ParseResult, PyElementType> genericType =
      classType.thenSkip(op("[")).then(typeParam).then(many(op(",").skipThen(typeParam))).thenSkip(op("]")).map(value -> {
        final Pair<ParseResult, ParseResult> firstPair = value.getFirst();
        final ParseResult first = firstPair.getFirst();
        final ParseResult second = firstPair.getSecond();
        final List<ParseResult> third = value.getSecond();
        final List<PyType> typesInBrackets = new ArrayList<>();
        typesInBrackets.add(second.getType());
        ParseResult result = first;
        result = result.merge(second);
        for (ParseResult r : third) {
          typesInBrackets.add(r.getType());
          result = result.merge(r);
        }
        final List<PyType> elementTypes = third.isEmpty() ? Collections.singletonList(second.getType()) : typesInBrackets;
        final PsiElement resolved = first.getElement();
        if (resolved != null) {
          final PyType typingType = PyTypingTypeProvider.getType(resolved, elementTypes);
          if (typingType != null) {
            return result.withType(typingType);
          }
        }
        return EMPTY_RESULT;
      }).named("generic-type");

    typeExpr.define(genericType.or(classType)).named("type-expr");

    final FunctionalParser<ParseResult, PyElementType> paramType = maybe(op("*")).then(maybe(op("*"))).then(typeExpr).map(pair -> {
      final ParseResult paramResult = pair.getSecond();
      final PyType type = paramResult.getType();
      int starCount = 0;
      if (pair.getFirst().getFirst() != null) {
        starCount++;
      }
      if (pair.getFirst().getSecond() != null) {
        starCount++;
      }
      if (starCount == 0) {
        return paramResult;
      }
      else if (starCount == 1) {
        final PyClassType tupleType = PyTupleType.createHomogeneous(anchor, type);
        if (tupleType != null) {
          return paramResult.withType(tupleType);
        }
        return EMPTY_RESULT;
      }
      else if (starCount == 2) {
        final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(anchor);
        final PyClassType dictType = builtinCache.getDictType();
        if (dictType != null) {
          final PyClass pyClass = dictType.getPyClass();
          return paramResult.withType(new PyCollectionTypeImpl(pyClass, false, Arrays.asList(builtinCache.getStrType(), type)));
        }
        return EMPTY_RESULT;
      }
      return EMPTY_RESULT;
    }).named("param-type");

    final FunctionalParser<ParseResult, PyElementType> paramTypes =
      paramType.then(many(op(",").skipThen(paramType))).map(toParamTypeList).named("param-types");

    final FunctionalParser<ParseResult, PyElementType> funcType =
      op("(").skipThen(maybe(paramTypes.or(ellipsis))).thenSkip(op(")")).thenSkip(op("->")).then(typeExpr).map(value -> {
        final ParseResult paramsResult = value.getFirst();
        final ParseResult returnResult = value.getSecond();
        final List<PyCallableParameter> parameters;
        ParseResult result = returnResult;
        if (paramsResult != null) {
          result = result.merge(paramsResult);
          final ParameterListType paramTypesList = as(paramsResult.getType(), ParameterListType.class);
          if (paramTypesList != null) {
            parameters = paramTypesList.getCallableParameters();
          }
          // ellipsis
          else {
            parameters = null;
          }
        }
        else {
          parameters = Collections.emptyList();
          result = returnResult;
        }
        return result.withType(new PyCallableTypeImpl(parameters, returnResult.getType()));
      }).named("func-type");

    final FunctionalParser<ParseResult, PyElementType> typeFile = funcType.endOfInput().named("function-type-comment");

    try {
      return typeFile.parse(tokenize(text));
    }
    catch (ParserException e) {
      return EMPTY_RESULT;
    }
  }

  private static class MakeSimpleType implements Function<Pair<Token<PyElementType>, List<Token<PyElementType>>>, ParseResult> {
    @Nonnull
    private final PsiElement myAnchor;

    public MakeSimpleType(@Nonnull PsiElement anchor) {
      myAnchor = anchor;
    }

    @Nullable
    @Override
    public ParseResult apply(@Nonnull Pair<Token<PyElementType>, List<Token<PyElementType>>> value) {
      final Token<PyElementType> first = value.getFirst();
      final List<Token<PyElementType>> rest = value.getSecond();
      final TextRange firstRange = first.getRange();
      final boolean unqualified = rest.isEmpty();

      if (unqualified) {
        final ParseResult result = parseBuiltinType(first);
        if (result != null) {
          return result;
        }
      }

      final PsiFile file = myAnchor.getContainingFile();
      final List<Token<PyElementType>> tokens = new ArrayList<>();
      tokens.add(first);
      tokens.addAll(rest);

      if (file instanceof PyFile) {
        final PyFile pyFile = (PyFile)file;
        final TypeEvalContext context = TypeEvalContext.codeInsightFallback(file.getProject());
        final Map<TextRange, PyType> types = new HashMap<>();
        final Map<PyType, TextRange> fullRanges = new HashMap<>();
        final Map<PyType, PyImportElement> imports = new HashMap<>();

        PyType type = resolveQualifierType(tokens, pyFile, context, types, fullRanges, imports);
        PsiElement resolved = type != null ? getElement(type) : null;

        if (type != null) {
          final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
          final PyExpression expression = myAnchor instanceof PyExpression ? (PyExpression)myAnchor : null;

          for (Token<PyElementType> token : tokens) {
            final PyType qualifierType = type;
            type = null;
            final List<? extends RatedResolveResult> results =
              qualifierType.resolveMember(token.getText().toString(), expression, AccessDirection.READ, resolveContext);
            if (results != null && !results.isEmpty()) {
              resolved = results.get(0).getElement();
              if (resolved instanceof PyTypedElement) {
                type = context.getType((PyTypedElement)resolved);
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
            return new ParseResult(resolved, type, types, fullRanges, imports);
          }
        }
      }

      return EMPTY_RESULT;
    }

    @Nullable
    private static PsiElement getElement(@Nonnull PyType type) {
      if (type instanceof PyModuleType) {
        return ((PyModuleType)type).getModule();
      }
      else if (type instanceof PyImportedModuleType) {
        return ((PyImportedModuleType)type).getImportedModule();
      }
      else if (type instanceof PyClassType) {
        return ((PyClassType)type).getPyClass();
      }
      else {
        return null;
      }
    }

    @Nullable
    private PyType resolveQualifierType(@Nonnull List<Token<PyElementType>> tokens,
                                        @Nonnull PyFile file,
                                        @Nonnull TypeEvalContext context,
                                        @Nonnull Map<TextRange, PyType> types,
                                        @Nonnull Map<PyType, TextRange> fullRanges,
                                        @Nonnull Map<PyType, PyImportElement> imports) {
      if (tokens.isEmpty()) {
        return null;
      }
      final Token<PyElementType> firstToken = tokens.get(0);
      final String firstText = firstToken.getText().toString();
      final TextRange firstRange = firstToken.getRange();
      final List<RatedResolveResult> resolveResults = file.multiResolveName(firstText);
      if (resolveResults.isEmpty()) {
        return getImplicitlyResolvedType(tokens, context, types, fullRanges, firstRange);
      }
      final List<PyType> members = Lists.newArrayList();
      for (RatedResolveResult result : resolveResults) {
        final PsiElement resolved = result.getElement();
        PyType type = null;
        if (resolved instanceof PyTargetExpression) {
          type = PyTypingTypeProvider.getTypeFromTargetExpression((PyTargetExpression)resolved, context);
        }
        if (type == null && resolved instanceof PyTypedElement) {
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
      return PyUnionType.union(members);
    }

    @Nullable
    private PyType getImplicitlyResolvedType(@Nonnull List<Token<PyElementType>> tokens,
                                             @Nonnull TypeEvalContext context,
                                             @Nonnull Map<TextRange, PyType> types,
                                             @Nonnull Map<PyType, TextRange> fullRanges,
                                             TextRange firstRange) {
      PyType type = null;
      QualifiedName qName = null;
      while (!tokens.isEmpty()) {
        final Token<PyElementType> token = tokens.get(0);
        final String name = token.getText().toString();
        qName = qName != null ? qName.append(name) : QualifiedName.fromComponents(name);
        PsiElement module = new QualifiedNameResolverImpl(qName).fromElement(myAnchor).firstResult();
        if (module == null) {
          break;
        }
        if (module instanceof PsiDirectory) {
          module = PyUtil.getPackageElement((PsiDirectory)module, myAnchor);
        }
        if (module instanceof PyTypedElement) {
          final PyType moduleType = context.getType((PyTypedElement)module);
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

    private static boolean allowResolveToType(@Nonnull PyType type) {
      return type instanceof PyClassLikeType || type instanceof PyModuleType || type instanceof PyImportedModuleType ||
        type instanceof PyGenericType;
    }

    @Nullable
    private ParseResult parseBuiltinType(@Nonnull Token<PyElementType> token) {
      final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(myAnchor);
      final String name = token.getText().toString();
      final TextRange range = token.getRange();

      if (PyNames.UNKNOWN_TYPE.equals(name)) {
        return EMPTY_RESULT;
      }
      else if (PyNames.NONE.equals(name)) {
        return new ParseResult(null, PyNoneType.INSTANCE, range);
      }
      else if ("integer".equals(name) || ("long".equals(name) && LanguageLevel.forElement(myAnchor).isPy3K())) {
        final PyClassType type = builtinCache.getIntType();
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }
      else if ("string".equals(name)) {
        final PyType type = builtinCache.getStringType(LanguageLevel.forElement(myAnchor));
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }
      else if ("bytestring".equals(name)) {
        final PyType type = builtinCache.getByteStringType(LanguageLevel.forElement(myAnchor));
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }
      else if ("bytes".equals(name)) {
        final PyClassType type = builtinCache.getBytesType(LanguageLevel.forElement(myAnchor));
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }
      else if ("unicode".equals(name)) {
        final PyClassType type = builtinCache.getUnicodeType(LanguageLevel.forElement(myAnchor));
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }
      else if ("boolean".equals(name)) {
        final PyClassType type = builtinCache.getBoolType();
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }
      else if ("dictionary".equals(name)) {
        final PyClassType type = builtinCache.getDictType();
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }

      final PyType builtinType = builtinCache.getObjectType(name);
      if (builtinType != null) {
        return new ParseResult(null, builtinType, range);
      }

      return null;
    }
  }

  private static FunctionalParser<Token<PyElementType>, PyElementType> op(@Nullable String text) {
    return token(PyTypeTokenTypes.OP, text);
  }

  private static List<Token<PyElementType>> tokenize(@Nonnull String s) {
    final List<Token<PyElementType>> tokens = new ArrayList<>();
    final _PyTypeLexer lexer = new _PyTypeLexer(new StringReader(s));
    lexer.reset(s, 0, s.length(), lexer.yystate());
    try {
      PyElementType type;
      while ((type = lexer.advance()) != null) {
        if (type == PyTypeTokenTypes.SPACE || type == PyTypeTokenTypes.MARKUP) {
          continue;
        }
        final TextRange range = TextRange.create(lexer.getTokenStart(), lexer.getTokenEnd());
        final Token<PyElementType> token = new Token<>(type, lexer.yytext(), range);
        tokens.add(token);
      }
    }
    catch (IOException e) {
      return Collections.emptyList();
    }
    catch (Error e) {
      return Collections.emptyList();
    }
    return tokens;
  }

  private static abstract class PyTypeAdapter implements PyType {
    @Nullable
    @Override
    public List<? extends RatedResolveResult> resolveMember(@Nonnull String name,
                                                            @Nullable PyExpression location,
                                                            @Nonnull AccessDirection direction,
                                                            @Nonnull PyResolveContext resolveContext) {
      return null;
    }

    @Override
    public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Nullable
    @Override
    public String getName() {
      return null;
    }

    @Override
    public boolean isBuiltin() {
      return false;
    }

    @Override
    public void assertValid(String message) {

    }
  }

  public static class ParameterListType extends PyTypeAdapter {
    private final List<PyCallableParameter> myParams;

    public static ParameterListType fromParameterTypes(@Nonnull Iterable<PyType> types) {
      return new ParameterListType(ContainerUtil.map(types, type -> new PyCallableParameterImpl(null, type)));
    }

    private ParameterListType(@Nonnull List<PyCallableParameter> types) {
      myParams = types;
    }

    @Nonnull
    public List<PyCallableParameter> getCallableParameters() {
      return myParams;
    }

    @Override
    public String toString() {
      return "[" + StringUtil.join(myParams, ",") + "]";
    }
  }

  public static class EllipsisType extends PyTypeAdapter {
    public static final EllipsisType INSTANCE = new EllipsisType();

    private EllipsisType() {
    }

    @Override
    public String toString() {
      return "...";
    }
  }

}
