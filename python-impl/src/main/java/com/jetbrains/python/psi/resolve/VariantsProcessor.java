/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.resolve;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.util.lang.function.Condition;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyUtil;

public abstract class VariantsProcessor implements PsiScopeProcessor
{
	protected final PsiElement myContext;
	protected Condition<PsiElement> myNodeFilter;
	protected Condition<String> myNameFilter;

	protected boolean myPlainNamesOnly = false; // if true, add insert handlers to known things like functions
	private List<String> myAllowedNames;
	private final List<String> mySeenNames = new ArrayList<>();

	public VariantsProcessor(PsiElement context)
	{
		// empty
		myContext = context;
	}

	public VariantsProcessor(PsiElement context, @Nullable final Condition<PsiElement> nodeFilter, @Nullable final Condition<String> nameFilter)
	{
		myContext = context;
		myNodeFilter = nodeFilter;
		myNameFilter = nameFilter;
	}

	public boolean isPlainNamesOnly()
	{
		return myPlainNamesOnly;
	}

	public void setPlainNamesOnly(boolean plainNamesOnly)
	{
		myPlainNamesOnly = plainNamesOnly;
	}


	@Override
	public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState substitutor)
	{
		if(myNodeFilter != null && !myNodeFilter.value(element))
		{
			return true; // skip whatever the filter rejects
		}
		// TODO: refactor to look saner; much code duplication
		if(element instanceof PsiNamedElement)
		{
			final PsiNamedElement psiNamedElement = (PsiNamedElement) element;
			final String name = PyUtil.getElementNameWithoutExtension(psiNamedElement);
			if(name != null && nameIsAcceptable(name))
			{
				addElement(name, psiNamedElement);
			}
		}
		else if(element instanceof PyReferenceExpression)
		{
			PyReferenceExpression expr = (PyReferenceExpression) element;
			String referencedName = expr.getReferencedName();
			if(nameIsAcceptable(referencedName))
			{
				addElement(referencedName, expr);
			}
		}
		else if(element instanceof PyImportedNameDefiner)
		{
			boolean handledAsImported = false;
			if(element instanceof PyImportElement)
			{
				final PyImportElement importElement = (PyImportElement) element;
				handledAsImported = handleImportElement(importElement);
			}
			if(!handledAsImported)
			{
				final PyImportedNameDefiner definer = (PyImportedNameDefiner) element;
				for(PyElement expr : definer.iterateNames())
				{
					if(expr != null && expr != myContext)
					{ // NOTE: maybe rather have SingleIterables skip nulls outright?
						if(!expr.isValid())
						{
							throw new PsiInvalidElementAccessException(expr, "Definer: " + definer);
						}
						String referencedName = expr instanceof PyFile ? FileUtil.getNameWithoutExtension(((PyFile) expr).getName()) : expr.getName();
						if(referencedName != null && nameIsAcceptable(referencedName))
						{
							addImportedElement(referencedName, expr);
						}
					}
				}
			}
		}

		return true;
	}

	protected boolean handleImportElement(PyImportElement importElement)
	{
		final QualifiedName qName = importElement.getImportedQName();
		if(qName != null && qName.getComponentCount() == 1)
		{
			String name = importElement.getAsName() != null ? importElement.getAsName() : qName.getLastComponent();
			if(name != null && nameIsAcceptable(name))
			{
				final PsiElement resolved = importElement.resolve();
				if(resolved instanceof PsiNamedElement)
				{
					addElement(name, resolved);
					return true;
				}
			}
		}
		return false;
	}

	protected void addElement(String name, PsiElement psiNamedElement)
	{
		mySeenNames.add(name);
	}

	protected void addImportedElement(String referencedName, PyElement expr)
	{
		addElement(referencedName, expr);
	}

	private boolean nameIsAcceptable(String name)
	{
		if(name == null)
		{
			return false;
		}
		if(mySeenNames.contains(name))
		{
			return false;
		}
		if(myNameFilter != null && !myNameFilter.value(name))
		{
			return false;
		}
		if(myAllowedNames != null && !myAllowedNames.contains(name))
		{
			return false;
		}
		return true;
	}

	@Override
	@Nullable
	public <T> T getHint(@Nonnull Key<T> hintKey)
	{
		return null;
	}

	@Override
	public void handleEvent(@Nonnull Event event, Object associated)
	{
	}

	public void setAllowedNames(List<String> namesFilter)
	{
		myAllowedNames = namesFilter;
	}
}
