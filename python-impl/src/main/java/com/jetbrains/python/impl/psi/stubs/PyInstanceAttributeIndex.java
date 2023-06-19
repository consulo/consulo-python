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

package com.jetbrains.python.impl.psi.stubs;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.stub.StubIndexKey;
import com.jetbrains.python.psi.PyTargetExpression;
import javax.annotation.Nonnull;

import java.util.Collection;

/**
 * @author yole
 */
@ExtensionImpl
public class PyInstanceAttributeIndex extends StringStubIndexExtension<PyTargetExpression> {
  public static final StubIndexKey<String, PyTargetExpression> KEY = StubIndexKey.createIndexKey("Py.instanceAttribute.name");

  @Override
  public int getVersion() {
    return super.getVersion() + 1;
  }

  @Nonnull
  @Override
  public StubIndexKey<String, PyTargetExpression> getKey() {
    return KEY;
  }

  public static Collection<PyTargetExpression> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getInstance().get(KEY, name, project, scope);
  }
}
