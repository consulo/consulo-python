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

package com.jetbrains.python.codeInsight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.util.dataholder.Key;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.LightNamedElement;

/**
 * @author yole
 */
public class PyDunderAllReference extends PsiReferenceBase<PyStringLiteralExpression> {
  public PyDunderAllReference(@Nonnull PyStringLiteralExpression element) {
    super(element);
    final List<TextRange> ranges = element.getStringValueTextRanges();
    if (ranges.size() > 0) {
      setRangeInElement(ranges.get(0));
    }
  }

  @Override
  public PsiElement resolve() {
    final PyStringLiteralExpression element = getElement();
    final String name = element.getStringValue();
    PyFile containingFile = (PyFile) element.getContainingFile();
    return containingFile.getElementNamed(name);
  }

  @Nonnull
  @Override
  public Object[] getVariants() {
    final List<LookupElement> result = new ArrayList<LookupElement>();
    PyFile containingFile = (PyFile) getElement().getContainingFile().getOriginalFile();
    final List<String> dunderAll = containingFile.getDunderAll();
    final Set<String> seenNames = new HashSet<String>();
    if (dunderAll != null) {
      seenNames.addAll(dunderAll);
    }
    containingFile.processDeclarations(new PsiScopeProcessor() {
      @Override
      public boolean execute(@Nonnull PsiElement element, ResolveState state) {
        if (element instanceof PsiNamedElement && !(element instanceof LightNamedElement)) {
          final String name = ((PsiNamedElement)element).getName();
          if (name != null && PyUtil.getInitialUnderscores(name) == 0 && !seenNames.contains(name)) {
            seenNames.add(name);
            result.add(LookupElementBuilder.create((PsiNamedElement) element).withIcon(IconDescriptorUpdaters.getIcon(element, 0)));
          }
        }
        else if (element instanceof PyImportElement) {
          final String visibleName = ((PyImportElement)element).getVisibleName();
          if (visibleName != null && !seenNames.contains(visibleName)) {
            seenNames.add(visibleName);
            result.add(LookupElementBuilder.create(element, visibleName));
          }
        }
        return true;
      }

      @Override
      public <T> T getHint(@Nonnull Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(Event event, @Nullable Object associated) {
      }
    }, ResolveState.initial(), null, containingFile);
    return ArrayUtil.toObjectArray(result);
  }
}
