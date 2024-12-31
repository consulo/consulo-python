/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.psi.types.functionalParser;

import consulo.util.lang.Pair;
import consulo.util.lang.ref.SoftReference;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author vlan
 */
public abstract class FunctionalParserBase<R, T> implements FunctionalParser<R, T> {
  @Nullable
  private String myName = null;

  @Override
  public String toString() {
    return myName != null ? String.format("<%s>", myName) : super.toString();
  }

  @Nonnull
  @Override
  public R parse(@Nonnull List<Token<T>> tokens) throws ParserException {
    return parse(tokens, new State()).getFirst();
  }

  @Nonnull
  public static <T> FunctionalParser<Token<T>, T> token(@Nonnull T type) {
    return token(type, null);
  }

  @Nonnull
  public static <T> FunctionalParser<Token<T>, T> token(@Nonnull final T type, @Nullable final String text) {
    return new TokenParser<T>(type, text);
  }

  @Nonnull
  public static <R, T> FunctionalParser<List<R>, T> many(@Nonnull final FunctionalParser<R, T> parser) {
    return new ManyParser<R, T>(parser);
  }

  @Nonnull
  public static <R, T> FunctionalParser<R, T> maybe(@Nonnull final FunctionalParser<R, T> parser) {
    return parser.or(FunctionalParserBase.<R, T>pure(null));
  }

  @Nonnull
  @Override
  public FunctionalParser<R, T> endOfInput() {
    return this.thenSkip(FunctionalParserBase.<T>finished());
  }

  @Nonnull
  @Override
  public FunctionalParser<R, T> named(@Nonnull String name) {
    myName = name;
    return this;
  }

  @Nonnull
  @Override
  public FunctionalParser<R, T> cached() {
    return new CachedParser<R, T>(this);
  }

  @Nonnull
  @Override
  public <R2> FunctionalParser<Pair<R, R2>, T> then(@Nonnull final FunctionalParser<R2, T> parser) {
    return new ThenParser<R, R2, T>(this, parser);
  }

  @Nonnull
  @Override
  public <R2> FunctionalParser<R2, T> skipThen(@Nonnull final FunctionalParser<R2, T> parser) {
    return second(this.then(parser));
  }

  @Nonnull
  @Override
  public <R2> FunctionalParser<R, T> thenSkip(@Nonnull FunctionalParser<R2, T> parser) {
    return first(this.then(parser));
  }

  @Nonnull
  @Override
  public FunctionalParser<R, T> or(@Nonnull final FunctionalParser<R, T> parser) {
    return new OrParser<R, T>(this, parser);
  }

  @Nonnull
  @Override
  public <R2> FunctionalParser<R2, T> map(@Nonnull final Function<R, R2> f) {
    return new MapParser<R2, T, R>(this, f);
  }

  @Nonnull
  private static <R, R2, T> FunctionalParser<R, T> first(@Nonnull final FunctionalParser<Pair<R, R2>, T> parser) {
    return new FirstParser<R, T, R2>(parser);
  }

  @Nonnull
  private static <R, R2, T> FunctionalParser<R2, T> second(@Nonnull final FunctionalParser<Pair<R, R2>, T> parser) {
    return new SecondParser<R2, T, R>(parser);
  }

  @Nonnull
  private static <T> FunctionalParser<Object, T> finished() {
    return new FinishedParser<T>();
  }

  @Nonnull
  private static <R, T> FunctionalParser<R, T> pure(@Nullable final R value) {
    return new PureParser<R, T>(value);
  }

  private static class TokenParser<T> extends FunctionalParserBase<Token<T>, T> {
    @Nonnull
    private final T myType;
    @Nullable
    private final String myText;

    public TokenParser(@Nonnull T type, @Nullable String text) {
      myType = type;
      myText = text;
    }

    @Nonnull
    @Override
    public Pair<Token<T>, State> parse(@Nonnull List<Token<T>> tokens, @Nonnull State state) throws ParserException {
      final int pos = state.getPos();
      if (pos >= tokens.size()) {
        throw new ParserException("No tokens left", state);
      }
      final Token<T> token = tokens.get(pos);
      if (token.getType().equals(myType) && (myText == null || token.getText().equals(myText))) {
        final int newPos = pos + 1;
        final State newState = new State(state, newPos, Math.max(newPos, state.getMax()));
        return Pair.create(token, newState);
      }
      final String expected = myText != null ? String.format("Token(<%s>, \"%s\")", myType, myText) : String.format("Token(<%s>)", myType);
      throw new ParserException(String.format("Expected %s, found %s", expected, token), state);
    }
  }

  private static class ManyParser<R, T> extends FunctionalParserBase<List<R>, T> {
    @Nonnull
    private final FunctionalParser<R, T> myParser;

    public ManyParser(@Nonnull FunctionalParser<R, T> parser) {
      myParser = parser;
    }

    @Nonnull
    @Override
    public Pair<List<R>, State> parse(@Nonnull List<Token<T>> tokens, @Nonnull State state) throws ParserException {
      final List<R> list = new ArrayList<R>();
      try {
        //noinspection InfiniteLoopStatement
        while (true) {
          final Pair<R, State> result = myParser.parse(tokens, state);
          state = result.getSecond();
          list.add(result.getFirst());
        }
      }
      catch (ParserException e) {
        return Pair.create(list, new State(state, state.getPos(), e.getState().getMax()));
      }
    }
  }

  private static class CachedParser<R, T> extends FunctionalParserBase<R, T> {
    @Nonnull
    private final FunctionalParser<R, T> myParser;
    @Nullable
    private Object myKey;
    @Nonnull
    private Map<Integer, SoftReference<Pair<R, State>>> myCache;

    public CachedParser(@Nonnull FunctionalParser<R, T> parser) {
      myParser = parser;
      myKey = null;
      myCache = new HashMap<Integer, SoftReference<Pair<R, State>>>();
    }

    @Nonnull
    @Override
    public Pair<R, State> parse(@Nonnull List<Token<T>> tokens, @Nonnull State state) throws ParserException {
      if (myKey != state.getKey()) {
        myKey = state.getKey();
        myCache.clear();
      }
      final SoftReference<Pair<R, State>> ref = myCache.get(state.getPos());
      if (ref != null) {
        final Pair<R, State> cached = ref.get();
        if (cached != null) {
          return cached;
        }
      }
      final Pair<R, State> result = myParser.parse(tokens, state);
      myCache.put(state.getPos(), new SoftReference<Pair<R, State>>(result));
      return result;
    }
  }

  private static class OrParser<R, T> extends FunctionalParserBase<R, T> {
    @Nonnull
    private final FunctionalParserBase<R, T> myFirst;
    @Nonnull
    private final FunctionalParser<R, T> mySecond;

    public OrParser(@Nonnull FunctionalParserBase<R, T> first, @Nonnull FunctionalParser<R, T> second) {
      myFirst = first;
      mySecond = second;
    }

    @Nonnull
    @Override
    public Pair<R, State> parse(@Nonnull List<Token<T>> tokens, @Nonnull State state) throws ParserException {
      try {
        return myFirst.parse(tokens, state);
      }
      catch (ParserException e) {
        return mySecond.parse(tokens, new State(state, state.getPos(), e.getState().getMax()));
      }
    }
  }

  private static class FirstParser<R, T, R2> extends FunctionalParserBase<R, T> {
    @Nonnull
    private final FunctionalParser<Pair<R, R2>, T> myParser;

    public FirstParser(@Nonnull FunctionalParser<Pair<R, R2>, T> parser) {
      myParser = parser;
    }

    @Nonnull
    @Override
    public Pair<R, State> parse(@Nonnull List<Token<T>> tokens, @Nonnull State state) throws ParserException {
      final Pair<Pair<R, R2>, State> result = myParser.parse(tokens, state);
      return Pair.create(result.getFirst().getFirst(), result.getSecond());
    }
  }

  private static class SecondParser<R2, T, R> extends FunctionalParserBase<R2, T> {
    @Nonnull
    private final FunctionalParser<Pair<R, R2>, T> myParser;

    public SecondParser(@Nonnull FunctionalParser<Pair<R, R2>, T> parser) {
      myParser = parser;
    }

    @Nonnull
    @Override
    public Pair<R2, State> parse(@Nonnull List<Token<T>> tokens, @Nonnull State state) throws ParserException {
      final Pair<Pair<R, R2>, State> result = myParser.parse(tokens, state);
      return Pair.create(result.getFirst().getSecond(), result.getSecond());
    }
  }

  private static class FinishedParser<T> extends FunctionalParserBase<Object, T> {
    @Nonnull
    @Override
    public Pair<Object, State> parse(@Nonnull List<Token<T>> tokens, @Nonnull State state) throws ParserException {
      final int pos = state.getPos();
      if (pos >= tokens.size()) {
        return Pair.create(null, state);
      }
      throw new ParserException(String.format("Expected end of input, found %s", tokens.get(pos)), state);
    }
  }

  private static class PureParser<R, T> extends FunctionalParserBase<R, T> {
    @Nullable
    private final R myValue;

    public PureParser(@Nullable R value) {
      myValue = value;
    }

    @Nonnull
    @Override
    public Pair<R, State> parse(@Nonnull List<Token<T>> tokens, @Nonnull State state) throws ParserException {
      return Pair.create(myValue, state);
    }
  }

  private static class ThenParser<R, R2, T> extends FunctionalParserBase<Pair<R, R2>, T> {
    @Nonnull
    private final FunctionalParser<R, T> myFirst;
    @Nonnull
    private final FunctionalParser<R2, T> mySecond;

    public ThenParser(@Nonnull FunctionalParser<R, T> first, @Nonnull FunctionalParser<R2, T> second) {
      myFirst = first;
      mySecond = second;
    }

    @Nonnull
    @Override
    public Pair<Pair<R, R2>, State> parse(@Nonnull List<Token<T>> tokens, @Nonnull State state) throws ParserException {
      final Pair<R, State> result1 = myFirst.parse(tokens, state);
      final Pair<R2, State> result2 = mySecond.parse(tokens, result1.getSecond());
      return Pair.create(Pair.create(result1.getFirst(), result2.getFirst()), result2.getSecond());
    }
  }

  private static class MapParser<R2, T, R> extends FunctionalParserBase<R2, T> {
    @Nonnull
    private final FunctionalParserBase<R, T> myParser;
    @Nonnull
    private final Function<R, R2> myFunction;

    public MapParser(@Nonnull FunctionalParserBase<R, T> parser, @Nonnull Function<R, R2> function) {
      myParser = parser;
      myFunction = function;
    }

    @Nonnull
    @Override
    public Pair<R2, State> parse(@Nonnull List<Token<T>> tokens, @Nonnull State state) throws ParserException {
      final Pair<R, State> result = myParser.parse(tokens, state);
      return Pair.create(myFunction.apply(result.getFirst()), result.getSecond());
    }
  }
}
