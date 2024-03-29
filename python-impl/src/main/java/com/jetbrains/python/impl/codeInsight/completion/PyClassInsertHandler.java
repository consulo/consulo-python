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
package com.jetbrains.python.impl.codeInsight.completion;

import consulo.language.editor.AutoPopupController;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.PyUtil;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;

/**
 * @author yole
 */
public class PyClassInsertHandler implements InsertHandler<LookupElement>
{
	public static PyClassInsertHandler INSTANCE = new PyClassInsertHandler();

	private PyClassInsertHandler()
	{
	}

	public void handleInsert(InsertionContext context, LookupElement item)
	{
		final Editor editor = context.getEditor();
		final Document document = editor.getDocument();
		if(context.getCompletionChar() == '(')
		{
			context.setAddCompletionChar(false);
			final int offset = context.getTailOffset();
			document.insertString(offset, "()");

			PyClass pyClass = PyUtil.as(item.getPsiElement(), PyClass.class);
			PyFunction init = pyClass != null ? pyClass.findInitOrNew(true, null) : null;
			if(init != null && PyFunctionInsertHandler.hasParams(context, init))
			{
				editor.getCaretModel().moveToOffset(offset + 1);
				AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), init);
			}
			else
			{
				editor.getCaretModel().moveToOffset(offset + 2);
			}
		}
	}
}
