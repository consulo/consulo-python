package com.jetbrains.python.buildout.config;

import org.consulo.python.buildout.module.extension.BuildoutModuleExtension;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.fileTypes.ExactFileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;

/**
 * @author traff
 */
public class BuildoutCfgFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    consumer.consume(BuildoutCfgFileType.INSTANCE, new ExactFileNameMatcher(BuildoutModuleExtension.BUILDOUT_CFG, true));
  }
}