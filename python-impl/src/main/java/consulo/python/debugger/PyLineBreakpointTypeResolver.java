/*
 * Copyright 2013-2016 must-be.org
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

package consulo.python.debugger;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.debugger.PyDebugSupportUtils;
import com.jetbrains.python.impl.debugger.PyLineBreakpointType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.breakpoint.XLineBreakpointType;
import consulo.execution.debug.breakpoint.XLineBreakpointTypeResolver;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 5/8/2016
 */
@ExtensionImpl
public class PyLineBreakpointTypeResolver implements XLineBreakpointTypeResolver {
  @Nullable
  @Override
  @RequiredReadAction
  public XLineBreakpointType<?> resolveBreakpointType(@Nonnull Project project, @Nonnull VirtualFile virtualFile, int line) {
    final Ref<PyLineBreakpointType> result = Ref.create();
    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document != null) {
      if (virtualFile.getFileType() == PythonFileType.INSTANCE) {
        XDebuggerUtil.getInstance().iterateLine(project, document, line, new Processor<PsiElement>() {
          @Override
          @RequiredReadAction
          public boolean process(PsiElement psiElement) {
            if (psiElement instanceof PsiWhiteSpace || psiElement instanceof PsiComment) {
              return true;
            }
            if (psiElement.getNode() != null && notStoppableElementType(psiElement.getNode().getElementType())) {
              return true;
            }

            // Python debugger seems to be able to stop on pretty much everything
            result.set(PyLineBreakpointType.getInstance());
            return false;
          }
        });

        if (PyDebugSupportUtils.isContinuationLine(document, line - 1)) {
          result.set(null);
        }
      }
    }

    return result.get();
  }

  @Nonnull
  @Override
  public FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  private static boolean notStoppableElementType(IElementType elementType) {
    return elementType == PyTokenTypes.TRIPLE_QUOTED_STRING ||
      elementType == PyTokenTypes.SINGLE_QUOTED_STRING ||
      elementType == PyTokenTypes.SINGLE_QUOTED_UNICODE ||
      elementType == PyTokenTypes.DOCSTRING;
  }
}
