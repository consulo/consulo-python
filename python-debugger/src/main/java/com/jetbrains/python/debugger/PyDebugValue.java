package com.jetbrains.python.debugger;

import com.google.common.base.Strings;
import com.jetbrains.python.debugger.pydev.PyVariableLocator;
import consulo.application.ApplicationManager;
import consulo.execution.debug.frame.*;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.logging.Logger;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// todo: load long lists by parts
// todo: null modifier for modify modules, class objects etc.
public class PyDebugValue extends XNamedValue
{
	private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.pydev.PyDebugValue");
	public static final int MAX_VALUE = 256;

	public static final String RETURN_VALUES_PREFIX = "__pydevd_ret_val_dict";

	private String myTempName = null;
	private final String myType;
	private final String myTypeQualifier;
	private final String myValue;
	private final boolean myContainer;
	private final boolean myIsReturnedVal;
	private final boolean myIsIPythonHidden;
	private final PyDebugValue myParent;
	private String myId = null;

	private final PyFrameAccessor myFrameAccessor;

	private PyVariableLocator myVariableLocator;

	private final boolean myErrorOnEval;

	public PyDebugValue(@Nonnull final String name,
			final String type,
			String typeQualifier,
			final String value,
			final boolean container,
			boolean isReturnedVal,
			boolean isIPythonHidden,
			boolean errorOnEval,
			final PyFrameAccessor frameAccessor)
	{
		this(name, type, typeQualifier, value, container, isReturnedVal, isIPythonHidden, errorOnEval, null, frameAccessor);
	}

	public PyDebugValue(@Nonnull final String name,
			final String type,
			String typeQualifier,
			final String value,
			final boolean container,
			boolean isReturnedVal,
			boolean isIPythonHidden,
			boolean errorOnEval,
			final PyDebugValue parent,
			final PyFrameAccessor frameAccessor)
	{
		super(name);
		myType = type;
		myTypeQualifier = Strings.isNullOrEmpty(typeQualifier) ? null : typeQualifier;
		myValue = value;
		myContainer = container;
		myIsReturnedVal = isReturnedVal;
		myIsIPythonHidden = isIPythonHidden;
		myErrorOnEval = errorOnEval;
		myParent = parent;
		myFrameAccessor = frameAccessor;
	}

	public String getTempName()
	{
		return myTempName != null ? myTempName : myName;
	}

	public void setTempName(String tempName)
	{
		myTempName = tempName;
	}

	public String getType()
	{
		return myType;
	}

	public String getValue()
	{
		return myValue;
	}

	public boolean isContainer()
	{
		return myContainer;
	}

	public boolean isReturnedVal()
	{
		return myIsReturnedVal;
	}

	public boolean isIPythonHidden()
	{
		return myIsIPythonHidden;
	}

	public boolean isErrorOnEval()
	{
		return myErrorOnEval;
	}

	public PyDebugValue setParent(@Nullable PyDebugValue parent)
	{
		return new PyDebugValue(myName, myType, myTypeQualifier, myValue, myContainer, myIsReturnedVal, myIsIPythonHidden, myErrorOnEval, parent, myFrameAccessor);
	}

	public PyDebugValue getParent()
	{
		return myParent;
	}

	public PyDebugValue getTopParent()
	{
		return myParent == null ? this : myParent.getTopParent();
	}

	@Override
	public String getEvaluationExpression()
	{
		StringBuilder stringBuilder = new StringBuilder();
		buildExpression(stringBuilder);
		return stringBuilder.toString();
	}

	void buildExpression(StringBuilder result)
	{
		if(myParent == null)
		{
			result.append(getTempName());
		}
		else
		{
			myParent.buildExpression(result);
			if(("dict".equals(myParent.getType()) || "list".equals(myParent.getType()) || "tuple".equals(myParent.getType())) && !isLen(myName))
			{
				result.append('[').append(removeLeadingZeros(removeId(myName))).append(']');
			}
			else if(("set".equals(myParent.getType())) && !isLen(myName))
			{
				//set doesn't support indexing
			}
			else if(isLen(myName))
			{
				result.append('.').append(myName).append("()");
			}
			else if(("ndarray".equals(myParent.getType()) || "matrix".equals(myParent.getType())) && myName.startsWith("["))
			{
				result.append(removeLeadingZeros(myName));
			}
			else
			{
				result.append('.').append(myName);
			}
		}
	}

	public String getFullName()
	{
		return wrapWithPrefix(getName());
	}

	private static String removeId(@Nonnull String name)
	{
		if(name.indexOf('(') != -1)
		{
			name = name.substring(0, name.indexOf('(')).trim();
		}

		return name;
	}

	private static String removeLeadingZeros(@Nonnull String name)
	{
		//bugs.python.org/issue15254: "0" prefix for octal
		while(name.length() > 1 && name.startsWith("0"))
		{
			name = name.substring(1);
		}
		return name;
	}

	private static boolean isLen(String name)
	{
		return "__len__".equals(name);
	}

	private static boolean isCollection(@Nonnull PyDebugValue parent)
	{
		String type = parent.getType();
		return type.equals("dict") || type.equals("list");
	}

	private static String getChildNamePresentation(@Nonnull PyDebugValue parent, @Nonnull String childName)
	{
		if(isCollection(parent))
		{
			return "[".concat(removeId(childName)).concat("]");
		}
		else
		{
			return ".".concat(childName);
		}
	}

	private String wrapWithPrefix(String name)
	{
		if(isReturnedVal())
		{
			// return values are saved in dictionary on Python side, so the variable's name should be transformed
			return RETURN_VALUES_PREFIX + "[\"" + name + "\"]";
		}
		else
		{
			return name;
		}
	}

	private String getFullTreeName()
	{
		String result = "";
		String curNodeName = myName;
		PyDebugValue parent = myParent;
		while(parent != null)
		{
			result = getChildNamePresentation(parent, curNodeName).concat(result);
			curNodeName = parent.getName();
			parent = parent.getParent();
		}
		return wrapWithPrefix(curNodeName.concat(result));
	}

	@Override
	public void computePresentation(@Nonnull XValueNode node, @Nonnull XValuePlace place)
	{
		String value = PyTypeHandler.format(this);
		setFullValueEvaluator(node, value);
		if(value.length() >= MAX_VALUE)
		{
			value = value.substring(0, MAX_VALUE);
		}
		node.setPresentation(getValueIcon(), myType, value, myContainer);
	}

	private boolean isDataFrame()
	{
		return "DataFrame".equals(myType);
	}

	private boolean isNdarray()
	{
		return "ndarray".equals(myType);
	}

	private void setFullValueEvaluator(XValueNode node, String value)
	{
		String treeName = getFullTreeName();
		if(!isDataFrame() && !isNdarray())
		{
			if(value.length() >= MAX_VALUE)
			{
				node.setFullValueEvaluator(new PyFullValueEvaluator(myFrameAccessor, treeName));
			}
			return;
		}
		String linkText = "...View as " + (isDataFrame() ? "DataFrame" : "Array");
		node.setFullValueEvaluator(new PyNumericContainerValueEvaluator(linkText, myFrameAccessor, treeName));
	}

	@Override
	public void computeChildren(@Nonnull final XCompositeNode node)
	{
		if(node.isObsolete())
		{
			return;
		}
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			if(myFrameAccessor == null)
			{
				return;
			}

			try
			{
				final XValueChildrenList values = myFrameAccessor.loadVariable(this);
				if(!node.isObsolete())
				{
					node.addChildren(values, true);
				}
			}
			catch(PyDebuggerException e)
			{
				if(!node.isObsolete())
				{
					node.setErrorMessage("Unable to display children:" + e.getMessage());
				}
				LOG.warn(e);
			}
		});
	}

	@Override
	public XValueModifier getModifier()
	{
		return new PyValueModifier(myFrameAccessor, this);
	}

	private Image getValueIcon()
	{
		if(!myContainer)
		{
			return ExecutionDebugIconGroup.nodePrimitive();
		}
		else if("list".equals(myType) || "tuple".equals(myType))
		{
			return ExecutionDebugIconGroup.nodeArray();
		}
		else
		{
			return ExecutionDebugIconGroup.nodeValue();
		}
	}

	public PyDebugValue setName(String newName)
	{
		PyDebugValue value = new PyDebugValue(newName, myType, myTypeQualifier, myValue, myContainer, myIsReturnedVal, myIsIPythonHidden, myErrorOnEval, myParent, myFrameAccessor);
		value.setTempName(myTempName);
		return value;
	}

	@Nullable
	@Override
	public XReferrersProvider getReferrersProvider()
	{
		if(myFrameAccessor.getReferrersLoader() != null)
		{
			return new XReferrersProvider()
			{
				@Override
				public XValue getReferringObjectsValue()
				{
					return new PyReferringObjectsValue(PyDebugValue.this);
				}
			};
		}
		else
		{
			return null;
		}
	}

	public PyFrameAccessor getFrameAccessor()
	{
		return myFrameAccessor;
	}

	public PyVariableLocator getVariableLocator()
	{
		return myVariableLocator;
	}

	public void setVariableLocator(PyVariableLocator variableLocator)
	{
		myVariableLocator = variableLocator;
	}

	public String getId()
	{
		return myId;
	}

	public void setId(String id)
	{
		myId = id;
	}

	@Override
	public boolean canNavigateToSource()
	{
		return true;
	}

	@Override
	public void computeSourcePosition(@Nonnull XNavigatable navigatable)
	{
		if(myParent == null)
		{
			navigatable.setSourcePosition(myFrameAccessor.getSourcePositionForName(myName, null));
		}
		else
		{
			navigatable.setSourcePosition(myFrameAccessor.getSourcePositionForName(myName, myParent.getDeclaringType()));
		}
	}

	@Override
	public boolean canNavigateToTypeSource()
	{
		return true;
	}

	private static final Pattern IS_TYPE_DECLARATION = Pattern.compile("<(?:class|type)\\s*'(?<TYPE>.*?)'>");

	@Override
	public void computeTypeSourcePosition(@Nonnull XNavigatable navigatable)
	{

		String lookupType = getDeclaringType();
		navigatable.setSourcePosition(myFrameAccessor.getSourcePositionForType(lookupType));
	}

	protected final String getDeclaringType()
	{
		String lookupType = getQualifiedType();
		if(!Strings.isNullOrEmpty(myValue))
		{
			Matcher matcher = IS_TYPE_DECLARATION.matcher(myValue);
			if(matcher.matches())
			{
				lookupType = matcher.group("TYPE");
			}
		}
		return lookupType;
	}

	public String getQualifiedType()
	{
		if(Strings.isNullOrEmpty(myType))
		{
			return null;
		}
		return (myTypeQualifier == null) ? myType : (myTypeQualifier + "." + myType);
	}

	public String getTypeQualifier()
	{
		return myTypeQualifier;
	}
}
