package com.jetbrains.rest;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonFileType;

/**
 * Enables python highlighting for Rest files
 * <p/>
 * User : catherine
 */
public class RestEditorHighlighter extends LayeredLexerEditorHighlighter
{

	public RestEditorHighlighter(@NotNull EditorColorsScheme scheme, @Nullable Project project, @Nullable VirtualFile file)
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
