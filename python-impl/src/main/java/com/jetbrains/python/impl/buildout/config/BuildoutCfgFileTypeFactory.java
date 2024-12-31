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

package com.jetbrains.python.impl.buildout.config;

import consulo.annotation.component.ExtensionImpl;
import consulo.python.buildout.module.extension.BuildoutModuleExtension;
import consulo.virtualFileSystem.fileType.FileNameMatcherFactory;
import consulo.virtualFileSystem.fileType.FileTypeConsumer;
import consulo.virtualFileSystem.fileType.FileTypeFactory;
import jakarta.inject.Inject;

import jakarta.annotation.Nonnull;

/**
 * @author traff
 */
@ExtensionImpl
public class BuildoutCfgFileTypeFactory extends FileTypeFactory
{
	private final FileNameMatcherFactory myFileNameMatcherFactory;

	@Inject
	public BuildoutCfgFileTypeFactory(FileNameMatcherFactory fileNameMatcherFactory)
	{
		myFileNameMatcherFactory = fileNameMatcherFactory;
	}

	@Override
	public void createFileTypes(final @Nonnull FileTypeConsumer consumer)
	{
		consumer.consume(BuildoutCfgFileType.INSTANCE, myFileNameMatcherFactory.createExactFileNameMatcher(BuildoutModuleExtension.BUILDOUT_CFG, true));
	}
}