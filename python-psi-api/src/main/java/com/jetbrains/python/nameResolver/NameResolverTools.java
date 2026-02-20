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
package com.jetbrains.python.nameResolver;

import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiCacheKey;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.Pair;
import consulo.util.lang.function.Condition;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

/**
 * @author Ilya.Kazakevich
 */
public final class NameResolverTools {
  /**
   * Cache: pair [qualified element name, class name (may be null)] by any psi element.
   */
  private static final PsiCacheKey<Pair<String, String>, PyElement> QUALIFIED_AND_CLASS_NAME =
    PsiCacheKey.create(NameResolverTools.class.getName(), new QualifiedAndClassNameObtainer(),
                       PsiModificationTracker.MODIFICATION_COUNT);

  private NameResolverTools() {

  }

  /**
   * For each provided element checks if FQ element name is one of provided names
   *
   * @param elements       element to check
   * @param namesProviders some enum that has one or more names
   * @return true if element's fqn is one of names, provided by provider
   */
  public static boolean isElementWithName(@Nonnull Collection<? extends PyElement> elements,
                                          @Nonnull FQNamesProvider... namesProviders) {
    for (PyElement element : elements) {
      if (isName(element, namesProviders)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if FQ element name is one of provided names. May be <strong>heavy</strong>.
   * It is always better to use less accurate but lighter {@link #isCalleeShortCut(PyCallExpression, FQNamesProvider)}
   *
   * @param element        element to check
   * @param namesProviders some enum that has one or more names
   * @return true if element's fqn is one of names, provided by provider
   */
  public static boolean isName(@Nonnull PyElement element, @Nonnull FQNamesProvider... namesProviders) {
    assert element.isValid();
    Pair<String, String> qualifiedAndClassName = QUALIFIED_AND_CLASS_NAME.getValue(element);
    String qualifiedName = qualifiedAndClassName.first;
    String className = qualifiedAndClassName.second;

    for (FQNamesProvider provider : namesProviders) {
      List<String> names = Arrays.asList(provider.getNames());
      if (qualifiedName != null && names.contains(qualifiedName)) {
        return true;
      }
      if (className != null && provider.isClass() && names.contains(className)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Looks for parent call of certain function
   *
   * @param anchor       element to look parent for
   * @param functionName function to find
   * @return parent call or null if not found
   */
  @Nullable
  public static PyCallExpression findCallExpParent(@Nonnull PsiElement anchor, @Nonnull FQNamesProvider functionName) {
    PsiElement parent = PsiTreeUtil.findFirstParent(anchor, new MyFunctionCondition(functionName));
    if (parent instanceof PyCallExpression) {
      return (PyCallExpression)parent;
    }
    return null;
  }

  /**
   * Same as {@link #isName(PyElement, FQNamesProvider...)} for call expr, but first checks name.
   * Aliases not supported, but much lighter that way
   *
   * @param call     expr
   * @param function names to check
   * @return true if callee is correct
   */
  public static boolean isCalleeShortCut(@Nonnull PyCallExpression call, @Nonnull FQNamesProvider function) {
    PyExpression callee = call.getCallee();
    if (callee == null) {
      return false;
    }

    String callableName = callee.getName();

    Collection<String> possibleNames = new LinkedList<>();
    for (String lastComponent : getLastComponents(function)) {
      possibleNames.add(lastComponent);
    }
    return possibleNames.contains(callableName) && call.isCallee(function);
  }

  @Nonnull
  private static List<String> getLastComponents(@Nonnull FQNamesProvider provider) {
    List<String> result = new ArrayList<>();
    for (String name : provider.getNames()) {
      String component = QualifiedName.fromDottedString(name).getLastComponent();
      if (component != null) {
        result.add(component);
      }
    }
    return result;
  }

  /**
   * Checks if some string contains last component one of name
   *
   * @param text  test to check
   * @param names
   */
  public static boolean isContainsName(@Nonnull String text, @Nonnull FQNamesProvider names) {
    for (String lastComponent : getLastComponents(names)) {
      if (text.contains(lastComponent)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if some file contains last component one of name
   *
   * @param file  file to check
   * @param names
   */
  public static boolean isContainsName(@Nonnull PsiFile file, @Nonnull FQNamesProvider names) {
    return isContainsName(file.getText(), names);
  }

  /**
   * Check if class has parent with some name
   *
   * @param child class to check
   */
  public static boolean isSubclass(@Nonnull PyClass child,
                                   @Nonnull FQNamesProvider parentName,
                                   @Nonnull TypeEvalContext context) {
    for (String nameToCheck : parentName.getNames()) {
      if (child.isSubclass(nameToCheck, context)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Looks for call of some function
   */
  private static class MyFunctionCondition implements Condition<PsiElement> {
    @Nonnull
    private final FQNamesProvider myNameToSearch;

    MyFunctionCondition(@Nonnull FQNamesProvider name) {
      myNameToSearch = name;
    }

    @Override
    public boolean value(PsiElement element) {
      if (element instanceof PyCallExpression) {
        return ((PyCallExpression)element).isCallee(myNameToSearch);
      }
      return false;
    }
  }

  /**
   * Returns pair [qualified name, class name (may be null)] by psi element
   */
  private static class QualifiedAndClassNameObtainer implements Function<PyElement, Pair<String, String>>
  {
    @Override
    @Nonnull
    public Pair<String, String> apply(@Nonnull PyElement param) {
      PyElement elementToCheck = param;

      // Trying to use no implicit context if possible...
      PsiReference reference;
      if (param instanceof PyReferenceOwner) {
        reference = ((PyReferenceOwner)param).getReference(PyResolveContext.noImplicits());
      }
      else {
        reference = param.getReference();
      }

      if (reference != null) {
        PsiElement resolvedElement = reference.resolve();
        if (resolvedElement instanceof PyElement) {
          elementToCheck = (PyElement)resolvedElement;
        }
      }
      String qualifiedName = null;
      if (elementToCheck instanceof PyQualifiedNameOwner) {
        qualifiedName = ((PyQualifiedNameOwner)elementToCheck).getQualifiedName();
      }
      String className = null;
      if (elementToCheck instanceof PyFunction) {
        PyClass aClass = ((PyFunction)elementToCheck).getContainingClass();
        if (aClass != null) {
          className = aClass.getQualifiedName();
        }
      }
      return Pair.create(qualifiedName, className);
    }
  }
}
