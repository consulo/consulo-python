package org.consulo.python.formatter;

import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.formatting.Indent;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author VISTALL
 * @since 23.08.13.
 */
public class PythonFormattingModelBuilder implements FormattingModelBuilder {
	private static boolean DUMP_FORMATTING_AST = false;

	@Override
	@NotNull
	public FormattingModel createModel(PsiElement element, CodeStyleSettings settings) {
		if (DUMP_FORMATTING_AST) {
			ASTNode fileNode = element.getContainingFile().getNode();
			System.out.println("AST tree for " + element.getContainingFile().getName() + ":");
			printAST(fileNode, 0);
		}

		return FormattingModelProvider.createFormattingModelForPsiFile(element.getContainingFile(), new PyBlock(element.getNode(), null, Indent.getNoneIndent(), null, settings), settings);
	}

	private void printAST(ASTNode node, int indent) {
		while (node != null) {
			for (int i = 0; i < indent; i++) {
				System.out.print(" ");
			}
			System.out.println(node.toString() + " " + node.getTextRange().toString());
			printAST(node.getFirstChildNode(), indent + 2);
			node = node.getTreeNext();
		}
	}

	@Nullable
	@Override
	public TextRange getRangeAffectingIndent(PsiFile psiFile, int i, ASTNode astNode) {
		return null;
	}
}
