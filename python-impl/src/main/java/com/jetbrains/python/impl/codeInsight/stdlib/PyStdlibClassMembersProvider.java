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
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.types.PyClassMembersProviderBase;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.dataholder.Key;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl
public class PyStdlibClassMembersProvider extends PyClassMembersProviderBase
{
	private Key<List<PyCustomMember>> mySocketMembersKey = Key.create("socket.members");

	@Nonnull
	@Override
	public Collection<PyCustomMember> getMembers(PyClassType classType, PsiElement location, TypeEvalContext typeEvalContext)
	{
		PyClass clazz = classType.getPyClass();
		final String qualifiedName = clazz.getQualifiedName();
		if("socket._socketobject".equals(qualifiedName))
		{
			final PyFile socketFile = (PyFile) clazz.getContainingFile();
			List<PyCustomMember> socketMembers = socketFile.getUserData(mySocketMembersKey);
			if(socketMembers == null)
			{
				socketMembers = calcSocketMembers(socketFile);
				socketFile.putUserData(mySocketMembersKey, socketMembers);
			}
			return socketMembers;
		}
		return Collections.emptyList();
	}

	private static List<PyCustomMember> calcSocketMembers(PyFile socketFile)
	{
		List<PyCustomMember> result = new ArrayList<>();
		addMethodsFromAttr(socketFile, result, "_socketmethods");
		addMethodsFromAttr(socketFile, result, "_delegate_methods");
		return result;
	}

	private static void addMethodsFromAttr(PyFile socketFile, List<PyCustomMember> result, final String attrName)
	{
		final PyTargetExpression socketMethods = socketFile.findTopLevelAttribute(attrName);
		if(socketMethods != null)
		{
			final List<String> methods = PyUtil.getStringListFromTargetExpression(socketMethods);
			if(methods != null)
			{
				for(String name : methods)
				{
					result.add(new PyCustomMember(name).resolvesTo("_socket").toClass("SocketType").toFunction(name));
				}
			}
		}
	}
}
