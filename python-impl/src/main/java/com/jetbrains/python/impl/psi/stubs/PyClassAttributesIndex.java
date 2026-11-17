// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import consulo.annotation.component.ExtensionImpl;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.application.progress.ProgressManager;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.stub.StubIndexKey;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class PyClassAttributesIndex extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String, PyClass> KEY = StubIndexKey.createIndexKey("Py.class.attributes");

  @Override
  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }

  public static Collection<PyClass> find(String name, Project project) {
    return StubIndex.getElements(KEY, name, project, GlobalSearchScope.allScope(project), PyClass.class);
  }

  public static Collection<PyTargetExpression> findClassAndInstanceAttributes(
    String name,
    Project project,
    GlobalSearchScope scope) {
    List<PyTargetExpression> ret = new ArrayList<>();
    StubIndex.getInstance().processElements(KEY, name, project, scope, PyClass.class, clazz -> {
      ProgressManager.checkCanceled();
      PyTargetExpression classAttr = clazz.findClassAttribute(name, false, null);
      if (classAttr != null) {
        ret.add(classAttr);
      }

      PyTargetExpression instAttr = clazz.findInstanceAttribute(name, false);
      if (instAttr != null) {
        ret.add(instAttr);
      }

      return true;
    });
    return ret;
  }

  /**
   * Returns all attributes: methods, class and instance fields that are declared directly in the specified class
   * (not taking inheritance into account).
   * <p/>
   * This method <b>must not</b> access the AST because it is being called during stub indexing.
   */
  public static List<String> getAllDeclaredAttributeNames(PyClass pyClass) {
    List<PsiNamedElement> members = ContainerUtil.concat(pyClass.getInstanceAttributes(),
                                                         pyClass.getClassAttributes(),
                                                         Arrays.asList(pyClass.getMethods()));
    return ContainerUtil.mapNotNull(members, expression -> expression.getName());
  }
}
