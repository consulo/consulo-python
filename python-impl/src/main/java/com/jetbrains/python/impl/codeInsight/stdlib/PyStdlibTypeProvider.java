/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.impl.stubs.PyNamedTupleStubImpl;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyNamedTupleStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * @author yole
 */
@ExtensionImpl
public class PyStdlibTypeProvider extends PyTypeProviderBase {
  private static final Set<String> OPEN_FUNCTIONS = ImmutableSet.of("__builtin__.open", "io.open", "os.fdopen", "pathlib.Path.open");

  private static final String PY2K_FILE_TYPE = "file";
  private static final String PY3K_BINARY_FILE_TYPE = "io.FileIO[bytes]";
  private static final String PY3K_TEXT_FILE_TYPE = "io.TextIOWrapper[unicode]";

  @Nullable
  public static PyStdlibTypeProvider getInstance() {
    return PyTypeProvider.EP_NAME.findExtensionOrFail(PyStdlibTypeProvider.class);
  }

  @Override
  @RequiredReadAction
  public PyType getReferenceType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
    PyType type = getBaseStringType(referenceTarget);
    if (type != null) {
      return type;
    }
    type = getNamedTupleType(referenceTarget, context, anchor);
    if (type != null) {
      return type;
    }
    type = getEnumType(referenceTarget, context, anchor);
    if (type != null) {
      return type;
    }
    return null;
  }

  @Nullable
  private static PyType getBaseStringType(PsiElement referenceTarget) {
    PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(referenceTarget);
    if (referenceTarget instanceof PyElement element
        && builtinCache.isBuiltin(referenceTarget)
        && "basestring".equals(element.getName())) {
      return builtinCache.getStringType(LanguageLevel.forElement(referenceTarget));
    }
    return null;
  }

  @Nullable
  @RequiredReadAction
  private static PyType getEnumType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PyTargetExpression target
      && ScopeUtil.getScopeOwner(target) instanceof PyClass cls) {
      for (PyClassLikeType type : cls.getAncestorTypes(context)) {
        if (type != null
          && "enum.Enum".equals(type.getClassQName())
          && context.getType(cls) instanceof PyClassType classType) {
          return classType.toInstance();
        }
      }
    }
    if (referenceTarget instanceof PyQualifiedNameOwner qualifiedNameOwner) {
      String name = qualifiedNameOwner.getQualifiedName();
      if ("enum.Enum.name".equals(name)) {
        return PyBuiltinCache.getInstance(referenceTarget).getStrType();
      }
      else if ("enum.Enum.value".equals(name) && anchor instanceof PyReferenceExpression anchorExpr && context.maySwitchToAST(anchor)) {
        if (anchorExpr.getQualifier() instanceof PyReferenceExpression qualifierExpr
          && qualifierExpr.getReference().resolve() instanceof PyTargetExpression qualifierTarget
          && context.maySwitchToAST(qualifierTarget)) {
          // Requires switching to AST, we cannot use getType(qualifierTarget) here, because its type is overridden by this type provider
          PyExpression value = qualifierTarget.findAssignedValue();
          if (value != null) {
              return context.getType(value);
          }
        }
      }
      else if ("enum.EnumMeta.__members__".equals(name)) {
        return PyTypeParser.getTypeByName(referenceTarget, "dict[str, unknown]");
      }
    }
    return null;
  }

  @Nullable
  @Override
  @RequiredReadAction
  public SimpleReference<PyType> getCallType(PyFunction function, @Nullable PyCallSiteExpression callSite, TypeEvalContext context) {
    if (callSite != null && isListGetItem(function)) {
      PyExpression receiver = PyTypeChecker.getReceiver(callSite, function);
      Map<PyExpression, PyNamedParameter> mapping = PyCallExpressionHelper.mapArguments(callSite, function, context);
      Map<PyGenericType, PyType> substitutions = PyTypeChecker.unifyGenericCall(receiver, mapping, context);
      if (substitutions != null) {
        return analyzeListGetItemCallType(receiver, mapping, substitutions, context);
      }
    }

    String qName = getQualifiedName(function, callSite);
    if (qName != null) {
      if (OPEN_FUNCTIONS.contains(qName) && callSite instanceof PyCallExpression callExpr) {
        PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
        PyCallExpression.PyArgumentsMapping mapping = callExpr.mapArguments(resolveContext);
        if (mapping.getMarkedCallee() != null) {
          return getOpenFunctionType(qName, mapping.getMappedParameters(), callSite);
        }
      }
      else if ("__builtin__.tuple.__init__".equals(qName) && callSite instanceof PyCallExpression callExpr) {
        return getTupleInitializationType(callExpr, context);
      }
      else if ("__builtin__.tuple.__add__".equals(qName) && callSite instanceof PyBinaryExpression binExpr) {
        return getTupleConcatenationResultType(binExpr, context);
      }
      else if ("__builtin__.tuple.__mul__".equals(qName) && callSite instanceof PyBinaryExpression binExpr) {
        return getTupleMultiplicationResultType(binExpr, context);
      }
    }

    return null;
  }

  @RequiredReadAction
  private static boolean isListGetItem(PyFunction function) {
    return PyNames.GETITEM.equals(function.getName())
      && Optional.ofNullable(PyBuiltinCache.getInstance(function).getListType())
        .map(PyClassType::getPyClass)
        .map(cls -> cls.equals(function.getContainingClass()))
        .orElse(false);
  }

  @Nullable
  @RequiredReadAction
  private static SimpleReference<PyType> analyzeListGetItemCallType(
    @Nullable PyExpression receiver,
    Map<PyExpression, PyNamedParameter> parameters,
    Map<PyGenericType, PyType> substitutions,
    TypeEvalContext context
  ) {
    if (parameters.size() != 1 || substitutions.size() > 1) {
      return null;
    }

    PyType firstArgumentType = Optional.ofNullable(parameters.keySet().iterator().next()).map(context::getType).orElse(null);

    if (firstArgumentType == null) {
      return null;
    }

    if (PyABCUtil.isSubtype(firstArgumentType, PyNames.ABC_INTEGRAL, context)) {
      PyType result = substitutions.isEmpty() ? null : substitutions.values().iterator().next();
      return SimpleReference.create(result);
    }

    if (PyNames.SLICE.equals(firstArgumentType.getName()) && firstArgumentType.isBuiltin()) {
      return SimpleReference.create(Optional.ofNullable(receiver)
        .map(context::getType)
        .orElseGet(() -> PyTypeChecker.substitute(PyBuiltinCache.getInstance(receiver).getListType(), substitutions, context)));
    }

    return null;
  }

  @Nullable
  @RequiredReadAction
  private static SimpleReference<PyType> getTupleMultiplicationResultType(PyBinaryExpression multiplication, TypeEvalContext context) {
    PyTupleType leftTupleType = as(context.getType(multiplication.getLeftExpression()), PyTupleType.class);
    if (leftTupleType == null) {
      return null;
    }

    PyExpression rightExpression = multiplication.getRightExpression();
      if (rightExpression instanceof PyReferenceExpression rightRefExpr
          && rightRefExpr.getReference().resolve() instanceof PyTargetExpression rightTarget) {
        rightExpression = rightTarget.findAssignedValue();
      }

    if (rightExpression instanceof PyNumericLiteralExpression numLiteral && numLiteral.isIntegerLiteral()) {
      if (leftTupleType.isHomogeneous()) {
        return SimpleReference.create(leftTupleType);
      }

      int multiplier = numLiteral.getBigIntegerValue().intValue();
      int originalSize = leftTupleType.getElementCount();
      // Heuristic
      if (originalSize * multiplier <= 20) {
        PyType[] elementTypes = new PyType[leftTupleType.getElementCount() * multiplier];
        for (int i = 0; i < multiplier; i++) {
          for (int j = 0; j < originalSize; j++) {
            elementTypes[i * originalSize + j] = leftTupleType.getElementType(j);
          }
        }
        return SimpleReference.create(PyTupleType.create(multiplication, Arrays.asList(elementTypes)));
      }
    }

    return null;
  }

  @Nullable
  private static SimpleReference<PyType> getTupleConcatenationResultType(PyBinaryExpression addition, TypeEvalContext context) {
    if (addition.getRightExpression() != null) {
      PyTupleType leftTupleType = as(context.getType(addition.getLeftExpression()), PyTupleType.class);
      PyTupleType rightTupleType = as(context.getType(addition.getRightExpression()), PyTupleType.class);

      if (leftTupleType != null && rightTupleType != null) {
        if (leftTupleType.isHomogeneous() || rightTupleType.isHomogeneous()) {
          // We may try to find the common type of elements of two homogeneous tuple as an alternative
          return null;
        }

        List<PyType> newElementTypes =
          ContainerUtil.concat(leftTupleType.getElementTypes(context), rightTupleType.getElementTypes(context));
        return SimpleReference.create(PyTupleType.create(addition, newElementTypes));
      }
    }

    return null;
  }

  @Nullable
  private static SimpleReference<PyType> getTupleInitializationType(PyCallExpression call, TypeEvalContext context) {
    PyExpression[] arguments = call.getArguments();

    if (arguments.length != 1) {
      return null;
    }

    PyExpression argument = arguments[0];
    PyType argumentType = context.getType(argument);

    if (argumentType instanceof PyTupleType) {
      return SimpleReference.create(argumentType);
    }
    else if (argumentType instanceof PyCollectionType collectionType) {
        return SimpleReference.create(PyTupleType.createHomogeneous(call, collectionType.getIteratedItemType()));
    }

    return null;
  }

  @Nullable
  @Override
  @RequiredReadAction
  public PyType getContextManagerVariableType(PyClass contextManager, PyExpression withExpression, TypeEvalContext context) {
    if ("contextlib.closing".equals(contextManager.getQualifiedName()) && withExpression instanceof PyCallExpression callExpr) {
      PyExpression closee = callExpr.getArgument(0, PyExpression.class);
      if (closee != null) {
        return context.getType(closee);
      }
    }
    String name = contextManager.getName();
    if ("FileIO".equals(name) || "TextIOWrapper".equals(name) || "IOBase".equals(name) || "_IOBase".equals(name)) {
      return context.getType(withExpression);
    }
    return null;
  }

  @Nullable
  private static PyType getNamedTupleType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
    if (referenceTarget instanceof PyTargetExpression) {
      PyTargetExpression target = (PyTargetExpression)referenceTarget;
      PyTargetExpressionStub stub = target.getStub();

      if (stub != null) {
        return getNamedTupleTypeFromStub(target, stub.getCustomStub(PyNamedTupleStub.class), 1);
      }
      else {
        return getNamedTupleTypeFromAST(target, context, 1);
      }
    }
    else if (referenceTarget instanceof PyFunction && anchor instanceof PyCallExpression) {
      return getNamedTupleTypeFromAST((PyCallExpression)anchor, context, 2);
    }
    return null;
  }

  private static SimpleReference<PyType> getOpenFunctionType(
    String callQName,
    Map<PyExpression, PyNamedParameter> arguments,
    PsiElement anchor
  ) {
    String mode = "r";
    for (Map.Entry<PyExpression, PyNamedParameter> entry : arguments.entrySet()) {
      PyNamedParameter parameter = entry.getValue();
      if ("mode".equals(parameter.getName())) {
        PyExpression argument = entry.getKey();
        if (argument instanceof PyKeywordArgument kwArg) {
          argument = kwArg.getValueExpression();
        }
        if (argument instanceof PyStringLiteralExpression stringLiteral) {
          mode = stringLiteral.getStringValue();
          break;
        }
      }
    }

    if (LanguageLevel.forElement(anchor).isAtLeast(LanguageLevel.PYTHON30) || "io.open".equals(callQName)) {
      if (mode.contains("b")) {
        return SimpleReference.create(PyTypeParser.getTypeByName(anchor, PY3K_BINARY_FILE_TYPE));
      }
      else {
        return SimpleReference.create(PyTypeParser.getTypeByName(anchor, PY3K_TEXT_FILE_TYPE));
      }
    }

    return SimpleReference.create(PyTypeParser.getTypeByName(anchor, PY2K_FILE_TYPE));
  }

  @Nullable
  @RequiredReadAction
  private static String getQualifiedName(PyFunction f, @Nullable PsiElement callSite) {
    PyPsiUtils.assertValid(f);
    String result = f.getName();
    PyClass c = f.getContainingClass();
    VirtualFile vFile = f.getContainingFile().getVirtualFile();
    if (vFile != null) {
      String module = QualifiedNameFinder.findShortestImportableName(callSite != null ? callSite : f, vFile);
      if ("builtins".equals(module)) {
        module = "__builtin__";
      }
      result = String.format("%s.%s%s", module, c != null ? c.getName() + "." : "", result);
      QualifiedName qName = PyStdlibCanonicalPathProvider.restoreStdlibCanonicalPath(QualifiedName.fromDottedString(result));
      if (qName != null) {
        return qName.toString();
      }
    }
    return result;
  }

  @Nullable
  private static PyType getNamedTupleTypeFromStub(PsiElement referenceTarget, @Nullable PyNamedTupleStub stub, int definitionLevel) {
    if (stub == null) {
      return null;
    }

    PyClass tupleClass = PyBuiltinCache.getInstance(referenceTarget).getClass(PyNames.FAKE_NAMEDTUPLE);
    if (tupleClass == null) {
      return null;
    }

    return new PyNamedTupleType(tupleClass, referenceTarget, stub.getName(), stub.getFields(), definitionLevel);
  }

  @Nullable
  private static PyType getNamedTupleTypeFromAST(PyTargetExpression expression, TypeEvalContext context, int definitionLevel) {
    if (context.maySwitchToAST(expression)) {
      return getNamedTupleTypeFromStub(expression, PyNamedTupleStubImpl.create(expression), definitionLevel);
    }

    return null;
  }

  @Nullable
  private static PyType getNamedTupleTypeFromAST(PyCallExpression expression, TypeEvalContext context, int definitionLevel) {
    if (context.maySwitchToAST(expression)) {
      return getNamedTupleTypeFromStub(expression, PyNamedTupleStubImpl.create(expression), definitionLevel);
    }

    return null;
  }
}
