package com.jetbrains.python.formatter;

import com.jetbrains.python.PythonLanguage;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.Alignment;
import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.BlockEx;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.Indent;
import consulo.language.codeStyle.Spacing;
import consulo.language.codeStyle.Wrap;
import consulo.language.codeStyle.inject.DefaultInjectedLanguageBlockBuilder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PyFormattableInjectedBlock extends PyBlock implements BlockEx {

  private final DefaultInjectedLanguageBlockBuilder myBlockBuilder;

  public PyFormattableInjectedBlock(PyBlock parent,
                                    ASTNode node,
                                    @Nullable Alignment alignment,
                                    @Nullable Wrap wrap,
                                    Indent indent,
                                    CodeStyleSettings settings,
                                    PyBlockContext context) {
    super(parent, node, alignment, indent, wrap, context);
    myBlockBuilder = new DefaultInjectedLanguageBlockBuilder(settings);
  }

  @Override
  public List<Block> getSubBlocks() {
    List<Block> result = new ArrayList<>();
    myBlockBuilder.addInjectedBlocks(result, getNode(), getWrap(), getAlignment(), getIndent());
    return result;
  }

  @Override
  public @Nullable Spacing getSpacing(@Nullable Block child1, Block child2) {
    return null;
  }

  @Override
  public boolean isLeaf() {
    return false;
  }

  @Override
  public @Nullable Language getLanguage() {
    return PythonLanguage.getInstance();
  }
}
