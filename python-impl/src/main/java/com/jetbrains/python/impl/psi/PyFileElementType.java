// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.PythonRuntimeService;
import com.jetbrains.python.parsing.console.PyConsoleParser;
import com.jetbrains.python.parsing.console.PythonConsoleData;
import com.jetbrains.python.parsing.console.PythonConsoleLexer;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.impl.psi.impl.stubs.PyFileStubBuilder;
import com.jetbrains.python.impl.psi.impl.stubs.PyFileStubImpl;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.stubs.PyFileStub;
import consulo.index.io.StringRef;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.lexer.Lexer;
import consulo.language.parser.ParserDefinition;
import consulo.language.parser.PsiBuilder;
import consulo.language.parser.PsiBuilderFactory;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.stub.*;
import consulo.language.version.LanguageVersion;
import consulo.language.version.LanguageVersionUtil;
import consulo.project.Project;

import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;


public class PyFileElementType extends IStubFileElementType<PyFileStub> {
  public static final PyFileElementType INSTANCE = new PyFileElementType(PythonLanguage.getInstance());

  protected PyFileElementType(Language language) {
    super(language);
  }

  @Override
  public StubBuilder getBuilder() {
    return new PyFileStubBuilder();
  }

  @Override
  public int getStubVersion() {
    // Don't forget to update versions of indexes that use the updated stub-based elements
    return 123;
  }

  @Override
  public @Nullable ASTNode parseContents(ASTNode node) {
    LanguageLevel languageLevel = getLanguageLevel(node.getPsi());
    PythonRuntimeService instance = PythonRuntimeService.getInstance();
    if (instance != null) {
      PythonConsoleData consoleData = instance.getPythonConsoleData(node);
      if (consoleData != null) {
        return parseConsoleCode(node, consoleData);
      }
    }
    PsiElement psi = node.getPsi();
    if (psi != null) {
      Project project = psi.getProject();
      PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
      Language language = getLanguage();
      ParserDefinition parserDefinition = ParserDefinition.forLanguage(language);
      if (parserDefinition == null) {
        return null;
      }
      LanguageVersion languageVersion = LanguageVersionUtil.findDefaultVersion(language);
      Lexer lexer = parserDefinition.createLexer(languageVersion);
      PsiParser parser = parserDefinition.createParser(languageVersion);
      PsiBuilder builder = factory.createBuilder(project, node, lexer, language, languageVersion, node.getChars());
      if (parser instanceof PyParser pythonParser) {
        pythonParser.setLanguageLevel(languageLevel);
      }
      var startTime = System.nanoTime();
      var result = parser.parse(this, builder, languageVersion).getFirstChildNode();
      // TODO consulo: ParsingDiagnostics missing
      // ParsingDiagnostics.registerParse(builder, getLanguage(), System.nanoTime() - startTime);
      return result;
    }
    return null;
  }

  private @Nullable ASTNode parseConsoleCode(ASTNode node, PythonConsoleData consoleData) {
    Lexer lexer = createConsoleLexer(node, consoleData);
    PsiElement psi = node.getPsi();
    if (psi != null) {
      Project project = psi.getProject();
      PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
      LanguageVersion defaultVersion = LanguageVersionUtil.findDefaultVersion(PythonLanguage.getInstance());
      PsiBuilder builder = factory.createBuilder(project, node, lexer, getLanguage(), defaultVersion, node.getChars());
      PyParser parser = new PyConsoleParser(consoleData, getLanguageLevel(psi));

      return parser.parse(this, builder, defaultVersion).getFirstChildNode();
    }
    return null;
  }

  private @Nullable Lexer createConsoleLexer(ASTNode node, PythonConsoleData consoleData) {
    if (consoleData.isIPythonEnabled()) {
      return new PythonConsoleLexer();
    }
    else {
      ParserDefinition parserDefinition = ParserDefinition.forLanguage(getLanguage());
      if (parserDefinition == null) {
        return null;
      }
      PsiElement psi = node.getPsi();
      if (psi == null) {
        return null;
      }
      Project project = psi.getProject();
      return parserDefinition.createLexer(LanguageVersionUtil.findDefaultVersion(PythonLanguage.getInstance()));
    }
  }

  private static LanguageLevel getLanguageLevel(PsiElement psi) {
    PsiFile file = psi.getContainingFile();
    if (!(file instanceof PyFile)) {
      PsiElement context = file.getContext();
      if (context != null) {
        return getLanguageLevel(context);
      }
      return LanguageLevel.getDefault();
    }
    return ((PyFile)file).getLanguageLevel();
  }

  @Override
  public String getExternalId() {
    return "python.FILE";
  }

  @Override
  public void serialize(PyFileStub stub, StubOutputStream dataStream) throws IOException {
    writeNullableList(dataStream, stub.getDunderAll());
    writeBitSet(dataStream, stub.getFutureFeatures());
    dataStream.writeName(stub.getDeprecationMessage());
  }

  @Override
  public PyFileStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    List<String> all = readNullableList(dataStream);
    BitSet future_features = readBitSet(dataStream);
    StringRef deprecationMessage = dataStream.readName();
    return new PyFileStubImpl(all, future_features, deprecationMessage);
  }

  private static BitSet readBitSet(StubInputStream dataStream) throws IOException {
    // NOTE: here we assume that bitset has no more than 32 bits so that the value fits into an int.
    BitSet ret = new BitSet(32); // see PyFileStubImpl: we assume that all bits fit into an int
    int bits = dataStream.readInt();
    for (int i = 0; i < 32; i += 1) {
      boolean bit = (bits & (1 << i)) != 0;
      ret.set(i, bit);
    }
    return ret;
  }

  private static void writeBitSet(StubOutputStream dataStream, BitSet bitset) throws IOException {
    // NOTE: here we assume that bitset has no more than 32 bits so that the value fits into an int.
    int result = 0;
    for (int i = 0; i < 32; i += 1) {
      int bit = (bitset.get(i) ? 1 : 0) << i;
      result |= bit;
    }
    dataStream.writeInt(result);
  }

  public static void writeNullableList(StubOutputStream dataStream, @Nullable List<String> names) throws IOException {
    if (names == null) {
      dataStream.writeBoolean(false);
    }
    else {
      dataStream.writeBoolean(true);
      dataStream.writeVarInt(names.size());
      for (String name : names) {
        dataStream.writeName(name);
      }
    }
  }

  public static @Nullable List<String> readNullableList(StubInputStream dataStream) throws IOException {
    boolean hasNames = dataStream.readBoolean();
    List<String> names = null;
    if (hasNames) {
      int size = dataStream.readVarInt();
      names = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        names.add(dataStream.readNameString());
      }
    }
    return names;
  }
}
