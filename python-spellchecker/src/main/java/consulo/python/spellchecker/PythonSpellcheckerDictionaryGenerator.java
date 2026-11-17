// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.python.spellchecker;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.editor.refactoring.NamesValidator;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.spellcheker.tokenizer.splitter.IdentifierTokenSplitter;
import consulo.language.spellcheker.tokenizer.splitter.SplitContext;
import consulo.language.spellchecker.editor.dictionary.SpellCheckerDictionaryGenerator;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import java.util.Set;

/**
 * @author yole
 */
public class PythonSpellcheckerDictionaryGenerator extends SpellCheckerDictionaryGenerator {
    public PythonSpellcheckerDictionaryGenerator(Project project, String dictOutputFolder) {
        super(project, dictOutputFolder, "python");
    }

    @Override
    protected void processFolder(Set<String> seenNames, PsiManager manager, VirtualFile folder) {
        if (!myExcludedFolders.contains(folder)) {
            String name = folder.getName();
            IdentifierTokenSplitter.getInstance().split(
                SplitContext.of(
                    name,
                    textRange -> {
                        String word = textRange.substring(name);
                        addSeenWord(seenNames, word, Language.ANY);
                    }
                ),
                TextRange.allOf(name)
            );
        }
        super.processFolder(seenNames, manager, folder);
    }

    @Override
    protected void processFile(PsiFile file, Set<String> seenNames) {
        file.accept(new PyRecursiveElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitPyFunction(PyFunction node) {
                super.visitPyFunction(node);
                processLeafsNames(node, seenNames);
            }

            @Override
            @RequiredReadAction
            public void visitPyClass(PyClass node) {
                super.visitPyClass(node);
                processLeafsNames(node, seenNames);
            }

            @Override
            @RequiredReadAction
            public void visitPyTargetExpression(PyTargetExpression node) {
                super.visitPyTargetExpression(node);
                if (PsiTreeUtil.getParentOfType(node, ScopeOwner.class) instanceof PyFile) {
                    processLeafsNames(node, seenNames);
                }
            }
        });
    }

    @Override
    protected boolean isKeyword(String word, Language language) {
        NamesValidator namesValidator = NamesValidator.forLanguage(language);
        return namesValidator != null && namesValidator.isKeyword(word, myProject);
    }
}
