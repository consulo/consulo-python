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
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.impl.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.SystemInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl
public class PyStdlibModuleMembersProvider extends PyModuleMembersProvider {
  @Override
  protected Collection<PyCustomMember> getMembersByQName(PyFile module, String qName) {
    if (qName.equals("os")) {
      final List<PyCustomMember> results = new ArrayList<>();
      PsiElement path = null;
      if (module != null) {
        final String pathModuleName = SystemInfo.isWindows ? "ntpath" : "posixpath";
        path = ResolveImportUtil.resolveModuleInRoots(QualifiedName.fromDottedString(pathModuleName), module);
      }
      results.add(new PyCustomMember("path", path));
      return results;
    }
    return Collections.emptyList();
  }
}
