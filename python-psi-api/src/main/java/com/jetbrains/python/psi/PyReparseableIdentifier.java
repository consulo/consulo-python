package com.jetbrains.python.psi;

import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.lexer.PythonLexer;
import consulo.application.util.registry.Registry;
import consulo.language.ast.ASTNode;
import consulo.language.impl.ast.ASTFactory;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.psi.PsiWhiteSpace;
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

public class PyReparseableIdentifier extends PyReparseableTokenType {

  @SuppressWarnings("LoggerInitializedWithForeignClass")
  private static final Logger LOG = Logger.getInstance(PyReparseableTokenType.class);

  public PyReparseableIdentifier(String debugName) {
    super(debugName);
  }

  @Override
  public @Nullable ASTNode reparseLeaf(ASTNode leaf, CharSequence newText) {
    if (!Registry.is("python.ast.leaves.incremental.reparse", false)) {
      return null;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Attempting to reparse leaf element of type" + this
                + "\nold text: \n" + leaf.getText()
                + "\n\nnew text: \n" + newText);
    }

    if (newText.isEmpty()) {
      LOG.debug("New text is empty");
      return null;
    }

    var lexingContainer = leaf.getTreeParent();
    if (lexingContainer == null) {
      LOG.debug("No re-lexing container for a leaf");
      return null;
    }

    var originalCharSequence = lexingContainer.getChars();
    var originalLeafRangeInLexingContainer = leaf.getTextRange().shiftLeft(lexingContainer.getStartOffset());
    var updatedCharSequence = StringUtil.replaceSubSequence(
      originalCharSequence, originalLeafRangeInLexingContainer.getStartOffset(), originalLeafRangeInLexingContainer.getEndOffset(),
      newText);


    var currentLeaf = TreeUtil.findFirstLeaf(lexingContainer);
    PythonLexer lexer = new PythonHighlightingLexer(LanguageLevel.forElement(leaf.getPsi()));

    lexer.start(updatedCharSequence);

    while (true) {
      if (currentLeaf == null) {
        LOG.debug("We are out of original leaves");
        return null;
      }

      var tokenType = lexer.getTokenType();

      if (currentLeaf instanceof PsiWhiteSpace) {
        currentLeaf = TreeUtil.nextLeaf(currentLeaf);
        lexer.advance();
        continue;
      }

      if (tokenType != currentLeaf.getElementType()) {
        LOG.debug("Wrong token type lexed: ", tokenType, " instead of ", currentLeaf.getElementType());
        return null;
      }

      var currentLeafRangeInLexingContainer = currentLeaf.getTextRange().shiftLeft(lexingContainer.getStartOffset());
      if (currentLeaf == leaf) {
        var expectedEndOffset = currentLeafRangeInLexingContainer.getStartOffset() + newText.length();
        if (lexer.getTokenEnd() != expectedEndOffset) {
          LOG.debug("Wrong end offset, got ", lexer.getTokenEnd(), " instead of ", expectedEndOffset);
          return null;
        }
        break;
      }
      else if (currentLeafRangeInLexingContainer.getEndOffset() != lexer.getTokenEnd()) {
        LOG.debug("Wrong token end offset for: ", tokenType,
                  "; got ", lexer.getTokenEnd(),
                  " instead of ", currentLeafRangeInLexingContainer.getEndOffset());
        return null;
      }

      currentLeaf = TreeUtil.nextLeaf(currentLeaf);
      lexer.advance();
    }

    LOG.debug("Reparse is successful");
    return ASTFactory.leaf(this, newText);
  }
}
