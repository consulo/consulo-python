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

import consulo.language.Language;
import consulo.language.file.LanguageFileType;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.util.io.CharsetToolkit;
import consulo.virtualFileSystem.VirtualFile;
import consulo.util.io.CharSequenceReader;
import consulo.localize.LocalizeValue;
import consulo.python.psi.icon.PythonPsiIconGroup;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class PythonFileType extends LanguageFileType {
  private static final Pattern ENCODING_PATTERN = Pattern.compile("coding[:=]\\s*([-\\w.]+)");

  public static PythonFileType INSTANCE = new PythonFileType();

  public PythonFileType() {
    this(PythonLanguage.INSTANCE);
  }

  public PythonFileType(Language language) {
    super(language);
  }

  @Nonnull
  public String getId() {
    return "Python";
  }

  @Nonnull
  public LocalizeValue getDescription() {
    return LocalizeValue.localizeTODO("Python files");
  }

  @Nonnull
  public String getDefaultExtension() {
    return "py";
  }

  @Nonnull
  public Image getIcon() {
    return PythonPsiIconGroup.pythonfile();
  }

  @Override
  public String getCharset(@Nonnull VirtualFile file, byte[] content) {
    if (CharsetToolkit.hasUTF8Bom(content)) {
      return CharsetToolkit.UTF8;
    }
    ByteBuffer bytes = ByteBuffer.wrap(content, 0, Math.min(256, content.length));
    String decoded = CharsetToolkit.UTF8_CHARSET.decode(bytes).toString();
    return getCharsetFromEncodingDeclaration(StringUtil.convertLineSeparators(decoded));
  }

  @Override
  public Charset extractCharsetFromFileContent(@Nullable Project project, @Nullable VirtualFile file, @Nonnull CharSequence content) {
    final String charsetName = getCharsetFromEncodingDeclaration(content);
    if (charsetName == null) {
      return null;
    }
    try {
      return Charset.forName(charsetName);
    }
    catch (IllegalCharsetNameException e) {
      return null;
    }
    catch (UnsupportedCharsetException e) {
      return null;
    }
  }

  @Nullable
  public static String getCharsetFromEncodingDeclaration(CharSequence content) {
    if (content == null || content.length() == 0) {
      return null;
    }
    try {
      final BufferedReader reader = new BufferedReader(new CharSequenceReader(content));
      try {
        for (int i = 0; i < 2; i++) {
          final String line = reader.readLine();
          if (line == null) {
            return null;
          }
          final Matcher matcher = ENCODING_PATTERN.matcher(line);
          if (matcher.find()) {
            final String charset = matcher.group(1);
            return normalizeCharset(charset);
          }
        }
      } finally {
        reader.close();
      }
    }
    catch (IOException ignored) {
    }
    return null;
  }

  @Nullable
  private static String normalizeCharset(String charset) {
    if (charset == null) {
      return null;
    }
    charset = charset.toLowerCase();
    if ("latin-1".equals(charset)) {
      return "iso-8859-1";
    }
    return charset;
  }
}
