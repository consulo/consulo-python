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
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.psi.PyFileElementType;
import com.jetbrains.python.impl.psi.impl.PyClassImpl;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyClassAttributesIndex;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndexInsensitive;
import com.jetbrains.python.impl.psi.stubs.PySuperClassIndex;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.stubs.PyClassStub;
import consulo.index.io.StringRef;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.*;
import consulo.language.psi.util.QualifiedName;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * @author max
 */
public class PyClassElementType extends PyStubElementType<PyClassStub, PyClass>
{

	public PyClassElementType()
	{
		this("CLASS_DECLARATION");
	}

	public PyClassElementType(@Nonnull @NonNls String debugName)
	{
		super(debugName);
	}

	@Nonnull
	public PsiElement createElement(@Nonnull final ASTNode node)
	{
		return new PyClassImpl(node);
	}

	public PyClass createPsi(@Nonnull final PyClassStub stub)
	{
		return new PyClassImpl(stub);
	}

	@Nonnull
	public PyClassStub createStub(@Nonnull final PyClass psi, final StubElement parentStub)
	{
		return new PyClassStubImpl(psi.getName(), parentStub, getSuperClassQNames(psi), PyPsiUtils.toQualifiedName(psi.getMetaClassExpression()), psi.getOwnSlots(), PyPsiUtils.strValue(psi
				.getDocStringExpression()), getStubElementType());
	}

	@Nonnull
	public static Map<QualifiedName, QualifiedName> getSuperClassQNames(@Nonnull final PyClass pyClass)
	{
		final Map<QualifiedName, QualifiedName> result = new LinkedHashMap<>();

		Arrays.stream(pyClass.getSuperClassExpressions()).filter(expression -> !PyKeywordArgument.class.isInstance(expression)).map(PyClassImpl::unfoldClass).forEach(expression -> result.put
				(PyPsiUtils.toQualifiedName(expression), resolveOriginalSuperClassQName(expression)));

		return result;
	}

	@Nullable
	private static QualifiedName resolveOriginalSuperClassQName(@Nonnull PyExpression superClassExpression)
	{
		if(superClassExpression instanceof PyReferenceExpression)
		{
			final PyReferenceExpression reference = (PyReferenceExpression) superClassExpression;
			final String referenceName = reference.getName();

			if(referenceName == null)
			{
				return PyPsiUtils.toQualifiedName(superClassExpression);
			}

			final Optional<QualifiedName> qualifiedName = PyResolveUtil.resolveLocally(reference).stream().filter(PyImportElement.class::isInstance).map(PyImportElement.class::cast).filter(element
					-> element.getAsName() != null).map(PyImportElement::getImportedQName).findAny();

			if(qualifiedName.isPresent())
			{
				return qualifiedName.get();
			}
		}

		return PyPsiUtils.toQualifiedName(superClassExpression);
	}

	public void serialize(@Nonnull final PyClassStub pyClassStub, @Nonnull final StubOutputStream dataStream) throws IOException
	{
		dataStream.writeName(pyClassStub.getName());

		final Map<QualifiedName, QualifiedName> superClasses = pyClassStub.getSuperClasses();
		dataStream.writeByte(superClasses.size());
		for(Map.Entry<QualifiedName, QualifiedName> entry : superClasses.entrySet())
		{
			QualifiedName.serialize(entry.getKey(), dataStream);
			QualifiedName.serialize(entry.getValue(), dataStream);
		}

		QualifiedName.serialize(pyClassStub.getMetaClass(), dataStream);

		PyFileElementType.writeNullableList(dataStream, pyClassStub.getSlots());

		final String docString = pyClassStub.getDocString();
		dataStream.writeUTFFast(docString != null ? docString : "");
	}

	@Nonnull
	public PyClassStub deserialize(@Nonnull final StubInputStream dataStream, final StubElement parentStub) throws IOException
	{
		final String name = StringRef.toString(dataStream.readName());

		final int superClassCount = dataStream.readByte();
		final Map<QualifiedName, QualifiedName> superClasses = new LinkedHashMap<>();
		for(int i = 0; i < superClassCount; i++)
		{
			superClasses.put(QualifiedName.deserialize(dataStream), QualifiedName.deserialize(dataStream));
		}

		final QualifiedName metaClass = QualifiedName.deserialize(dataStream);

		final List<String> slots = PyFileElementType.readNullableList(dataStream);

		final String docStringInStub = dataStream.readUTFFast();
		final String docString = docStringInStub.length() > 0 ? docStringInStub : null;

		return new PyClassStubImpl(name, parentStub, superClasses, metaClass, slots, docString, getStubElementType());
	}

	public void indexStub(@Nonnull final PyClassStub stub, @Nonnull final IndexSink sink)
	{
		final String name = stub.getName();
		if(name != null)
		{
			sink.occurrence(PyClassNameIndex.KEY, name);
			sink.occurrence(PyClassNameIndexInsensitive.KEY, name.toLowerCase());
		}

		for(String attribute : PyClassAttributesIndex.getAllDeclaredAttributeNames(createPsi(stub)))
		{
			sink.occurrence(PyClassAttributesIndex.KEY, attribute);
		}

		stub.getSuperClasses().values().stream().filter(Objects::nonNull).map(QualifiedName::getLastComponent).filter(Objects::nonNull).forEach(className -> sink.occurrence(PySuperClassIndex.KEY,
				className));
	}

	@Nonnull
	protected IStubElementType getStubElementType()
	{
		return PyElementTypes.CLASS_DECLARATION;
	}
}