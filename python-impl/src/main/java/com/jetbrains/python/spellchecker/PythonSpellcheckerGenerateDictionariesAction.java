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

package com.jetbrains.python.spellchecker;

import com.jetbrains.python.sdk.PythonSdkType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.language.editor.LangDataKeys;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.VirtualFile;

/**
 * @author yole
 */
public class PythonSpellcheckerGenerateDictionariesAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE);
    if (module == null) {
      return;
    }
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (contentRoots.length == 0) {
      return;
    }
    Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk == null) {
      return;
    }

    final PythonSpellcheckerDictionaryGenerator generator = new PythonSpellcheckerDictionaryGenerator(module.getProject(),
                                                                                                      contentRoots[0].getPath() + "/dicts");

    VirtualFile[] roots = sdk.getRootProvider().getFiles(BinariesOrderRootType.getInstance());
    for (VirtualFile root : roots) {
      if (root.getName().equals("Lib")) {
        generator.addFolder("python", root);
        generator.excludeFolder(root.findChild("test"));
        generator.excludeFolder(root.findChild("site-packages"));
      }
      else if (root.getName().equals("site-packages")) {
        VirtualFile djangoRoot = root.findChild("django");
        if (djangoRoot != null) {
          generator.addFolder("django", djangoRoot);
        }
      }
    }

    generator.generate();
  }
}
