// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.psi.impl.stubs.PyDataclassTransformDecoratorStubType;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.impl.stubs.PyCustomDecoratorStub;
import consulo.index.io.data.DataInputOutputUtil;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents arguments of `typing.dataclass_transform` or `typing_extensions.dataclass_transform` decorators
 * applied to another decorator function or a base class for creating new dataclasses.
 * <p>
 * Omitted arguments are expected to be replaced by their default values by the caller creating a new instance.
 * <p>
 * See https://docs.python.org/3.13/library/typing.html#typing.dataclass_transform.
 */
public final class PyDataclassTransformDecoratorStub implements PyCustomDecoratorStub {
  private final boolean eqDefault;
  private final boolean orderDefault;
  private final boolean kwOnlyDefault;
  private final @Nullable Boolean frozenDefault;
  private final List<QualifiedName> fieldSpecifiers;

  public PyDataclassTransformDecoratorStub(boolean eqDefault,
                                           boolean orderDefault,
                                           boolean kwOnlyDefault,
                                           @Nullable Boolean frozenDefault,
                                           List<QualifiedName> fieldSpecifiers) {
    this.eqDefault = eqDefault;
    this.orderDefault = orderDefault;
    this.kwOnlyDefault = kwOnlyDefault;
    this.frozenDefault = frozenDefault;
    this.fieldSpecifiers = fieldSpecifiers;
  }

  public boolean getEqDefault() {
    return eqDefault;
  }

  public boolean getOrderDefault() {
    return orderDefault;
  }

  public boolean getKwOnlyDefault() {
    return kwOnlyDefault;
  }

  public @Nullable Boolean getFrozenDefault() {
    return frozenDefault;
  }

  public List<QualifiedName> getFieldSpecifiers() {
    return fieldSpecifiers;
  }

  @Override
  public Class<PyDataclassTransformDecoratorStubType> getTypeClass() {
    return PyDataclassTransformDecoratorStubType.class;
  }

  @Override
  public void serialize(StubOutputStream stream) throws IOException {
    stream.writeBoolean(eqDefault);
    stream.writeBoolean(orderDefault);
    stream.writeBoolean(kwOnlyDefault);
    DataInputOutputUtil.writeNullable(stream, frozenDefault, stream::writeBoolean);
    DataInputOutputUtil.writeSeq(stream, fieldSpecifiers, it -> QualifiedName.serialize(it, stream));
  }

  public static PyDataclassTransformDecoratorStub deserialize(StubInputStream stream) throws IOException {
    boolean eqDefault = stream.readBoolean();
    boolean orderDefault = stream.readBoolean();
    boolean kwOnlyDefault = stream.readBoolean();
    Boolean frozenDefault = DataInputOutputUtil.readNullable(stream, stream::readBoolean);
    List<QualifiedName> fieldSpecifiers = DataInputOutputUtil.readSeq(stream, () -> Objects.requireNonNull(QualifiedName.deserialize(stream)));

    return new PyDataclassTransformDecoratorStub(
      eqDefault,
      orderDefault,
      kwOnlyDefault,
      frozenDefault,
      fieldSpecifiers
    );
  }

  public static @Nullable PyDataclassTransformDecoratorStub create(PyDecorator decorator) {
    if (!(decorator.getCallee() instanceof PyReferenceExpression decoratorName)) return null;
    boolean importedAsDataclassTransform = PyResolveUtil.resolveImportedElementQNameLocally(decoratorName)
      .stream()
      .anyMatch(it -> it.toString().contains(PyDataclassNames.DataclassTransform.DATACLASS_TRANSFORM_NAME));
    if (importedAsDataclassTransform) {
      PyExpression fieldSpecifierArgument = PyUtil.peelArgument(decorator.getKeywordArgument("field_specifiers"));
      List<QualifiedName> fieldSpecifiers;
      if (fieldSpecifierArgument instanceof PyTupleExpression fieldSpecifierList) {
        fieldSpecifiers = new ArrayList<>();
        for (PyExpression it : fieldSpecifierList.getElements()) {
          QualifiedName qName = it instanceof PyReferenceExpression referenceExpression ? referenceExpression.asQualifiedName() : null;
          if (qName != null) {
            fieldSpecifiers.add(qName);
          }
        }
      }
      else {
        fieldSpecifiers = Collections.emptyList();
      }
      return new PyDataclassTransformDecoratorStub(
        PyEvaluator.evaluateAsBooleanNoResolve(decorator.getKeywordArgument("eq_default"), true),
        PyEvaluator.evaluateAsBooleanNoResolve(decorator.getKeywordArgument("order_default"), false),
        PyEvaluator.evaluateAsBooleanNoResolve(decorator.getKeywordArgument("kw_only_default"), false),
        PyEvaluator.evaluateAsBooleanNoResolve(decorator.getKeywordArgument("frozen_default")),
        fieldSpecifiers
      );
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PyDataclassTransformDecoratorStub that)) return false;
    return eqDefault == that.eqDefault &&
           orderDefault == that.orderDefault &&
           kwOnlyDefault == that.kwOnlyDefault &&
           Objects.equals(frozenDefault, that.frozenDefault) &&
           fieldSpecifiers.equals(that.fieldSpecifiers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eqDefault, orderDefault, kwOnlyDefault, frozenDefault, fieldSpecifiers);
  }

  @Override
  public String toString() {
    return "PyDataclassTransformDecoratorStub(eqDefault=" + eqDefault +
           ", orderDefault=" + orderDefault +
           ", kwOnlyDefault=" + kwOnlyDefault +
           ", frozenDefault=" + frozenDefault +
           ", fieldSpecifiers=" + fieldSpecifiers + ")";
  }
}
