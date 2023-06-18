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

package consulo.ironPython.psi.impl;

import com.jetbrains.python.impl.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.impl.codeInsight.imports.PyImportCandidateProvider;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.dotnet.psi.resolve.DotNetShortNameSearcher;
import consulo.ironPython.module.extension.BaseIronPythonModuleExtension;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.stub.IdFilter;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

/**
 * @author yole
 * @author VISTALL
 */
@ExtensionImpl
public class PyDotNetImportCandidateProvider implements PyImportCandidateProvider {
  @RequiredReadAction
	@Override
  public void addImportCandidates(PsiReference reference, String name, final AutoImportQuickFix quickFix) {
    final PsiElement element = reference.getElement();
    final Project project = element.getProject();
    Module module = ModuleUtilCore.findModuleForPsiElement(element);

    if (module == null) {
      return;
    }
    if (ModuleUtilCore.getExtension(module, BaseIronPythonModuleExtension.class) == null) {
      return;
    }

    DotNetShortNameSearcher.getInstance(project)
                           .collectTypes(name, element.getResolveScope(), IdFilter.getProjectIdFilter(project, false), typeDeclaration -> {
                             String presentableParentQName = typeDeclaration.getPresentableParentQName();
                             if (StringUtil.isEmpty(presentableParentQName)) {
                               return true;
                             }
                             final QualifiedName packageQName =
                               QualifiedName.fromDottedString(typeDeclaration.getPresentableQName()).removeLastComponent();
                             quickFix.addImport(typeDeclaration, typeDeclaration.getContainingFile(), packageQName);
                             return true;
                           });
  }
}
