package com.jetbrains.python.impl.psi.stubs;

import com.jetbrains.python.psi.PyClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.stub.StubIndexKey;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public class PyClassAttributesIndex extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String, PyClass> KEY = StubIndexKey.createIndexKey("Py.class.attributes");

  @Nonnull
  @Override
  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }

  public static Collection<PyClass> find(@Nonnull String name, @Nonnull Project project) {
    return StubIndex.getElements(KEY, name, project, GlobalSearchScope.allScope(project), PyClass.class);
  }

  /**
   * Returns all attributes: methods, class and instance fields that are declared directly in the specified class
   * (not taking inheritance into account).
   * <p>
   * This method <b>must not</b> access the AST because it is being called during stub indexing.
   */
  @Nonnull
  public static List<String> getAllDeclaredAttributeNames(@Nonnull PyClass pyClass) {
    final List<PsiNamedElement> members = ContainerUtil.<PsiNamedElement>concat(pyClass.getInstanceAttributes(),
                                                                                pyClass.getClassAttributes(),
                                                                                Arrays.asList(pyClass.getMethods()));

    return ContainerUtil.mapNotNull(members, expression -> {
      final String attrName = expression.getName();
      return attrName != null ? attrName : null;
    });
  }
}
