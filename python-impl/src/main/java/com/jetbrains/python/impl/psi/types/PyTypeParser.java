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
      Map<TextRange, PyType> types = new HashMap<>();
      Map<PyType, TextRange> fullRanges = new HashMap<>();
      Map<PyType, PyImportElement> imports = new HashMap<>();
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
  public static PyType getTypeByName(@Nullable PsiElement anchor, @Nonnull String type) {
    return parse(anchor, type).getType();
  }

  /**
   * @param anchor should never be null or null will be returned
   */
  @Nonnull
  public static ParseResult parse(@Nullable PsiElement anchor, @Nonnull String type) {
    PyPsiUtils.assertValid(anchor);
    if (anchor == null) {
      return EMPTY_RESULT;
    }

    ForwardDeclaration<ParseResult, PyElementType> typeExpr = ForwardDeclaration.create();

    FunctionalParser<ParseResult, PyElementType> classType =
      token(IDENTIFIER).then(many(op(".").skipThen(token(IDENTIFIER)))).map(new MakeSimpleType(anchor)).cached().named("class-type");

    FunctionalParser<ParseResult, PyElementType> tupleType =
      op("(").skipThen(typeExpr).then(many(op(",").skipThen(typeExpr))).thenSkip(op(")")).map(value -> {
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
      }).named("tuple-type");

    FunctionalParser<ParseResult, PyElementType> typeParameter =
      token(PARAMETER).then(maybe(op("<=").skipThen(typeExpr))).map(value -> {
        Token<PyElementType> token = value.getFirst();
        String name = token.getText().toString();
        TextRange range = token.getRange();
        ParseResult boundResult = value.getSecond();
        if (boundResult != null) {
          PyGenericType type1 = new PyGenericType(name, boundResult.getType());
          ParseResult result = new ParseResult(null, type1, range);
          return result.merge(boundResult).withType(type1);
        }
        return new ParseResult(null, new PyGenericType(name, null), range);
      }).named("type-parameter");

    FunctionalParser<ParseResult, PyElementType> simpleExpr = classType.or(tupleType).or(typeParameter).named("simple-expr");

    FunctionalParser<ParseResult, PyElementType> paramExpr =
      classType.thenSkip(op("[")).then(typeExpr).then(many(op(",").skipThen(typeExpr))).thenSkip(op("]")).map(value -> {
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
        PsiElement resolved = first.getElement();
        if (resolved != null) {
          PyType typingType = PyTypingTypeProvider.getType(resolved, elementTypes);
          if (typingType != null) {
            return result.withType(typingType);
          }
        }
        if (firstType instanceof PyClassType) {
          PyType type1 = new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false, elementTypes);
          return result.withType(type1);
        }
        return EMPTY_RESULT;
      }).or(classType.thenSkip(op("of")).then(simpleExpr).map(value -> {
        ParseResult firstResult = value.getFirst();
        ParseResult secondResult = value.getSecond();
        ParseResult result = firstResult.merge(secondResult);
        PyType firstType = firstResult.getType();
        PyType secondType = secondResult.getType();
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
        Pair<ParseResult, ParseResult> firstPair = value.getFirst();
        ParseResult first = firstPair.getFirst();
        ParseResult second = firstPair.getSecond();
        ParseResult third = value.getSecond();
        PyType firstType = first.getType();
        if (firstType instanceof PyClassType) {
          List<PyType> elementTypes = Arrays.asList(second.getType(), third.getType());
          PyCollectionTypeImpl type1 = new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false, elementTypes);
          return first.merge(second).merge(third).withType(type1);
        }
        return EMPTY_RESULT;
      })).named("param-expr");

    FunctionalParser<ParseResult, PyElementType> callableExpr =
      op("(").skipThen(maybe(typeExpr.then(many(op(",").skipThen(typeExpr))))).thenSkip(op(")")).thenSkip(op("->")).then(typeExpr)
             .map(value -> {
               List<PyCallableParameter> parameters = new ArrayList<>();
               ParseResult returnResult = value.getSecond();
               ParseResult result;
               Pair<ParseResult, List<ParseResult>> firstPair = value.getFirst();
               if (firstPair != null) {
                 ParseResult first = firstPair.getFirst();
                 List<ParseResult> second = firstPair.getSecond();
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

    FunctionalParser<ParseResult, PyElementType> singleExpr = paramExpr.or(callableExpr).or(simpleExpr).named("single-expr");

    FunctionalParser<ParseResult, PyElementType> unionExpr =
      singleExpr.then(many(op("or").or(op("|")).skipThen(singleExpr))).map(value -> {
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
        return result.withType(PyUnionType.union(types));
      }).named("union-expr");

    typeExpr.define(unionExpr).named("type-expr");

    FunctionalParser<ParseResult, PyElementType> typeFile = typeExpr.endOfInput().named("type-file");

    try {
      return typeFile.parse(tokenize(type));
    }
    catch (ParserException e) {
      return EMPTY_RESULT;
    }
  }

  @Nonnull
  public static ParseResult parsePep484FunctionTypeComment(@Nonnull PsiElement anchor, @Nonnull String text) {
    ForwardDeclaration<ParseResult, PyElementType> typeExpr = ForwardDeclaration.create();

    Function<Pair<ParseResult, List<ParseResult>>, ParseResult> toParamTypeList = pair -> {
      if (pair != null) {
        ParseResult first = pair.getFirst();
        List<ParseResult> second = pair.getSecond();
        List<PyType> itemTypes = new ArrayList<>();
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

    FunctionalParser<ParseResult, PyElementType> ellipsis =
      op("...").map(token -> EMPTY_RESULT.withType(EllipsisType.INSTANCE)).named("ellipsis");

    FunctionalParser<ParseResult, PyElementType> classType =
      token(IDENTIFIER).then(many(op(".").skipThen(token(IDENTIFIER)))).map(new MakeSimpleType(anchor)).cached().named("class-type");

    FunctionalParser<ParseResult, PyElementType> typeParamList =
      op("[").skipThen(maybe(typeExpr.then(many(op(",").skipThen(typeExpr))))).thenSkip(op("]")).map(toParamTypeList).named
        ("type-param-list");

    FunctionalParser<ParseResult, PyElementType> typeParam = typeExpr.or(typeParamList).or(ellipsis).named("type-param");

    FunctionalParser<ParseResult, PyElementType> genericType =
      classType.thenSkip(op("[")).then(typeParam).then(many(op(",").skipThen(typeParam))).thenSkip(op("]")).map(value -> {
        Pair<ParseResult, ParseResult> firstPair = value.getFirst();
        ParseResult first = firstPair.getFirst();
        ParseResult second = firstPair.getSecond();
        List<ParseResult> third = value.getSecond();
        List<PyType> typesInBrackets = new ArrayList<>();
        typesInBrackets.add(second.getType());
        ParseResult result = first;
        result = result.merge(second);
        for (ParseResult r : third) {
          typesInBrackets.add(r.getType());
          result = result.merge(r);
        }
        List<PyType> elementTypes = third.isEmpty() ? Collections.singletonList(second.getType()) : typesInBrackets;
        PsiElement resolved = first.getElement();
        if (resolved != null) {
          PyType typingType = PyTypingTypeProvider.getType(resolved, elementTypes);
          if (typingType != null) {
            return result.withType(typingType);
          }
        }
        return EMPTY_RESULT;
      }).named("generic-type");

    typeExpr.define(genericType.or(classType)).named("type-expr");

    FunctionalParser<ParseResult, PyElementType> paramType = maybe(op("*")).then(maybe(op("*"))).then(typeExpr).map(pair -> {
      ParseResult paramResult = pair.getSecond();
      PyType type = paramResult.getType();
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
        PyClassType tupleType = PyTupleType.createHomogeneous(anchor, type);
        if (tupleType != null) {
          return paramResult.withType(tupleType);
        }
        return EMPTY_RESULT;
      }
      else if (starCount == 2) {
        PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(anchor);
        PyClassType dictType = builtinCache.getDictType();
        if (dictType != null) {
          PyClass pyClass = dictType.getPyClass();
          return paramResult.withType(new PyCollectionTypeImpl(pyClass, false, Arrays.asList(builtinCache.getStrType(), type)));
        }
        return EMPTY_RESULT;
      }
      return EMPTY_RESULT;
    }).named("param-type");

    FunctionalParser<ParseResult, PyElementType> paramTypes =
      paramType.then(many(op(",").skipThen(paramType))).map(toParamTypeList).named("param-types");

    FunctionalParser<ParseResult, PyElementType> funcType =
      op("(").skipThen(maybe(paramTypes.or(ellipsis))).thenSkip(op(")")).thenSkip(op("->")).then(typeExpr).map(value -> {
        ParseResult paramsResult = value.getFirst();
        ParseResult returnResult = value.getSecond();
        List<PyCallableParameter> parameters;
        ParseResult result = returnResult;
        if (paramsResult != null) {
          result = result.merge(paramsResult);
          ParameterListType paramTypesList = as(paramsResult.getType(), ParameterListType.class);
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

    FunctionalParser<ParseResult, PyElementType> typeFile = funcType.endOfInput().named("function-type-comment");

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

      if (file instanceof PyFile) {
        PyFile pyFile = (PyFile)file;
        TypeEvalContext context = TypeEvalContext.codeInsightFallback(file.getProject());
        Map<TextRange, PyType> types = new HashMap<>();
        Map<PyType, TextRange> fullRanges = new HashMap<>();
        Map<PyType, PyImportElement> imports = new HashMap<>();

        PyType type = resolveQualifierType(tokens, pyFile, context, types, fullRanges, imports);
        PsiElement resolved = type != null ? getElement(type) : null;

        if (type != null) {
          PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
          PyExpression expression = myAnchor instanceof PyExpression ? (PyExpression)myAnchor : null;

          for (Token<PyElementType> token : tokens) {
            PyType qualifierType = type;
            type = null;
            List<? extends RatedResolveResult> results =
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
      Token<PyElementType> firstToken = tokens.get(0);
      String firstText = firstToken.getText().toString();
      TextRange firstRange = firstToken.getRange();
      List<RatedResolveResult> resolveResults = file.multiResolveName(firstText);
      if (resolveResults.isEmpty()) {
        return getImplicitlyResolvedType(tokens, context, types, fullRanges, firstRange);
      }
      List<PyType> members = Lists.newArrayList();
      for (RatedResolveResult result : resolveResults) {
        PsiElement resolved = result.getElement();
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
        Token<PyElementType> token = tokens.get(0);
        String name = token.getText().toString();
        qName = qName != null ? qName.append(name) : QualifiedName.fromComponents(name);
        PsiElement module = new QualifiedNameResolverImpl(qName).fromElement(myAnchor).firstResult();
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

    private static boolean allowResolveToType(@Nonnull PyType type) {
      return type instanceof PyClassLikeType || type instanceof PyModuleType || type instanceof PyImportedModuleType ||
        type instanceof PyGenericType;
    }

    @Nullable
    private ParseResult parseBuiltinType(@Nonnull Token<PyElementType> token) {
      PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(myAnchor);
      String name = token.getText().toString();
      TextRange range = token.getRange();

      if (PyNames.UNKNOWN_TYPE.equals(name)) {
        return EMPTY_RESULT;
      }
      else if (PyNames.NONE.equals(name)) {
        return new ParseResult(null, PyNoneType.INSTANCE, range);
      }
      else if ("integer".equals(name) || ("long".equals(name) && LanguageLevel.forElement(myAnchor).isPy3K())) {
        PyClassType type = builtinCache.getIntType();
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }
      else if ("string".equals(name)) {
        PyType type = builtinCache.getStringType(LanguageLevel.forElement(myAnchor));
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }
      else if ("bytestring".equals(name)) {
        PyType type = builtinCache.getByteStringType(LanguageLevel.forElement(myAnchor));
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }
      else if ("bytes".equals(name)) {
        PyClassType type = builtinCache.getBytesType(LanguageLevel.forElement(myAnchor));
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }
      else if ("unicode".equals(name)) {
        PyClassType type = builtinCache.getUnicodeType(LanguageLevel.forElement(myAnchor));
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }
      else if ("boolean".equals(name)) {
        PyClassType type = builtinCache.getBoolType();
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }
      else if ("dictionary".equals(name)) {
        PyClassType type = builtinCache.getDictType();
        return type != null ? new ParseResult(null, type, range) : EMPTY_RESULT;
      }

      PyType builtinType = builtinCache.getObjectType(name);
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
