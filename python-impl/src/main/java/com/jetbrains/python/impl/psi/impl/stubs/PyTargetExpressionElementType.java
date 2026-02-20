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
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.impl.psi.stubs.PyInstanceAttributeIndex;
import com.jetbrains.python.impl.psi.stubs.PyVariableNameIndex;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStubType;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import consulo.component.extension.Extensions;
import consulo.index.io.StringRef;
import consulo.language.ast.ASTNode;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.io.IOException;

/**
 * @author yole
 */
public class PyTargetExpressionElementType extends PyStubElementType<PyTargetExpressionStub, PyTargetExpression>
{
	private CustomTargetExpressionStubType[] myCustomStubTypes;

	public PyTargetExpressionElementType()
	{
		super("TARGET_EXPRESSION");
	}

	public PyTargetExpressionElementType(@Nonnull @NonNls String debugName)
	{
		super(debugName);
	}

	private CustomTargetExpressionStubType[] getCustomStubTypes()
	{
		if(myCustomStubTypes == null)
		{
			myCustomStubTypes = Extensions.getExtensions(CustomTargetExpressionStubType.EP_NAME);
		}
		return myCustomStubTypes;
	}

	@Nonnull
	public PsiElement createElement(@Nonnull ASTNode node)
	{
		return new PyTargetExpressionImpl(node);
	}

	public PyTargetExpression createPsi(@Nonnull PyTargetExpressionStub stub)
	{
		return new PyTargetExpressionImpl(stub);
	}

	@Nonnull
	public PyTargetExpressionStub createStub(@Nonnull PyTargetExpression psi, StubElement parentStub)
	{
		String name = psi.getName();
		PyExpression assignedValue = psi.findAssignedValue();
		String docString = DocStringUtil.getDocStringValue(psi);
		String typeComment = psi.getTypeCommentAnnotation();
		for(CustomTargetExpressionStubType customStubType : getCustomStubTypes())
		{
			CustomTargetExpressionStub customStub = customStubType.createStub(psi);
			if(customStub != null)
			{
				return new PyTargetExpressionStubImpl(name, docString, typeComment, customStub, parentStub);
			}
		}
		PyTargetExpressionStub.InitializerType initializerType = PyTargetExpressionStub.InitializerType.Other;
		QualifiedName initializer = null;
		if(assignedValue instanceof PyReferenceExpression)
		{
			initializerType = PyTargetExpressionStub.InitializerType.ReferenceExpression;
			initializer = ((PyReferenceExpression) assignedValue).asQualifiedName();
		}
		else if(assignedValue instanceof PyCallExpression)
		{
			initializerType = PyTargetExpressionStub.InitializerType.CallExpression;
			PyExpression callee = ((PyCallExpression) assignedValue).getCallee();
			if(callee instanceof PyReferenceExpression)
			{
				initializer = ((PyReferenceExpression) callee).asQualifiedName();
			}
		}
		return new PyTargetExpressionStubImpl(name, docString, initializerType, initializer, psi.isQualified(), typeComment, parentStub);
	}

	public void serialize(@Nonnull PyTargetExpressionStub stub, @Nonnull StubOutputStream stream) throws IOException
	{
		stream.writeName(stub.getName());
		String docString = stub.getDocString();
		stream.writeUTFFast(docString != null ? docString : "");
		stream.writeVarInt(stub.getInitializerType().getIndex());
		stream.writeName(stub.getTypeComment());
		CustomTargetExpressionStub customStub = stub.getCustomStub(CustomTargetExpressionStub.class);
		if(customStub != null)
		{
			stream.writeName(customStub.getTypeClass().getCanonicalName());
			customStub.serialize(stream);
		}
		else
		{
			QualifiedName.serialize(stub.getInitializer(), stream);
			stream.writeBoolean(stub.isQualified());
		}
	}

	@Nonnull
	public PyTargetExpressionStub deserialize(@Nonnull StubInputStream stream, StubElement parentStub) throws IOException
	{
		String name = StringRef.toString(stream.readName());
		String docString = stream.readUTFFast();
		if(docString.isEmpty())
		{
			docString = null;
		}
		PyTargetExpressionStub.InitializerType initializerType = PyTargetExpressionStub.InitializerType.fromIndex(stream.readVarInt());
		StringRef typeCommentRef = stream.readName();
		String typeComment = typeCommentRef == null ? null : typeCommentRef.getString();
		if(initializerType == PyTargetExpressionStub.InitializerType.Custom)
		{
			String typeName = stream.readName().getString();
			for(CustomTargetExpressionStubType type : getCustomStubTypes())
			{
				if(type.getClass().getCanonicalName().equals(typeName))
				{
					CustomTargetExpressionStub stub = type.deserializeStub(stream);
					return new PyTargetExpressionStubImpl(name, docString, typeComment, stub, parentStub);
				}
			}
			throw new IOException("Unknown custom stub type " + typeName);
		}
		QualifiedName initializer = QualifiedName.deserialize(stream);
		boolean isQualified = stream.readBoolean();
		return new PyTargetExpressionStubImpl(name, docString, initializerType, initializer, isQualified, typeComment, parentStub);
	}

	public boolean shouldCreateStub(ASTNode node)
	{
		if(PsiTreeUtil.getParentOfType(node.getPsi(), PyComprehensionElement.class, true, PyDocStringOwner.class) != null)
		{
			return false;
		}
		ASTNode functionNode = TreeUtil.findParent(node, PyElementTypes.FUNCTION_DECLARATION);
		ASTNode qualifierNode = node.findChildByType(PythonDialectsTokenSetProvider.INSTANCE.getReferenceExpressionTokens());
		if(functionNode != null && qualifierNode != null)
		{
			PsiElement function = functionNode.getPsi();
			if(function instanceof PyFunction && PyNames.NEW.equals(((PyFunction) function).getName()))
			{
				return true;
			}
			ASTNode parameterList = functionNode.findChildByType(PyElementTypes.PARAMETER_LIST);
			assert parameterList != null;
			ASTNode[] children = parameterList.getChildren(PyElementTypes.FORMAL_PARAMETER_SET);
			if(children.length > 0 && children[0].getText().equals(qualifierNode.getText()))
			{
				return true;
			}
		}
		return functionNode == null && qualifierNode == null;
	}

	@Override
	public void indexStub(@Nonnull PyTargetExpressionStub stub, @Nonnull IndexSink sink)
	{
		String name = stub.getName();
		if(name != null && PyUtil.getInitialUnderscores(name) == 0)
		{
			if(stub.getParentStub() instanceof PyFileStub)
			{
				sink.occurrence(PyVariableNameIndex.KEY, name);
			}
			else if(isInstanceAttributeStub(stub))
			{
				sink.occurrence(PyInstanceAttributeIndex.KEY, name);
			}
		}
		for(CustomTargetExpressionStubType stubType : getCustomStubTypes())
		{
			stubType.indexStub(stub, sink);
		}
	}

	private static boolean isInstanceAttributeStub(PyTargetExpressionStub stub)
	{
		StubElement parent = stub.getParentStub();
		return parent instanceof PyFunctionStub;   // otherwise we wouldn't create the stub (see shouldCreateStub() implementation)
	}
}
