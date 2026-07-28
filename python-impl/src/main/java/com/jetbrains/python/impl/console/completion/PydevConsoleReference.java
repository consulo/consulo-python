/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.console.completion;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.IToken;
import com.jetbrains.python.console.pydev.PyCodeCompletionImages;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.completion.CompletionInitializationContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.ParenthesesInsertHandler;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.util.lang.StringUtil;

import java.util.List;
import java.util.Map;

/**
 * @author oleg
 */
public class PydevConsoleReference extends PsiPolyVariantReferenceBase<PyReferenceExpression>
{
	private final ConsoleCommunication myCommunication;
	private final String myPrefix;
	private final boolean myAllowRemoteResolve;

	public PydevConsoleReference(PyReferenceExpression expression, ConsoleCommunication communication, String prefix, boolean allowRemoteResolve)
	{
		super(expression, true);
		myCommunication = communication;
		myPrefix = prefix;
		myAllowRemoteResolve = allowRemoteResolve;
	}

	@Override
    @RequiredReadAction
    public ResolveResult[] multiResolve(boolean incompleteCode)
	{
		if(!myAllowRemoteResolve)
		{
			return RatedResolveResult.EMPTY_ARRAY;
		}
		PyExpression pyExpression = resolveToDummyDescription();
		if(pyExpression == null)
		{
			return RatedResolveResult.EMPTY_ARRAY;
		}

		if(pyExpression instanceof PyReferenceExpression)
		{
			PsiReference redirectedRef = pyExpression.getReference();
			if(redirectedRef != null)
			{
				PsiElement resolved = redirectedRef.resolve();
				if(resolved != null)
				{
					return new ResolveResult[]{new RatedResolveResult(RatedResolveResult.RATE_HIGH, resolved)};
				}
			}
		}

		return RatedResolveResult.EMPTY_ARRAY;

	}

	@RequiredReadAction
    public PyElement getDocumentationElement()
	{
		return resolveToDummyDescription();
	}

	@RequiredReadAction
    private PyExpression resolveToDummyDescription()
	{
		String qualifiedName = myElement.getText();
		if(qualifiedName == null)
		{
			return null;
		}
		String description;
		try
		{

			description = myCommunication.getDescription(qualifiedName);
			if(Strings.isNullOrEmpty(description))
			{
				return null;
			}
		}
		catch(Exception e)
		{
			return null;
		}

		PyElementGenerator generator = PyElementGenerator.getInstance(myElement.getProject());
		PyFile dummyFile = (PyFile) generator.createDummyFile(LanguageLevel.forElement(myElement), description);
        Module module = myElement.getModule();
		if(module != null)
		{
			dummyFile.putUserData(ModuleUtilCore.KEY_MODULE, module);
		}

		List<PyStatement> statements = dummyFile.getStatements();
		PyStatement pyStatement = statements.get(statements.size() - 1);
		return pyStatement instanceof PyExpressionStatement ? ((PyExpressionStatement) pyStatement).getExpression() : null;
	}

	@Override
    @RequiredReadAction
    public Object[] getVariants()
	{
		Map<String, LookupElement> variants = Maps.newHashMap();
		try
		{
			List<PydevCompletionVariant> completions = myCommunication.getCompletions(getText(), myPrefix);
			for(PydevCompletionVariant completion : completions)
			{
				PsiManager manager = myElement.getManager();
				String name = completion.getName();
				int type = completion.getType();
				LookupElementBuilder builder = LookupElementBuilder.create(new PydevConsoleElement(manager, name, completion.getDescription())).withIcon(PyCodeCompletionImages.getImageForType(type));


				String args = completion.getArgs();
				if(args.equals("(%)"))
				{
					builder.withPresentableText("%" + completion.getName());
					builder = builder.withInsertHandler((context, item) -> {
                        Editor editor = context.getEditor();
                        Document document = editor.getDocument();
                        int offset = context.getStartOffset();
                        if(offset == 0 || !"%".equals(document.getText(TextRange.from(offset - 1, 1))))
                        {
                            document.insertString(offset, "%");
                        }
                    });
					args = "";
				}
				else if(!StringUtil.isEmptyOrSpaces(args))
				{
					builder = builder.withTailText(args);
				}
				// Set function insert handler
				if(type == IToken.TYPE_FUNCTION || args.endsWith(")"))
				{
					builder = builder.withInsertHandler(ParenthesesInsertHandler.WITH_PARAMETERS);
				}
				variants.put(name, builder);
			}
		}
		catch(Exception e)
		{
			//LOG.error(e);
		}
		return variants.values().toArray();
	}

	@RequiredReadAction
    private String getText()
	{
		PsiElement element = PsiTreeUtil.getParentOfType(getElement(), PyFile.class);
		if(element != null)
		{
			return element.getText().replace(CompletionInitializationContext.DUMMY_IDENTIFIER, "");
		}
		return myPrefix;
	}
}
