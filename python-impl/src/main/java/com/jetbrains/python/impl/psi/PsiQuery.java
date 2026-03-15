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
package com.jetbrains.python.impl.psi;

import com.jetbrains.python.nameResolver.FQNamesProvider;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.function.Condition;

import org.jspecify.annotations.Nullable;
import java.util.*;
import java.util.stream.Collectors;

// TODO: Propogate typization to all class like in PsiTypedQuery

/**
 * JQuery-like tool that makes PSI navigation easier.
 * You just "drive" your query filtering results, and no need to check result for null.
 *
 * @author Ilya.Kazakevich
 */
public class PsiQuery {
  private static final PsiQuery EMPTY = new PsiQuery();
  private final PsiElement[] myPsiElements;

  /**
   * @param psiElement one or more elements to start
   */
  public PsiQuery(PsiElement... psiElement) {
    myPsiElements = psiElement.clone();
  }


  /**
   * @param psiElements one or more elements to start
   */
  public PsiQuery(List<? extends PsiElement> psiElements) {
    this(psiElements.toArray(new PsiElement[psiElements.size()]));
  }

  /**
   * Filter children by name
   */
  public PsiQuery childrenNamed(String name) {
    return childrenNamed(PsiNamedElement.class, name);
  }

  /**
   * Filter children by name and class
   */
  public PsiQuery childrenNamed(Class<? extends PsiNamedElement> clazz, String name) {
    List<PsiElement> result = new ArrayList<>();
    for (PsiElement element : myPsiElements) {
      for (PsiNamedElement child : PsiTreeUtil.findChildrenOfType(element, clazz)) {
        if (name.equals(child.getName())) {
          result.add(child);
        }
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }


  /**
   * Searches for string literals with specific text
   *
   * @param clazz        string literal class
   * @param expectedText expected text
   * @return query {@link PsiQuery}
   */
  public final PsiQuery childrenStringLiterals(Class<? extends PyStringLiteralExpression> clazz,
                                               String expectedText) {
    List<PsiElement> result = new ArrayList<>();
    for (PyStringLiteralExpression element : getChildrenElements(clazz)) {
      if (element.getStringValue().equals(expectedText)) {
        result.add(element);
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }


  /**
   * TODO: Support types?
   * Filter children by function call
   *
   * @return {@link PsiQuery} backed by {@link com.jetbrains.python.psi.PyCallExpression}
   */
  public PsiQuery childrenCall(FQNamesProvider name) {
    List<PsiElement> result = new ArrayList<>();
    for (PsiElement element : myPsiElements) {
      for (PyCallExpression call : PsiTreeUtil.findChildrenOfType(element, PyCallExpression.class)) {
        if (call.isCallee(name)) {
          result.add(call);
        }
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }

  /**
   * Filter children by class
   */
  public PsiQuery children(Class<? extends PsiElement> clazz) {
    List<PsiElement> result = new ArrayList<>();
    for (PsiElement element : myPsiElements) {
      result.addAll(PsiTreeUtil.findChildrenOfType(element, clazz));
    }

    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }


  /**
   * Filter parents by name
   */
  public PsiQuery parents(String name) {
    throw new RuntimeException("Not implemented");
  }


  /**
   * Filter parents by name and class
   */
  public PsiQuery parents(Class<? extends PsiElement> clazz) {
    List<PsiElement> result = new ArrayList<>();
    for (PsiElement element : myPsiElements) {
      PsiElement parent = PsiTreeUtil.getParentOfType(element, clazz);
      if (parent != null) {
        result.add(parent);
      }
    }
    return new PsiQuery(result);
  }

  /**
   * Get qualifiers of all elements if elements do have any
   */
  public PsiQuery qualifiers() {
    return new PsiQuery(Arrays.stream(myPsiElements)
                              .filter(o -> o instanceof PyQualifiedExpression)
                              .map(o -> ((PyQualifiedExpression)o).getQualifier())
                              .filter(o -> o != null)
                              .collect
                                (Collectors.toList()));
  }


  /**
   * Filter parents by condition
   */
  public PsiQuery parents(Condition<Class<? extends PsiElement>> condition) {
    throw new RuntimeException("Not impl");
  }


  /**
   * Filter parents by class and name
   */
  public PsiQuery parents(Class<? extends PsiElement> clazz, String name) {
    throw new RuntimeException("Not impl");
  }


  /**
   * Filter parents by function call
   */
  public PsiQuery parents(FQNamesProvider name) {
    throw new RuntimeException("Not impl");
  }


  /**
   * Filter siblings by name
   */
  public PsiQuery siblings(String name) {
    return siblings(PsiNamedElement.class, name);
  }


  /**
   * Filter siblings by class returning typed result
   */
  public <T extends PsiElement> PsiTypedQuery<T> siblings(Class<T> clazz) {
    // TODO: Rewrite function, get rid of inner class
    List<T> result = new ArrayList<>();
    for (PsiElement element : myPsiElements) {
      PsiElement parent = element.getParent();
      for (T sibling : PsiTreeUtil.findChildrenOfType(parent, clazz)) {
        if ((!sibling.equals(element))) {
          result.add(sibling);
        }
      }
    }
    return new PsiTypedQuery<>(clazz, result);
  }


  /**
   * Filter siblings by name and class
   */
  public PsiQuery siblings(Class<? extends PsiNamedElement> clazz, String name) {
    List<PsiElement> result = new ArrayList<>();
    for (PsiElement element : myPsiElements) {
      PsiElement parent = element.getParent();
      for (PsiNamedElement namedSibling : PsiTreeUtil.findChildrenOfType(parent, clazz)) {
        if ((!namedSibling.equals(element)) && (name.equals(namedSibling.getName()))) {
          result.add(namedSibling);
        }
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }


  /**
   * Filter siblings by function call name
   */
  public PsiQuery siblings(FQNamesProvider name) {
    List<PsiElement> result = new ArrayList<>();
    for (PsiElement element : myPsiElements) {
      PsiElement parent = element.getParent();
      for (PyCallExpression callSibling : PsiTreeUtil.findChildrenOfType(parent, PyCallExpression.class)) {
        if ((!callSibling.equals(element)) && (callSibling.isCallee(name))) {
          result.add(callSibling);
        }
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }


  /**
   * Get first element from result only
   */
  public PsiQuery first() {
    return (myPsiElements.length > 0) ? new PsiQuery(myPsiElements[0]) : EMPTY;
  }


  /**
   * Get last element from result only
   */
  public PsiQuery last() {
    return (myPsiElements.length > 0) ? new PsiQuery(myPsiElements[myPsiElements.length - 1]) : EMPTY;
  }


  /**
   * Get first element from result only if certain class
   */
  @Nullable
  public <T extends PsiElement> T getFirstElement(Class<T> expectedClass) {
    List<T> elements = getChildrenElements(expectedClass);
    if (!elements.isEmpty()) {
      return elements.get(0);
    }
    return null;
  }


  /**
   * Get last element from result only if certain class
   */
  @Nullable
  public <T extends PsiElement> T getLastElement(Class<T> expectedClass) {
    List<T> elements = getChildrenElements(expectedClass);
    if (!elements.isEmpty()) {
      return elements.get(elements.size() - 1);
    }
    return null;
  }


  /**
   * Get children elements filtered by class
   */
  public <T extends PsiElement> List<T> getChildrenElements(Class<T> expectedClass) {
    List<T> result = new ArrayList<>();
    for (PsiElement element : myPsiElements) {
      T typedElement = PyUtil.as(element, expectedClass);
      if (typedElement != null) {
        result.add(typedElement);
      }
      else {
        T[] children = PsiTreeUtil.getChildrenOfType(element, expectedClass);
        if (children != null) {
          Collections.addAll(result, children);
        }
      }
    }
    return result;
  }


  /**
   * Filter by function call
   */
  public PsiQuery filter(FQNamesProvider name) {
    Set<PsiElement> result = new HashSet<>(Arrays.asList(myPsiElements));
    for (PsiElement element : myPsiElements) {
      PyCallExpression callExpression = PyUtil.as(element, PyCallExpression.class);
      if ((callExpression == null) || (!callExpression.isCallee(name))) {
        result.remove(element);
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }


  /**
   * Filter by element name
   */
  public PsiQuery filter(String name) {
    return filter(PsiNamedElement.class, name);
  }


  /**
   * Filter elements by class
   */
  public <T extends PsiElement> PsiTypedQuery<T> filter(Class<T> clazz) {
    Set<PsiElement> result = new HashSet<>(Arrays.asList(myPsiElements));
    for (PsiElement element : myPsiElements) {
      if (!(clazz.isInstance(element))) {
        result.remove(element);
      }
    }
    // We checked it in runtime
    @SuppressWarnings("unchecked") List<T> toAdd = (List<T>)new ArrayList<>(result);
    return new PsiTypedQuery<>(clazz, toAdd);
  }


  /**
   * Filter elements by class and name
   */
  public PsiQuery filter(Class<? extends PsiNamedElement> clazz, String name) {
    Set<PsiElement> result = new HashSet<>(Arrays.asList(myPsiElements));
    for (PsiElement element : myPsiElements) {
      PsiNamedElement namedElement = PyUtil.as(element, clazz);
      if ((namedElement == null) || (!name.equals(namedElement.getName()))) {
        result.remove(element);
      }
    }
    return new PsiQuery(result.toArray(new PsiElement[result.size()]));
  }

  /**
   * @return is result empty or not
   */
  public boolean isEmpty() {
    return myPsiElements.length == 0;
  }

  /**
   * Typed class that returns elements of certian type
   *
   * @param <T> class type
   */
  public static class PsiTypedQuery<T extends PsiElement> extends PsiQuery {
    private final Class<T> myClass;
    private final List<T> myElements;

    /**
     * @param clazz    type
     * @param elements elements
     */
    private PsiTypedQuery(Class<T> clazz, List<T> elements) {
      super(elements);
      myClass = clazz;
      myElements = elements;
    }

    /**
     * @return First element of certain type
     */
    @Nullable
    public T getFirstElement() {
      return getFirstElement(myClass);
    }

    /**
     * @return Last element of certain type
     */
    @Nullable
    public T getLastElement() {
      return getLastElement(myClass);
    }

    /**
     * @return All elements of certain type
     */
    public List<T> getElements() {
      return Collections.unmodifiableList(myElements);
    }
  }
}
