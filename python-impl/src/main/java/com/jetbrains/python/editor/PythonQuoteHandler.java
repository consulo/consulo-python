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

package com.jetbrains.python.editor;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.action.FileQuoteHandler;
import consulo.virtualFileSystem.fileType.FileType;

import javax.annotation.Nonnull;

/**
 * @author traff
 */
@ExtensionImpl
public class PythonQuoteHandler extends BaseQuoteHandler implements FileQuoteHandler
{
	public PythonQuoteHandler()
	{
		super(PyTokenTypes.STRING_NODES, new char[]{
				'}',
				']',
				')',
				',',
				':',
				';',
				' ',
				'\t',
				'\n'
		});
	}

	@Nonnull
	@Override
	public FileType getFileType()
	{
		return PythonFileType.INSTANCE;
	}
}
