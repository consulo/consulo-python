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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.annotations.RequiredReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Processor;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.debugger.PyDebugSupportUtils;
import com.jetbrains.python.debugger.PyLineBreakpointType;
import consulo.xdebugger.breakpoints.XLineBreakpointTypeResolver;

/**
 * @author VISTALL
 * @since 5/8/2016
 */
public class PyLineBreakpointTypeResolver implements XLineBreakpointTypeResolver
{
	@Nullable
	@Override
	@RequiredReadAction
	public XLineBreakpointType<?> resolveBreakpointType(@Nonnull Project project, @Nonnull VirtualFile virtualFile, int line)
	{
		final Ref<PyLineBreakpointType> result = Ref.create();
		final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
		if(document != null)
		{
			if(virtualFile.getFileType() == PythonFileType.INSTANCE)
			{
				XDebuggerUtil.getInstance().iterateLine(project, document, line, new Processor<PsiElement>()
				{
					@Override
					@RequiredReadAction
					public boolean process(PsiElement psiElement)
					{
						if(psiElement instanceof PsiWhiteSpace || psiElement instanceof PsiComment)
						{
							return true;
						}
						if(psiElement.getNode() != null && notStoppableElementType(psiElement.getNode().getElementType()))
						{
							return true;
						}

						// Python debugger seems to be able to stop on pretty much everything
						result.set(PyLineBreakpointType.getInstance());
						return false;
					}
				});

				if(PyDebugSupportUtils.isContinuationLine(document, line - 1))
				{
					result.set(null);
				}
			}
		}

		return result.get();
	}

	private static boolean notStoppableElementType(IElementType elementType)
	{
		return elementType == PyTokenTypes.TRIPLE_QUOTED_STRING ||
				elementType == PyTokenTypes.SINGLE_QUOTED_STRING ||
				elementType == PyTokenTypes.SINGLE_QUOTED_UNICODE ||
				elementType == PyTokenTypes.DOCSTRING;
	}
}
