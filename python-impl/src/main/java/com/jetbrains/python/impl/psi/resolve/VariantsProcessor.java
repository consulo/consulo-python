// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.QualifiedName;
import consulo.util.io.FileUtil;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public abstract class VariantsProcessor implements PsiScopeProcessor {

  protected final PsiElement myContext;

  protected final @Nullable Predicate<PsiElement> myNodeFilter;

  protected final @Nullable Predicate<String> myNameFilter;

  protected final boolean myPlainNamesOnly; // if true, add insert handlers to known things like functions

  private @Nullable Set<String> myAllowedNames;

  private final Set<String> mySeenNames = new HashSet<>();

  public VariantsProcessor(PsiElement context) {
    this(context, null, null, false);
  }

  public VariantsProcessor(PsiElement context, @Nullable Predicate<PsiElement> nodeFilter, @Nullable Predicate<String> nameFilter) {
    this(context, nodeFilter, nameFilter, false);
  }

  public VariantsProcessor(PsiElement context,
                           @Nullable Predicate<PsiElement> nodeFilter,
                           @Nullable Predicate<String> nameFilter,
                           boolean plainNamesOnly) {
    myContext = context;
    myNodeFilter = nodeFilter;
    myNameFilter = nameFilter;
    myPlainNamesOnly = plainNamesOnly;
  }

  @Override
  public boolean execute(PsiElement element, ResolveState substitutor) {
    if (myNodeFilter != null && !myNodeFilter.test(element)) return true; // skip whatever the filter rejects
    if (element instanceof PsiNamedElement namedElement) {
      final String name = PyUtil.getElementNameWithoutExtension(namedElement);
      if (nameIsAcceptable(name)) {
        addElement(name, namedElement);
      }
    }
    else if (element instanceof PyReferenceExpression referenceExpression) {
      final String name = referenceExpression.getReferencedName();
      if (nameIsAcceptable(name)) {
        addElement(name, referenceExpression);
      }
    }
    else if (element instanceof PyImportedNameDefiner) {
      if (!(element instanceof PyImportElement) || !handleImportElement((PyImportElement)element)) {
        final PyImportedNameDefiner definer = (PyImportedNameDefiner)element;
        for (PyElement expr : definer.iterateNames()) {
          if (expr != null && expr != myContext) { // NOTE: maybe rather have SingleIterables skip nulls outright?
            if (!expr.isValid()) {
              throw new PsiInvalidElementAccessException(expr, "Definer: " + definer);
            }
            final String name = expr instanceof PyFile ? FileUtil.getNameWithoutExtension(((PyFile)expr).getName()) : expr.getName();
            if (nameIsAcceptable(name)) {
              addImportedElement(name, expr);
            }
          }
        }
      }
    }

    return true;
  }

  private boolean handleImportElement(PyImportElement importElement) {
    final QualifiedName qName = importElement.getImportedQName();
    if (qName != null && qName.getComponentCount() == 1) {
      final String name = importElement.getAsName() != null ? importElement.getAsName() : qName.getLastComponent();
      if (nameIsAcceptable(name)) {
        final PsiElement resolved = importElement.resolve();
        if (resolved instanceof PsiNamedElement) {
          addElement(name, resolved);
          return true;
        }
      }
    }
    return false;
  }

  protected abstract void addElement(String name, PsiElement element);

  protected void addImportedElement(String name, PyElement element) {
    addElement(name, element);
  }

  protected void markAsProcessed(String name) {
    mySeenNames.add(name);
  }

  private boolean nameIsAcceptable(@Nullable String name) {
    if (name == null) {
      return false;
    }
    if (mySeenNames.contains(name)) {
      return false;
    }
    if (myNameFilter != null && !myNameFilter.test(name)) {
      return false;
    }
    if (myAllowedNames != null && !myAllowedNames.contains(name)) {
      return false;
    }
    return true;
  }

  public void setAllowedNames(@Nullable Collection<String> allowedNames) {
    if (allowedNames == null) {
      myAllowedNames = null;
    }
    else {
      myAllowedNames = new HashSet<>(allowedNames);
    }
  }
}
