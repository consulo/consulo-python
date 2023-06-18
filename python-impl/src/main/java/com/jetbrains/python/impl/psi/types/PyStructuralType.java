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

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import consulo.application.AllIcons;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author vlan
 */
public class PyStructuralType implements PyType {
  @Nonnull
  private final Set<String> myAttributes;
  private final boolean myInferredFromUsages;

  public PyStructuralType(@Nonnull Set<String> attributes, boolean inferredFromUsages) {
    myAttributes = attributes;
    myInferredFromUsages = inferredFromUsages;
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@Nonnull String name,
                                                          @Nullable PyExpression location,
                                                          @Nonnull AccessDirection direction,
                                                          @Nonnull PyResolveContext resolveContext) {
    return Collections.emptyList();
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final List<Object> variants = new ArrayList<>();
    for (String attribute : myAttributes) {
      if (!attribute.equals(completionPrefix)) {
        variants.add(LookupElementBuilder.create(attribute).withIcon(AllIcons.Nodes.Field));
      }
    }
    return variants.toArray();
  }

  @Nullable
  @Override
  public String getName() {
    return "{" + StringUtil.join(myAttributes, ", ") + "}";
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }

  @Override
  public String toString() {
    return "PyStructuralType(" + StringUtil.join(myAttributes, ", ") + ")";
  }

  public boolean isInferredFromUsages() {
    return myInferredFromUsages;
  }

  public Set<String> getAttributeNames() {
    return myAttributes;
  }
}
