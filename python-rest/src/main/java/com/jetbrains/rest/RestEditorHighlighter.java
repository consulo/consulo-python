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

package com.jetbrains.rest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.codeEditor.EditorColors;
import consulo.colorScheme.EditorColorsScheme;
import consulo.language.editor.highlight.LayerDescriptor;
import consulo.language.editor.highlight.LayeredLexerEditorHighlighter;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.language.file.FileTypeManager;
import consulo.language.plain.PlainTextFileType;
import consulo.language.editor.highlight.SyntaxHighlighterFactory;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import com.jetbrains.python.PythonFileType;

/**
 * Enables python highlighting for Rest files
 * <p/>
 * User : catherine
 */
public class RestEditorHighlighter extends LayeredLexerEditorHighlighter
{

	public RestEditorHighlighter(@Nonnull EditorColorsScheme scheme, @Nullable Project project, @Nullable VirtualFile file)
	{
		super(SyntaxHighlighterFactory.getSyntaxHighlighter(RestLanguage.INSTANCE, project, file), scheme);

		registerLayer(RestTokenTypes.PYTHON_LINE, new LayerDescriptor(SyntaxHighlighterFactory.getSyntaxHighlighter(PythonFileType.INSTANCE, project, file), "", EditorColors.INJECTED_LANGUAGE_FRAGMENT));

		FileType djangoTemplateFileType = FileTypeManager.getInstance().findFileTypeByName("DjangoTemplate");
		if(djangoTemplateFileType != null)
		{
			registerLayer(RestTokenTypes.DJANGO_LINE, new LayerDescriptor(SyntaxHighlighterFactory.getSyntaxHighlighter(djangoTemplateFileType, project, file), "", EditorColors.INJECTED_LANGUAGE_FRAGMENT));
		}


		FileType javascriptFileType = FileTypeManager.getInstance().getStdFileType("JAVASCRIPT");
		if(javascriptFileType != PlainTextFileType.INSTANCE)
		{
			registerLayer(RestTokenTypes.JAVASCRIPT_LINE,
					new LayerDescriptor(SyntaxHighlighterFactory.getSyntaxHighlighter(javascriptFileType, project, file), "", EditorColors.INJECTED_LANGUAGE_FRAGMENT));
		}
	}
}
