package com.jetbrains.python.debugger;

import java.util.List;

import org.jspecify.annotations.Nullable;
import com.google.common.collect.Lists;
import consulo.util.lang.StringUtil;

/**
 * @author traff
 */
public class PySignature
{
	private static final String UNION_PREFIX = "Union[";
	private final String myFile;
	private final String myFunctionName;

	private NamedParameter myReturnType = null;

	private final List<NamedParameter> myArgs = Lists.newArrayList();

	public PySignature(String file, String name)
	{
		myFile = file;
		myFunctionName = name;
	}

	@Nullable
	public String getArgTypeQualifiedName(String name)
	{
		for(NamedParameter param : myArgs)
		{
			if(name.equals(param.getName()))
			{
				return param.getTypeQualifiedName();
			}
		}
		return null;
	}

	public String getFile()
	{
		return myFile;
	}

	public String getFunctionName()
	{
		return myFunctionName;
	}

	public List<NamedParameter> getArgs()
	{
		return myArgs;
	}

	public NamedParameter getReturnType()
	{
		return myReturnType;
	}

	public PySignature addReturnType(@Nullable String returnType)
	{
		if(StringUtil.isNotEmpty(returnType))
		{
			if(myReturnType != null)
			{
				myReturnType.addType(returnType);
			}
			else
			{
				myReturnType = new NamedParameter("", returnType);
			}
		}
		return this;
	}

	public PySignature addAllArgs(PySignature signature)
	{
		for(NamedParameter param : signature.getArgs())
		{
			NamedParameter ourParam = getArgForName(param.getName());
			if(ourParam != null)
			{
				ourParam.addTypes(param.getTypesList());
			}
			else
			{
				addArgument(param);
			}
		}
		return this;
	}

	@Nullable
	private NamedParameter getArgForName(String name)
	{
		for(NamedParameter param : myArgs)
		{
			if(param.getName().equals(name))
			{
				return param;
			}
		}

		return null;
	}

	@Nullable
	public String getReturnTypeQualifiedName()
	{
		return myReturnType != null ? myReturnType.getTypeQualifiedName() : null;
	}


	public static class NamedParameter
	{
		private final String myName;
		private final List<String> myTypes;

		private NamedParameter(String name, String type)
		{
			myName = name;

			myTypes = parseTypes(type);
		}

		private static List<String> parseTypes(String type)
		{
			if(type.startsWith(UNION_PREFIX) && type.endsWith("]"))
			{
				return Lists.newArrayList(type.substring(UNION_PREFIX.length(), type.length() - 1).split("\\s*,\\s*"));
			}
			else
			{
				String[] parts = type.split(" or ");
				return Lists.newArrayList(parts);
			}
		}

		public String getName()
		{
			return myName;
		}

		public String getTypeQualifiedName()
		{
			if(myTypes.size() == 1)
			{
				return noneTypeToNone(myTypes.get(0));
			}
			else
			{
				return UNION_PREFIX + StringUtil.join(myTypes, NamedParameter::noneTypeToNone, ", ") + "]";
			}
		}

		@Nullable
		private static String noneTypeToNone(@Nullable String type)
		{
			return "NoneType".equals(type) ? "None" : type;
		}

		public void addType(String type)
		{
			if(!myTypes.contains(type))
			{
				myTypes.add(type);
			}
		}

		public void addTypes(List<String> newTypes)
		{
			for(String type : newTypes)
			{
				addType(type);
			}
		}

		public List<String> getTypesList()
		{
			return myTypes;
		}
	}


	public PySignature addArgument(String name, String type)
	{
		return addArgument(new NamedParameter(name, type));
	}

	public PySignature addArgument(NamedParameter argument)
	{
		myArgs.add(argument);
		return this;
	}
}
