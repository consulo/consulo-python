// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.python.spellchecker;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.python.spellchecker.localize.PySpellcheckerLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.VirtualFile;

/**
 * @author yole
 */
@ActionImpl(id = "PythonGenerateDictionaries", parents = @ActionParentRef(@ActionRef(id = "Internal")))
public class PythonSpellcheckerGenerateDictionariesAction extends AnAction {
    public PythonSpellcheckerGenerateDictionariesAction() {
        super(PySpellcheckerLocalize.actionPythongeneratedictionariesText());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Module module = e.getData(Module.KEY);
        if (module == null) {
            return;
        }
        VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
        if (contentRoots.length == 0) {
            return;
        }
        // TODO consulo: JB resolves the interpreter via EvoPyProject (python.pyproject model), Consulo uses the module SDK
        Sdk sdk = PythonSdkType.findPythonSdk(module);
        if (sdk == null) {
            return;
        }

        PythonSpellcheckerDictionaryGenerator generator = new PythonSpellcheckerDictionaryGenerator(
            module.getProject(),
            contentRoots[0].getPath() + "/dicts"
        );

        VirtualFile[] roots = sdk.getRootProvider().getFiles(BinariesOrderRootType.ID);
        for (VirtualFile root : roots) {
            if (root.getName().equals("Lib")) {
                generator.addFolder("python", root);
                generator.excludeFolder(root.findChild("test"));
                generator.excludeFolder(root.findChild(PyNames.SITE_PACKAGES));
            }
            else if (root.getName().equals(PyNames.SITE_PACKAGES)) {
                VirtualFile djangoRoot = root.findChild("django");
                if (djangoRoot != null) {
                    generator.addFolder("django", djangoRoot);
                }
            }
        }

        generator.generate();
    }
}
