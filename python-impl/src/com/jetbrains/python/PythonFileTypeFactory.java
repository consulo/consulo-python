package com.jetbrains.python;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.jetbrains.pyqt.QtUIFileType;

/**
 * @author yole
 */
public class PythonFileTypeFactory extends FileTypeFactory
{
	@Override
	public void createFileTypes(@NonNls @NotNull final FileTypeConsumer consumer)
	{
		consumer.consume(PythonFileType.INSTANCE, "py;pyw;");
		consumer.consume(QtUIFileType.INSTANCE, "ui");
	}
}