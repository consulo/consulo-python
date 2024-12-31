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

package com.jetbrains.python.rest;

import com.jetbrains.rest.RestFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.EditorHighlighter;
import consulo.colorScheme.EditorColorsScheme;
import consulo.language.editor.highlight.EditorHighlighterProvider;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * User : catherine
 */
@ExtensionImpl
public class RestEditorHighlighterProvider implements EditorHighlighterProvider
{
	@Override
	public EditorHighlighter getEditorHighlighter(@Nullable Project project,
												  @Nonnull FileType fileType,
												  @Nullable VirtualFile virtualFile,
												  @Nonnull EditorColorsScheme colors)
	{
		return new RestEditorHighlighter(colors, project, virtualFile);
	}

	@Nonnull
	@Override
	public FileType getFileType()
	{
		return RestFileType.INSTANCE;
	}
}
