// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.python;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;

import java.util.function.Function;

@ServiceAPI(ComponentScope.APPLICATION)
public abstract class PyElementTypesFacade {
  // stubs on backend side
  public abstract IElementType getFunctionDeclaration();

  public abstract IElementType getClassDeclaration();

  public abstract IElementType getParameterList();

  public abstract IElementType getDecoratorList();

  public abstract IElementType getNamedParameter();

  public abstract IElementType getTupleParameter();

  public abstract IElementType getSlashParameter();

  public abstract IElementType getSingleStarParameter();

  public abstract IElementType getDecoratorCall();

  public abstract IElementType getImportElement();

  public abstract IElementType getAnnotation();

  public abstract IElementType getStarImportElement();

  public abstract IElementType getExceptPart();

  public abstract IElementType getFromImportStatement();

  public abstract IElementType getImportStatement();

  public abstract IElementType getTargetExpression();

  public abstract IElementType getTypeParameter();

  public abstract IElementType getTypeParameterList();

  public abstract IElementType getTypeAliasStatement();

  // reparseable elements
  public abstract IElementType getStatementList();

  // constructors for non-stub elements
  public abstract Function<? super ASTNode, ? extends PsiElement> getArgumentListConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getPrintTargetConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getDecoratorConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getExpressionStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getAssignmentStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getAugAssignmentStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getAssertStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getBreakStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getContinueStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getDelStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getExecStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getForStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getTypeDeclarationStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getGlobalStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getIfStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getPassStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getPrintStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getRaiseStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getReturnStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getTryExceptStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getWithStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getWhileStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getNonlocalStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getWithItemConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getEmptyExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getReferenceExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getIntegerLiteralExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getFloatLiteralExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getImaginaryLiteralExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getStringLiteralExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getNoneLiteralExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getEllipsisLiteralExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getBoolLiteralExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getParenthesizedExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getSubscriptionExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getSliceItemConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getBinaryExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getPrefixExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getCallExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getListLiteralExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getTupleExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getKeywordArgumentExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getStarArgumentExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getLambdaExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getListCompExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getDictLiteralExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getKeyValueExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getReprExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getGeneratorExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getConditionalExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getYieldExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getStarExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getDoubleStarExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getAssignmentExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getSetLiteralExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getSetCompExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getDictCompExpressionConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getIfPartIfConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getIfPartElifConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getForPartConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getWhilePartConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getTryPartConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getFinallyPartConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getElsePartConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getFStringNodeConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getFStringFragmentConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getFStringFragmentFormatPartConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getMatchStatementConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getCaseClauseConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getLiteralPatternConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getValuePatternConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getCapturePatternConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getWildcardPatternConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getGroupPatternConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getSequencePatternConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getSingleStarPatternConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getDoubleStarPatternConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getMappingPatternConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getKeyValuePatternConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getClassPatternConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getPatternArgumentListConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getKeywordPatternConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getOrPatternConstructor();

  public abstract Function<? super ASTNode, ? extends PsiElement> getAsPatternConstructor();

  public static PyElementTypesFacade getInstance() {
    return Holder.INSTANCE;
  }

  private static final class Holder {
    // TODO consulo: com.intellij.openapi.progress.Cancellation.forceNonCancellableSectionInClassInitializer missing
    private static final PyElementTypesFacade INSTANCE = Application.get().getInstance(PyElementTypesFacade.class);
  }
}
