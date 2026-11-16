// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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
package com.jetbrains.python.impl;

import com.jetbrains.python.PyElementTypesFacade;
import com.jetbrains.python.impl.psi.impl.PyArgumentListImpl;
import com.jetbrains.python.impl.psi.impl.PyAsPatternImpl;
import com.jetbrains.python.impl.psi.impl.PyAssertStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyAssignmentExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyAssignmentStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyAugAssignmentStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyBinaryExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyBoolLiteralExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyBreakStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyCapturePatternImpl;
import com.jetbrains.python.impl.psi.impl.PyCaseClauseImpl;
import com.jetbrains.python.impl.psi.impl.PyClassPatternImpl;
import com.jetbrains.python.impl.psi.impl.PyConditionalExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyContinueStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyDecoratorImpl;
import com.jetbrains.python.impl.psi.impl.PyDelStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyDictCompExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyDictLiteralExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyDoubleStarExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyDoubleStarPatternImpl;
import com.jetbrains.python.impl.psi.impl.PyEllipsisLiteralExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyElsePartImpl;
import com.jetbrains.python.impl.psi.impl.PyEmptyExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyExecStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyExpressionStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyFStringFragmentFormatPartImpl;
import com.jetbrains.python.impl.psi.impl.PyFStringFragmentImpl;
import com.jetbrains.python.impl.psi.impl.PyFinallyPartImpl;
import com.jetbrains.python.impl.psi.impl.PyForPartImpl;
import com.jetbrains.python.impl.psi.impl.PyForStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyFormattedStringElementImpl;
import com.jetbrains.python.impl.psi.impl.PyGeneratorExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyGlobalStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyGroupPatternImpl;
import com.jetbrains.python.impl.psi.impl.PyIfPartElifImpl;
import com.jetbrains.python.impl.psi.impl.PyIfPartIfImpl;
import com.jetbrains.python.impl.psi.impl.PyIfStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyKeyValueExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyKeyValuePatternImpl;
import com.jetbrains.python.impl.psi.impl.PyKeywordArgumentImpl;
import com.jetbrains.python.impl.psi.impl.PyKeywordPatternImpl;
import com.jetbrains.python.impl.psi.impl.PyLambdaExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyListCompExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyListLiteralExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyLiteralPatternImpl;
import com.jetbrains.python.impl.psi.impl.PyMappingPatternImpl;
import com.jetbrains.python.impl.psi.impl.PyMatchStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyNoneLiteralExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyNonlocalStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyNumericLiteralExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyOrPatternImpl;
import com.jetbrains.python.impl.psi.impl.PyParenthesizedExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyPassStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyPatternArgumentListImpl;
import com.jetbrains.python.impl.psi.impl.PyPrefixExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyPrintStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyPrintTargetImpl;
import com.jetbrains.python.impl.psi.impl.PyRaiseStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyReprExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyReturnStatementImpl;
import com.jetbrains.python.impl.psi.impl.PySequencePatternImpl;
import com.jetbrains.python.impl.psi.impl.PySetCompExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PySetLiteralExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PySingleStarPatternImpl;
import com.jetbrains.python.impl.psi.impl.PySliceItemImpl;
import com.jetbrains.python.impl.psi.impl.PyStarArgumentImpl;
import com.jetbrains.python.impl.psi.impl.PyStarExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyStatementListElementType;
import com.jetbrains.python.impl.psi.impl.PyStringLiteralExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PySubscriptionExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyTryExceptStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyTryPartImpl;
import com.jetbrains.python.impl.psi.impl.PyTupleExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyValuePatternImpl;
import com.jetbrains.python.impl.psi.impl.PyWhilePartImpl;
import com.jetbrains.python.impl.psi.impl.PyWhileStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyWildcardPatternImpl;
import com.jetbrains.python.impl.psi.impl.PyWithItemImpl;
import com.jetbrains.python.impl.psi.impl.PyWithStatementImpl;
import com.jetbrains.python.impl.psi.impl.PyYieldExpressionImpl;
import consulo.annotation.component.ServiceImpl;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IReparseableElementType;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.PsiElement;
import jakarta.inject.Singleton;

import java.util.function.Function;

@ServiceImpl
@Singleton
public class PyElementTypesFacadeImpl extends PyElementTypesFacade {
  @Override
  public IStubElementType<?, ?> getFunctionDeclaration() {
    return PyStubElementTypes.FUNCTION_DECLARATION;
  }

  @Override
  public IStubElementType<?, ?> getClassDeclaration() {
    return PyStubElementTypes.CLASS_DECLARATION;
  }

  @Override
  public IStubElementType<?, ?> getParameterList() {
    return PyStubElementTypes.PARAMETER_LIST;
  }

  @Override
  public IStubElementType<?, ?> getDecoratorList() {
    return PyStubElementTypes.DECORATOR_LIST;
  }

  @Override
  public IStubElementType<?, ?> getNamedParameter() {
    return PyStubElementTypes.NAMED_PARAMETER;
  }

  @Override
  public IStubElementType<?, ?> getTupleParameter() {
    return PyStubElementTypes.TUPLE_PARAMETER;
  }

  @Override
  public IStubElementType<?, ?> getSlashParameter() {
    return PyStubElementTypes.SLASH_PARAMETER;
  }

  @Override
  public IStubElementType<?, ?> getSingleStarParameter() {
    return PyStubElementTypes.SINGLE_STAR_PARAMETER;
  }

  @Override
  public IStubElementType<?, ?> getDecoratorCall() {
    return PyStubElementTypes.DECORATOR_CALL;
  }

  @Override
  public IStubElementType<?, ?> getImportElement() {
    return PyStubElementTypes.IMPORT_ELEMENT;
  }

  @Override
  public IStubElementType<?, ?> getAnnotation() {
    return PyStubElementTypes.ANNOTATION;
  }

  @Override
  public IStubElementType<?, ?> getStarImportElement() {
    return PyStubElementTypes.STAR_IMPORT_ELEMENT;
  }

  @Override
  public IStubElementType<?, ?> getExceptPart() {
    return PyStubElementTypes.EXCEPT_PART;
  }

  @Override
  public IStubElementType<?, ?> getFromImportStatement() {
    return PyStubElementTypes.FROM_IMPORT_STATEMENT;
  }

  @Override
  public IStubElementType<?, ?> getImportStatement() {
    return PyStubElementTypes.IMPORT_STATEMENT;
  }

  @Override
  public IStubElementType<?, ?> getTargetExpression() {
    return PyStubElementTypes.TARGET_EXPRESSION;
  }

  @Override
  public IStubElementType<?, ?> getTypeParameter() {
    return PyStubElementTypes.TYPE_PARAMETER;
  }

  @Override
  public IStubElementType<?, ?> getTypeParameterList() {
    return PyStubElementTypes.TYPE_PARAMETER_LIST;
  }

  @Override
  public IStubElementType<?, ?> getTypeAliasStatement() {
    return PyStubElementTypes.TYPE_ALIAS_STATEMENT;
  }

  @Override
  public IReparseableElementType getStatementList() {
    return new PyStatementListElementType();
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getArgumentListConstructor() {
    return PyArgumentListImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getPrintTargetConstructor() {
    return PyPrintTargetImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getDecoratorConstructor() {
    return PyDecoratorImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getExpressionStatementConstructor() {
    return PyExpressionStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getAssignmentStatementConstructor() {
    return PyAssignmentStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getAugAssignmentStatementConstructor() {
    return PyAugAssignmentStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getAssertStatementConstructor() {
    return PyAssertStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getBreakStatementConstructor() {
    return PyBreakStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getContinueStatementConstructor() {
    return PyContinueStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getDelStatementConstructor() {
    return PyDelStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getExecStatementConstructor() {
    return PyExecStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getForStatementConstructor() {
    return PyForStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getTypeDeclarationStatementConstructor() {
    return PyTypeDeclarationStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getGlobalStatementConstructor() {
    return PyGlobalStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getIfStatementConstructor() {
    return PyIfStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getPassStatementConstructor() {
    return PyPassStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getPrintStatementConstructor() {
    return PyPrintStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getRaiseStatementConstructor() {
    return PyRaiseStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getReturnStatementConstructor() {
    return PyReturnStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getTryExceptStatementConstructor() {
    return PyTryExceptStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getWithStatementConstructor() {
    return PyWithStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getWhileStatementConstructor() {
    return PyWhileStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getNonlocalStatementConstructor() {
    return PyNonlocalStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getWithItemConstructor() {
    return PyWithItemImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getEmptyExpressionConstructor() {
    return PyEmptyExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getReferenceExpressionConstructor() {
    return PyReferenceExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getIntegerLiteralExpressionConstructor() {
    return PyNumericLiteralExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getFloatLiteralExpressionConstructor() {
    return PyNumericLiteralExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getImaginaryLiteralExpressionConstructor() {
    return PyNumericLiteralExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getStringLiteralExpressionConstructor() {
    return PyStringLiteralExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getNoneLiteralExpressionConstructor() {
    return PyNoneLiteralExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getEllipsisLiteralExpressionConstructor() {
    return PyEllipsisLiteralExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getBoolLiteralExpressionConstructor() {
    return PyBoolLiteralExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getParenthesizedExpressionConstructor() {
    return PyParenthesizedExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getSubscriptionExpressionConstructor() {
    return PySubscriptionExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getSliceItemConstructor() {
    return PySliceItemImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getBinaryExpressionConstructor() {
    return PyBinaryExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getPrefixExpressionConstructor() {
    return PyPrefixExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getCallExpressionConstructor() {
    return PyCallExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getListLiteralExpressionConstructor() {
    return PyListLiteralExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getTupleExpressionConstructor() {
    return PyTupleExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getKeywordArgumentExpressionConstructor() {
    return PyKeywordArgumentImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getStarArgumentExpressionConstructor() {
    return PyStarArgumentImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getLambdaExpressionConstructor() {
    return PyLambdaExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getListCompExpressionConstructor() {
    return PyListCompExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getDictLiteralExpressionConstructor() {
    return PyDictLiteralExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getKeyValueExpressionConstructor() {
    return PyKeyValueExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getReprExpressionConstructor() {
    return PyReprExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getGeneratorExpressionConstructor() {
    return PyGeneratorExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getConditionalExpressionConstructor() {
    return PyConditionalExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getYieldExpressionConstructor() {
    return PyYieldExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getStarExpressionConstructor() {
    return PyStarExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getDoubleStarExpressionConstructor() {
    return PyDoubleStarExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getAssignmentExpressionConstructor() {
    return PyAssignmentExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getSetLiteralExpressionConstructor() {
    return PySetLiteralExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getSetCompExpressionConstructor() {
    return PySetCompExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getDictCompExpressionConstructor() {
    return PyDictCompExpressionImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getIfPartIfConstructor() {
    return PyIfPartIfImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getIfPartElifConstructor() {
    return PyIfPartElifImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getForPartConstructor() {
    return PyForPartImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getWhilePartConstructor() {
    return PyWhilePartImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getTryPartConstructor() {
    return PyTryPartImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getFinallyPartConstructor() {
    return PyFinallyPartImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getElsePartConstructor() {
    return PyElsePartImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getFStringNodeConstructor() {
    return PyFormattedStringElementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getFStringFragmentConstructor() {
    return PyFStringFragmentImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getFStringFragmentFormatPartConstructor() {
    return PyFStringFragmentFormatPartImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getMatchStatementConstructor() {
    return PyMatchStatementImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getCaseClauseConstructor() {
    return PyCaseClauseImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getLiteralPatternConstructor() {
    return PyLiteralPatternImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getValuePatternConstructor() {
    return PyValuePatternImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getCapturePatternConstructor() {
    return PyCapturePatternImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getWildcardPatternConstructor() {
    return PyWildcardPatternImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getGroupPatternConstructor() {
    return PyGroupPatternImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getSequencePatternConstructor() {
    return PySequencePatternImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getSingleStarPatternConstructor() {
    return PySingleStarPatternImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getDoubleStarPatternConstructor() {
    return PyDoubleStarPatternImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getMappingPatternConstructor() {
    return PyMappingPatternImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getKeyValuePatternConstructor() {
    return PyKeyValuePatternImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getClassPatternConstructor() {
    return PyClassPatternImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getPatternArgumentListConstructor() {
    return PyPatternArgumentListImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getKeywordPatternConstructor() {
    return PyKeywordPatternImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getOrPatternConstructor() {
    return PyOrPatternImpl::new;
  }

  @Override
  public Function<? super ASTNode, ? extends PsiElement> getAsPatternConstructor() {
    return PyAsPatternImpl::new;
  }
}
