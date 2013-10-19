package com.jetbrains.python.psi.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ReferenceRange;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * @author max
 */
public class PyBaseElementImpl<T extends StubElement> extends StubBasedPsiElementBase<T> implements PyElement
{
	public PyBaseElementImpl(final T stub, IStubElementType nodeType)
	{
		super(stub, nodeType);
	}

	public PyBaseElementImpl(final ASTNode node)
	{
		super(node);
	}

	private static void addReferences(int offset, PsiElement element, final Collection<PsiReference> outReferences, PyResolveContext resolveContext)
	{
		final PsiReference[] references;
		if(element instanceof PyReferenceOwner)
		{
			final PsiPolyVariantReference reference = ((PyReferenceOwner) element).getReference(resolveContext);
			references = reference == null ? PsiReference.EMPTY_ARRAY : new PsiReference[]{reference};
		}
		else
		{
			references = element.getReferences();
		}
		for(final PsiReference reference : references)
		{
			for(TextRange range : ReferenceRange.getRanges(reference))
			{
				assert range != null : reference;
				if(range.containsOffset(offset))
				{
					outReferences.add(reference);
				}
			}
		}
	}

	@NotNull
	@Override
	public PythonLanguage getLanguage()
	{
		return (PythonLanguage) PythonFileType.INSTANCE.getLanguage();
	}

	@Override
	public String toString()
	{
		String className = getClass().getName();
		int pos = className.lastIndexOf('.');
		if(pos >= 0)
		{
			className = className.substring(pos + 1);
		}
		if(className.endsWith("Impl"))
		{
			className = className.substring(0, className.length() - 4);
		}
		return className;
	}

	public void accept(@NotNull PsiElementVisitor visitor)
	{
		if(visitor instanceof PyElementVisitor)
		{
			acceptPyVisitor(((PyElementVisitor) visitor));
		}
		else
		{
			super.accept(visitor);
		}
	}

	protected void acceptPyVisitor(PyElementVisitor pyVisitor)
	{
		pyVisitor.visitPyElement(this);
	}

	@NotNull
	protected <T extends PyElement> T[] childrenToPsi(TokenSet filterSet, T[] array)
	{
		final ASTNode[] nodes = getNode().getChildren(filterSet);
		return PyPsiUtils.nodesToPsi(nodes, array);
	}

	@Nullable
	protected <T extends PyElement> T childToPsi(TokenSet filterSet, int index)
	{
		final ASTNode[] nodes = getNode().getChildren(filterSet);
		if(nodes.length <= index)
		{
			return null;
		}
		//noinspection unchecked
		return (T) nodes[index].getPsi();
	}

	@Nullable
	protected <T extends PyElement> T childToPsi(IElementType elType)
	{
		final ASTNode node = getNode().findChildByType(elType);
		if(node == null)
		{
			return null;
		}

		//noinspection unchecked
		return (T) node.getPsi();
	}

	@NotNull
	protected <T extends PyElement> T childToPsiNotNull(TokenSet filterSet, int index)
	{
		final PyElement child = childToPsi(filterSet, index);
		if(child == null)
		{
			throw new RuntimeException("child must not be null: expression text " + getText());
		}
		//noinspection unchecked
		return (T) child;
	}

	@NotNull
	protected <T extends PyElement> T childToPsiNotNull(IElementType elType)
	{
		final PyElement child = childToPsi(elType);
		if(child == null)
		{
			throw new RuntimeException("child must not be null; expression text " + getText());
		}
		//noinspection unchecked
		return (T) child;
	}

	/**
	 * Overrides the findReferenceAt() logic in order to provide a resolve context with origin file for returned references.
	 * The findReferenceAt() is usually invoked from UI operations, and it helps to be able to do deeper analysis in the
	 * current file for such operations.
	 *
	 * @param offset the offset to find the reference at
	 * @return the reference or null.
	 */
	@Override
	public PsiReference findReferenceAt(int offset)
	{
		// copy/paste from SharedPsiElementImplUtil
		PsiElement element = findElementAt(offset);
		if(element == null || element instanceof OuterLanguageElement)
		{
			return null;
		}
		offset = getTextRange().getStartOffset() + offset - element.getTextRange().getStartOffset();

		List<PsiReference> referencesList = new ArrayList<PsiReference>();
		final PsiFile file = element.getContainingFile();
		final PyResolveContext resolveContext = file != null ? PyResolveContext.defaultContext().withTypeEvalContext(TypeEvalContext.codeAnalysis(file)) : PyResolveContext.defaultContext();
		while(element != null)
		{
			addReferences(offset, element, referencesList, resolveContext);
			offset = element.getStartOffsetInParent() + offset;
			if(element instanceof PsiFile)
			{
				break;
			}
			element = element.getParent();
		}

		if(referencesList.isEmpty())
		{
			return null;
		}
		if(referencesList.size() == 1)
		{
			return referencesList.get(0);
		}
		return new PsiMultiReference(referencesList.toArray(new PsiReference[referencesList.size()]), referencesList.get(referencesList.size() - 1).getElement());
	}
}
