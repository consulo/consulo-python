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
package com.jetbrains.python.impl.refactoring.rename;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatementBase;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.rename.RenamePsiFileProcessorBase;
import consulo.language.editor.refactoring.rename.UnresolvableCollisionUsageInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.usage.UsageInfo;
import consulo.util.io.FileUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
@ExtensionImpl(id = "pyFile")
public class RenamePyFileProcessor extends RenamePsiFileProcessorBase {
    @Override
    public boolean canProcessElement(@Nonnull PsiElement element) {
        return element instanceof PyFile;
    }

    @Override
    @RequiredReadAction
    public PsiElement substituteElementToRename(PsiElement element, @Nullable Editor editor) {
        PyFile file = (PyFile) element;
        if (PyNames.INIT_DOT_PY.equals(file.getName())) {
            return file.getParent();
        }
        return element;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public Collection<PsiReference> findReferences(PsiElement element) {
        List<PsiReference> results = new ArrayList<>();
        for (PsiReference reference : super.findReferences(element)) {
            if (isNotAliasedInImportElement(reference)) {
                results.add(reference);
            }
        }
        return results;
    }

    @Override
    @RequiredReadAction
    public void findCollisions(
        PsiElement element,
        String newName,
        Map<? extends PsiElement, String> allRenames,
        List<UsageInfo> result
    ) {
        final String newFileName = FileUtil.getNameWithoutExtension(newName);
        if (!PyNames.isIdentifier(newFileName)) {
            List<UsageInfo> usages = new ArrayList<>(result);
            for (UsageInfo usageInfo : usages) {
                final PyImportStatementBase importStatement =
                    PsiTreeUtil.getParentOfType(usageInfo.getElement(), PyImportStatementBase.class);
                if (importStatement != null) {
                    result.add(new UnresolvableCollisionUsageInfo(importStatement, element) {
                        @Override
                        public LocalizeValue getDescription() {
                            return LocalizeValue.localizeTODO(
                                "The name '" + newFileName + "' is not a valid Python identifier. Cannot update import statement in '" +
                                    importStatement.getContainingFile().getName() + "'"
                            );
                        }
                    });
                }
            }
        }
    }

    @RequiredReadAction
    private static boolean isNotAliasedInImportElement(@Nonnull PsiReference reference) {
        if (reference instanceof PsiPolyVariantReference polyRef) {
            for (ResolveResult result : polyRef.multiResolve(false)) {
                if (result.getElement() instanceof PyImportElement importElem && importElem.getAsName() != null) {
                    return false;
                }
            }
        }
        return true;
    }
}
