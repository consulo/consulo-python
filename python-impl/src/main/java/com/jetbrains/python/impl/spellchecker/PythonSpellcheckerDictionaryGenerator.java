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

package com.jetbrains.python.impl.spellchecker;

import com.intellij.spellchecker.generator.SpellCheckerDictionaryGenerator;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.spellcheker.tokenizer.splitter.IdentifierTokenSplitter;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import java.util.HashSet;

/**
 * @author yole
 */
public class PythonSpellcheckerDictionaryGenerator extends SpellCheckerDictionaryGenerator {
  public PythonSpellcheckerDictionaryGenerator(final Project project, final String dictOutputFolder) {
    super(project, dictOutputFolder, "python");
  }

  @Override
  protected void processFolder(final HashSet<String> seenNames, PsiManager manager, VirtualFile folder) {
    if (!myExcludedFolders.contains(folder)) {
      final String name = folder.getName();
      IdentifierTokenSplitter.getInstance().split(name, TextRange.allOf(name), textRange -> {
        final String word = textRange.substring(name);
        addSeenWord(seenNames, word, Language.ANY);
      });
    }
    super.processFolder(seenNames, manager, folder);
  }

  @Override
  protected void processFile(PsiFile file, final HashSet<String> seenNames) {
    file.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyFunction(PyFunction node) {
        super.visitPyFunction(node);
        processLeafsNames(node, seenNames);
      }

      @Override
      public void visitPyClass(PyClass node) {
        super.visitPyClass(node);
        processLeafsNames(node, seenNames);
      }

      @Override
      public void visitPyTargetExpression(PyTargetExpression node) {
        super.visitPyTargetExpression(node);
        if (PsiTreeUtil.getParentOfType(node, ScopeOwner.class) instanceof PyFile) {
          processLeafsNames(node, seenNames);
        }
      }
    });
  }
}
