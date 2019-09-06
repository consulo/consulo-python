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

package com.jetbrains.python;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class PyFileTypeDetector implements FileTypeRegistry.FileTypeDetector
{
	@Override
	public FileType detect(@Nonnull VirtualFile file, @Nonnull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText)
	{
		return FileUtil.isHashBangLine(firstCharsIfText, "python") ? PythonFileType.INSTANCE : null;
	}

	@Override
	public int getVersion()
	{
		return 1;
	}
}