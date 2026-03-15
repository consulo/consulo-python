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
package com.jetbrains.python.impl.refactoring.classes.membersManager;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.NotNullPredicate;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.impl.refactoring.classes.ui.PyClassCellRenderer;
import com.jetbrains.python.psi.*;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.util.collection.MultiMap;

import java.util.*;

/**
 * Plugin that moves superclasses from one class to another
 *
 * @author Ilya.Kazakevich
 */
class SuperClassesManager extends MembersManager<PyClass> {

  private static final NoFakeSuperClasses NO_FAKE_SUPER_CLASSES = new NoFakeSuperClasses();

  SuperClassesManager() {
    super(PyClass.class);
  }


  @Override
  protected Collection<PyElement> getDependencies(MultiMap<PyClass, PyElement> usedElements) {
    return Lists.<PyElement>newArrayList(usedElements.keySet());
  }

  protected MultiMap<PyClass, PyElement> getDependencies(PyElement member) {
    return MultiMap.empty();
  }

  @Override
  public boolean hasConflict(PyClass member, PyClass aClass) {
    List<PyExpression> expressionList = getExpressionsBySuperClass(aClass, Collections.singleton(member));
    return !expressionList.isEmpty();
  }

  @Override
  protected List<PyElement> getMembersCouldBeMoved(PyClass pyClass) {
    return Lists.<PyElement>newArrayList(Collections2.filter(Arrays.asList(pyClass.getSuperClasses(null)), NO_FAKE_SUPER_CLASSES));
  }

  @Override
  protected Collection<PyElement> moveMembers(PyClass from,
                                              Collection<PyMemberInfo<PyClass>> members,
                                              PyClass... to) {
    Collection<PyClass> elements = fetchElements(members);
    for (PyClass destClass : to) {
      PyClassRefactoringUtil.addSuperclasses(from.getProject(), destClass, elements.toArray(new PyClass[members.size()]));
    }

    List<PyExpression> expressionsToDelete = getExpressionsBySuperClass(from, elements);
    for (PyExpression expressionToDelete : expressionsToDelete) {
      expressionToDelete.delete();
    }

    return Collections.emptyList(); //Hack: we know that "superclass expression" can't have reference
  }

  /**
   * Returns superclass expressions that are resolved to one or more classes from collection
   *
   * @param from    class to get superclass expressions from
   * @param classes classes to check superclasses against
   * @return collection of expressions that are resolved to one or more class from classes param
   */
  private static List<PyExpression> getExpressionsBySuperClass(PyClass from, Collection<PyClass> classes) {
    List<PyExpression> expressionsToDelete = new ArrayList<>(classes.size());

    for (PyExpression expression : from.getSuperClassExpressions()) {
      // Remove all superclass expressions that point to class from memberinfo
      if (!(expression instanceof PyQualifiedExpression)) {
        continue;
      }
      PyReferenceExpression reference = (PyReferenceExpression)expression;
      for (PyClass element : classes) {
        if (reference.getReference().isReferenceTo(element)) {
          expressionsToDelete.add(expression);
        }
      }
    }
    return expressionsToDelete;
  }

  @Override
  public PyMemberInfo<PyClass> apply(PyClass input) {
    String name = RefactoringBundle.message("member.info.extends.0", PyClassCellRenderer.getClassText(input));
    //TODO: Check for "overrides"
    return new PyMemberInfo<>(input, false, name, false, this, false);
  }

  private static class NoFakeSuperClasses extends NotNullPredicate<PyClass> {
    @Override
    protected boolean applyNotNull(PyClass input) {
      return !PyNames.FAKE_OLD_BASE.equals(input.getName());
    }
  }
}
