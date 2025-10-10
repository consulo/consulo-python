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
package com.jetbrains.python.impl.buildout.config.inspection;

import com.google.common.collect.Lists;
import com.jetbrains.python.impl.buildout.config.BuildoutCfgFileType;
import com.jetbrains.python.impl.buildout.config.psi.impl.BuildoutCfgValueLine;
import com.jetbrains.python.impl.buildout.config.ref.BuildoutPartReference;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiRecursiveElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author traff
 */
@ExtensionImpl
public class BuildoutUnresolvedPartInspection extends LocalInspectionTool {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.buildout();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.buildoutUnresolvedPartInspection();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "BuildoutUnresolvedPartInspection";
    }

    @Nonnull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public ProblemDescriptor[] checkFile(@Nonnull PsiFile file, @Nonnull InspectionManager manager, boolean isOnTheFly) {
        List<ProblemDescriptor> problems = Lists.newArrayList();
        if (file.getFileType().equals(BuildoutCfgFileType.INSTANCE)) {
            Visitor visitor = new Visitor();
            file.accept(visitor);

            for (BuildoutPartReference ref : visitor.getUnresolvedParts()) {
                ProblemDescriptor d = manager.createProblemDescriptor(
                    ref.getElement(),
                    ref.getRangeInElement(),
                    PyLocalize.buildoutUnresolvedPartInspectionMsg().get(),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    false
                );
                problems.add(d);
            }
        }
        return problems.toArray(new ProblemDescriptor[problems.size()]);
    }

    private class Visitor extends PsiRecursiveElementVisitor {
        private final List<BuildoutPartReference> unresolvedParts = Lists.newArrayList();

        @Override
        public void visitElement(PsiElement element) {
            if (element instanceof BuildoutCfgValueLine) {
                PsiReference[] refs = element.getReferences();
                for (PsiReference ref : refs) {
                    if (ref instanceof BuildoutPartReference && ref.resolve() == null) {
                        unresolvedParts.add((BuildoutPartReference) ref);
                    }
                }

            }
            super.visitElement(element);    //To change body of overridden methods use File | Settings | File Templates.
        }

        public List<BuildoutPartReference> getUnresolvedParts() {
            return unresolvedParts;
        }
    }
}
