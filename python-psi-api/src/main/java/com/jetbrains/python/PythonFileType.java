// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.psi.icon.PythonPsiIconGroup;
import consulo.ui.image.Image;
import consulo.util.io.CharSequenceReader;
import consulo.util.io.CharsetToolkit;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PythonFileType extends LanguageFileType {
    private static final Pattern ENCODING_PATTERN = Pattern.compile("coding[:=]\\s*([-\\w.]+)");
    public static final int MAX_CHARSET_ENCODING_LINE = 2;

    public static final PythonFileType INSTANCE = new PythonFileType();

    private PythonFileType() {
        this(PythonLanguage.INSTANCE);
    }

    protected PythonFileType(Language language) {
        super(language);
    }

    @Override
    public String getId() {
        return "Python";
    }

    @Override
    public LocalizeValue getDescription() {
        return LocalizeValue.of("Python");
    }

    @Override
    public String getDefaultExtension() {
        return "py";
    }

    @Override
    public Image getIcon() {
        return PythonPsiIconGroup.python();
    }

    @Override
    public @Nullable String getCharset(VirtualFile file, byte[] content) {
        if (CharsetToolkit.hasUTF8Bom(content)) {
            return CharsetToolkit.UTF8;
        }
        ByteBuffer bytes = ByteBuffer.wrap(content, 0, Math.min(256, content.length));
        String decoded = StandardCharsets.UTF_8.decode(bytes).toString();
        return getCharsetFromEncodingDeclaration(StringUtil.convertLineSeparators(decoded));
    }

    @Override
    public @Nullable Charset extractCharsetFromFileContent(@Nullable Project project, @Nullable VirtualFile file, CharSequence content) {
        String charsetName = getCharsetFromEncodingDeclaration(content);
        if (charsetName == null) {
            return null;
        }
        try {
            return Charset.forName(charsetName);
        }
        catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return null;
        }
    }

    public static @Nullable String getCharsetFromEncodingDeclaration(PsiFile file) {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        String content;
        if (document != null && document.getLineCount() > MAX_CHARSET_ENCODING_LINE) {
            int offset = document.getLineEndOffset(MAX_CHARSET_ENCODING_LINE);
            content = document.getText(TextRange.create(0, offset));
        }
        else {
            content = file.getText();
        }
        return getCharsetFromEncodingDeclaration(content);
    }

    private static @Nullable String getCharsetFromEncodingDeclaration(@Nullable CharSequence content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new CharSequenceReader(content))) {
            for (int i = 0; i < MAX_CHARSET_ENCODING_LINE; i++) {
                String line = reader.readLine();
                if (line == null) {
                    return null;
                }
                Matcher matcher = ENCODING_PATTERN.matcher(line);
                if (matcher.find()) {
                    String charset = matcher.group(1);
                    return normalizeCharset(charset);
                }
            }
        }
        catch (IOException ignored) {
        }
        return null;
    }

    private static @Nullable String normalizeCharset(@Nullable String charset) {
        if (charset == null) {
            return null;
        }
        String lowerCharset = StringUtil.toLowerCase(charset);
        if ("latin-1".equals(lowerCharset)) {
            return "iso-8859-1";
        }
        return lowerCharset;
    }
}
