/*
 * Copyright 2006 Dmitry Jemerov (yole)
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

package ru.yole.pythonid;

import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.formatting.Indent;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.tree.IFileElementType;
import java.io.PrintStream;
import org.jetbrains.annotations.NotNull;
import ru.yole.pythonid.formatter.PyBlock;
import ru.yole.pythonid.psi.PyElement;
import ru.yole.pythonid.psi.PyUtil;
import ru.yole.pythonid.psi.impl.PyFileImpl;
import ru.yole.pythonid.structureView.PyStructureViewModel;

public class PythonLanguageImpl extends AbstractPythonLanguage
{
  private static boolean DUMP_FORMATTING_AST = false;

  public final IFileElementType ELTYPE_FILE = new IFileElementType(this);

  protected PythonLanguageImpl() {
    super("YolePython");
  }

  public FoldingBuilder getFoldingBuilder()
  {
    return new PythonFoldingBuilder(this);
  }

  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile)
  {
    if ((psiFile instanceof PyFileImpl)) {
      return new TreeBasedStructureViewBuilder() {
        public StructureViewModel createStructureViewModel() {
          return new PyStructureViewModel((PyElement)psiFile);
        }
      };
    }
    return super.getStructureViewBuilder(psiFile);
  }

  public FormattingModelBuilder getFormattingModelBuilder()
  {
    return new FormattingModelBuilder() {
      @NotNull
      public FormattingModel createModel(PsiElement element, CodeStyleSettings settings) { if (PythonLanguageImpl.DUMP_FORMATTING_AST) {
          ASTNode fileNode = element.getContainingFile().getNode();
          System.out.println("AST tree for " + element.getContainingFile().getName() + ":");
          printAST(fileNode, 0);
        }
        FormattingModel tmp97_94 = FormattingModelProvider.createFormattingModelForPsiFile(element.getContainingFile(), new PyBlock(PythonLanguageImpl.this, element.getNode(), null, Indent.getNoneIndent(), null, settings), settings); if (tmp97_94 == null) throw new IllegalStateException("@NotNull method must not return null"); return tmp97_94;
      }

      private void printAST(ASTNode node, int indent)
      {
        while (node != null) {
          for (int i = 0; i < indent; i++) {
            System.out.print(" ");
          }
          System.out.println(node.toString() + " " + node.getTextRange().toString());
          printAST(node.getFirstChildNode(), indent + 2);
          node = node.getTreeNext();
        }
      }
    };
  }

  public FileType getFileType() {
    return PyUtil.findPythonFileType();
  }

  public IFileElementType getFileElementType() {
    return this.ELTYPE_FILE;
  }
}